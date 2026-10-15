/**
 * Copyright 2026 Darshan <darshan@alchiemy.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package com.circuitnext.rastrio

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.escpos.*
import com.circuitnext.rastrio.core.preview.*
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import com.circuitnext.rastrio.core.text.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking

/** Device-only reference-backend gate; uses synthetic inputs and never opens a printer transport. */
class BundledTextRuntimeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val status = Bundle().apply {
            putString("id", "RastrIO")
            putInt("numtests", 1)
            putInt("current", 1)
            putString("class", this@BundledTextRuntimeInstrumentation.javaClass.name)
            putString("test", "bundledReferenceBackendAndPreparedArtifacts")
        }
        sendStatus(1, status)
        try {
            verifyBackend()
            sendStatus(0, status)
            finish(Activity.RESULT_OK, Bundle().apply {
                putString("stream", "OK (1 test): RastrIO Android reference backend and artifact gate PASS\n")
                putString("abi", Build.SUPPORTED_ABIS.joinToString(","))
            })
        } catch (failure: Throwable) {
            status.putString("stack", "${failure.javaClass.name}: ${failure.message}")
            sendStatus(-2, status)
            finish(Activity.RESULT_CANCELED, Bundle().apply {
                putString("stream", "RastrIO Android reference backend gate FAIL\n")
            })
        }
    }

    private fun verifyBackend() = runBlocking {
        val actual = createPlatformRasterTextBackend()
        check(actual is BundledRasterTextBackend) { "Packaged reference backend unavailable" }
        val typography = actual.typography(sizeMm = 3.0)
        for (text in listOf("Hello", "a\u0301", "क्षि", "नमस्ते", "Latin हिन्दी", "Ελληνικά Привет")) {
            val measured = actual.measure(TextMeasureRequest(text, typography))
            check(measured is TextMeasureResult.Success && measured.measurement.advanceMm > 0)
            if (text == "क्षि") check(measured.measurement.clusters.size == 1)
            val request = RasterTextRequest(text, typography, 577, 64, 40, 203, 203)
            val first = actual.rasterize(request)
            val second = actual.rasterize(request)
            check(first is RasterTextResult.Success && second is RasterTextResult.Success)
            check(first.fontIdentity.contains("@"))
            val bytes = first.bitmap.copyBytes()
            check(bytes.any { it.toInt() != 0 } && bytes.contentEquals(second.bitmap.copyBytes()))
            for (y in 0 until 64) check(bytes[(y + 1) * first.bitmap.rowBytes - 1].toInt() and 0x7f == 0)
        }
        for (text in listOf("🙂", "שלום", "A\u200fB", CharArray(1) { 0xd800.toChar() }.concatToString()))
            check(actual.measure(TextMeasureRequest(text, typography)) is TextMeasureResult.Failure)
        for (weight in TextWeight.entries) {
            val body = actual.typography(TextStyle(weight = weight))
            val code = actual.typography(TextStyle(weight = weight, role = TextRole.CODE))
            val variant = if (weight == TextWeight.BOLD) "Bold" else "Regular"
            for (arrow in listOf("↑", "→", "↓", "↗", "↘")) {
                check(actual.measure(TextMeasureRequest("AV${arrow}To", body)) is TextMeasureResult.Success)
                val request = RasterTextRequest(arrow, body, 577, 64, 40, 203, 203)
                val glyph = actual.rasterize(request)
                val expected = actual.rasterize(request.copy(typography = code))
                check(glyph is RasterTextResult.Success && expected is RasterTextResult.Success)
                check(glyph.fontIdentity.startsWith("JetBrainsMono-$variant@"))
                check(glyph.bitmap.copyBytes().any { it.toInt() != 0 })
                check(glyph.bitmap.copyBytes().contentEquals(expected.bitmap.copyBytes()))
                val mixed = actual.rasterize(request.copy(text = "AV${arrow}To"))
                check(mixed is RasterTextResult.Success && "NotoSans-$variant@" in mixed.fontIdentity &&
                    "JetBrainsMono-$variant@" in mixed.fontIdentity)
            }
        }
        for ((symbol, code) in listOf("🎵" to "U+1F3B5", "🎼" to "U+1F3BC")) {
            val measured = actual.measure(TextMeasureRequest(symbol, typography))
            check(measured is TextMeasureResult.Failure && code in measured.diagnostics.single().message)
            val raster = actual.rasterize(RasterTextRequest(symbol, typography, 577, 64, 40, 203, 203))
            check(raster is RasterTextResult.Failure && code in raster.diagnostics.single().message)
        }

        var measurements = 0
        var rasters = 0
        val observed = object : RasterTextBackend by actual {
            override fun measure(request: TextMeasureRequest, isCancelled: () -> Boolean): TextMeasureResult {
                measurements++
                return actual.measure(request, isCancelled)
            }
            override fun rasterize(request: RasterTextRequest): RasterTextResult {
                rasters++
                return actual.rasterize(request)
            }
        }
        val document = ThermalDocument(blocks = listOf(
            Paragraph(Alignment.LEFT, listOf(Text("ASCII"))),
            Paragraph(Alignment.LEFT, listOf(Text("हिन्दी e\u0301"))),
            Heading(2, Alignment.CENTER, listOf(Text("AV↑To → ↓ ↗ ↘"))),
            Paragraph(Alignment.RIGHT, listOf(Text("Arrows ↑ → ↓ ↗ ↘ "), Strong(listOf(Text("Bold ↑ → ↓ ↗ ↘"))))),
            Table(listOf(TableColumn(Alignment.LEFT), TableColumn(Alignment.RIGHT)), emptyList(),
                listOf(listOf(TableCell(listOf(Text("AV↑To → ↓ ↗ ↘ ".repeat(3)))),
                    TableCell(listOf(Strong(listOf(Text("Bold ↑ → ↓ ↗ ↘ ".repeat(3))))))))),
        ))
        val result = BasicPreparationEngine(rasterTextBackend = observed).prepare(document, SyntheticReferenceProfiles.narrow)
        check(result is PreparationResult.Success)
        val plan = result.print
        check(plan.operations.any { it is NativeText } && plan.operations.any { it is RasterText })
        check(measurements > 0 && rasters > 0)
        val counts = measurements to rasters
        val projected = plan.toPrintPreview()
        check(projected is PrintPreviewResult.Success)
        val seen = mutableSetOf<PreparedOperation>()
        repeat(projected.preview.tileCount) { index ->
            val tile = projected.preview.tile(index)
            check(tile is PrintPreviewTileResult.Success)
            for (primitive in tile.tile.primitives.filterIsInstance<MonochromePrimitive>()) {
                val operation = primitive.operation
                if (operation is RasterText) {
                    check(primitive.graphic === operation.graphic)
                    seen.add(operation)
                }
            }
        }
        val output = ByteArrayOutputStream()
        val encoded = EscPosEncoder(EncodingResourcePolicy(maxChunkBytes = 7)).encode(plan, EncodedByteSink {
            check(output.size().toLong() + it.size <= 256 * 1024)
            output.write(it)
            true
        })
        check(encoded is EncodingResult.Success)
        val bytes = output.toByteArray()
        for (operation in plan.operations.filterIsInstance<RasterText>()) {
            check(operation in seen)
            val graphic = operation.graphic
            val snapshot = graphic.copyBytes()
            graphic.copyBytes().fill(0)
            check(snapshot.contentEquals(graphic.copyBytes()))
            val frame = byteArrayOf(0x1d, 0x76, 0x30, 0, (graphic.rowBytes and 255).toByte(),
                (graphic.rowBytes ushr 8).toByte(), (graphic.heightDots and 255).toByte(),
                (graphic.heightDots ushr 8).toByte()) + snapshot
            check((0..bytes.size - frame.size).any { start -> frame.indices.all { bytes[start + it] == frame[it] } })
        }
        check(counts == (measurements to rasters)) { "A consumer invoked the text producer again" }
    }
}
