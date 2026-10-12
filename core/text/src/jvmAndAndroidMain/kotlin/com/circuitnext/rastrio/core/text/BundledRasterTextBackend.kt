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

package com.circuitnext.rastrio.core.text

import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt

internal expect fun loadTextNativeLibrary()

actual fun createPlatformRasterTextBackend(): RasterTextBackend = try {
    BundledRasterTextBackend.create()
} catch (_: LinkageError) {
    UnsupportedRasterTextBackend()
} catch (_: RuntimeException) {
    UnsupportedRasterTextBackend()
} catch (_: java.io.IOException) {
    UnsupportedRasterTextBackend()
}

internal object NativeTextBridge {
    external fun metrics(font: ByteArray): LongArray
    external fun shape(font: ByteArray, text: String, sizeMm: Double, dpiX: Int, dpiY: Int): LongArray
    external fun raster(font: ByteArray, text: String, sizeMm: Double, dpiX: Int, dpiY: Int,
        width: Int, height: Int, baseline: Int, origin: Int, isCancelled: () -> Boolean): ByteArray
}

/** Identical checked-in fonts and pinned native libraries on JVM and Android. */
class BundledRasterTextBackend private constructor() : RasterTextBackend {
    override val identity = "harfbuzz-14.6.0/freetype-2.14.3/language-und/ltr/no-hinting/gray-threshold128/v1"
    private val fonts = HashMap<String, ByteArray>()
    private data class Run(val start: Int, val end: Int, val font: String)
    private class FontRunLimitExceeded : IllegalArgumentException()
    private class TextCancelled : IllegalArgumentException()
    private fun checkCancellation(isCancelled: () -> Boolean) { if (isCancelled()) throw TextCancelled() }
    companion object {
        const val MAX_FONT_RUNS = 256
        private val hashes = mapOf(
            "NotoSans-Light" to "74ffdd438f2ae232371ceb00444dae93e2590d263a4ff4c5e2c8385aa8012fdb",
            "NotoSans-Regular" to "b85c38ecea8a7cfb39c24e395a4007474fa5a4fc864f6ee33309eb4948d232d5",
            "NotoSans-Bold" to "c976e4b1b99edc88775377fcc21692ca4bfa46b6d6ca6522bfda505b28ff9d6a",
            "NotoSans-Italic" to "36cff144df01309dab648bea71baff9bb074026914afe63aeacc8bc90b67a28b",
            "NotoSans-BoldItalic" to "6edf4227ef0fa846aca70e86a307804ca4401741830f5b3af0f2554abe2b8466",
            "NotoSansDevanagari-Regular" to "385e78e6359a9d88a0f243d53b1209d7548361ba2194e2b9ec779bcaa7e8949d",
            "NotoSansDevanagari-Bold" to "19cc835a064c4af63e3c20feb54f5bf51dc25ffa52c0f493a23904572af8b26e",
            "JetBrainsMono-Regular" to "a0bf60ef0f83c5ed4d7a75d45838548b1f6873372dfac88f71804491898d138f",
            "JetBrainsMono-Bold" to "5590990c82e097397517f275f430af4546e1c45cff408bde4255dad142479dcb",
            "JetBrainsMono-Italic" to "9d0a1f7a708e6af183f1193b7e81d40da294f5c67682c085d8401c60aac8ded4",
            "JetBrainsMono-BoldItalic" to "4039d5ce0ed225bf9c8b2c8c6436290ae2f356b7e90d70fa666227238324aa3b",
        )
        fun create(): BundledRasterTextBackend {
            val backend = BundledRasterTextBackend()
            try {
                for (name in hashes.keys) backend.font(name)
            } catch (_: java.io.IOException) {
                throw unavailableFonts()
            } catch (_: IllegalStateException) {
                throw unavailableFonts()
            } catch (_: java.security.GeneralSecurityException) {
                throw unavailableFonts()
            }
            loadTextNativeLibrary()
            return backend
        }
        private fun unavailableFonts() = TextBackendUnavailableException(SnapshotList(listOf(
            TextDiagnostic("TXT132", "Controlled bundled font resources are missing or invalid"))))
    }
    @Synchronized private fun font(name: String): ByteArray = fonts.getOrPut(name) {
        val stream = javaClass.getResourceAsStream("/rastrio-fonts/$name.ttf")
            ?: error("Bundled font unavailable")
        stream.use {
            val bytes = java.io.ByteArrayOutputStream().also { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = it.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 1024 * 1024) { "Bundled font resource limit" }
                    output.write(buffer, 0, count)
                }
            }.toByteArray()
            check(bytes.size in 1..1024 * 1024) { "Bundled font resource limit" }
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { b -> "%02x".format(b.toInt() and 255) }
            check(actual == hashes[name]) { "Bundled font hash mismatch" }
            bytes
        }
    }
    private fun face(style: TextStyle): String {
        val family = if (style.role == TextRole.CODE) "JetBrainsMono" else "NotoSans"
        val variant = when {
            style.weight == TextWeight.BOLD && style.emphasis -> "BoldItalic"
            style.weight == TextWeight.BOLD -> "Bold"
            style.emphasis -> "Italic"
            else -> "Regular"
        }
        return "$family-$variant"
    }
    override fun typography(style: TextStyle, sizeMm: Double): ResolvedTypography {
        require(sizeMm.isFinite() && sizeMm in 0.1..50.0)
        val main = NativeTextBridge.metrics(font(face(style)))
        val fallback = NativeTextBridge.metrics(font("NotoSansDevanagari-${if (style.weight == TextWeight.BOLD) "Bold" else "Regular"}"))
        fun metric(index: Int) = max(main[index].toDouble() / main[0], fallback[index].toDouble() / fallback[0]) * sizeMm
        val ascent = metric(1)
        val descent = metric(2)
        val height = max(metric(3), ascent + descent)
        val metrics = TypographyMetrics(ascent, descent, 0.0, height, ascent)
        val cell = if (style.role == TextRole.CODE) {
            val positions = NativeTextBridge.shape(font(face(style)), " ", sizeMm, 2540, 2540)
            val advance = positions[1].toDouble() / 6400.0
            FixedCellGeometry(advance, advance, height)
        } else null
        return ResolvedTypography("bundled:$sizeMm", style, metrics, cell)
    }
    private fun size(typography: ResolvedTypography): Double {
        require(typography.identity.startsWith("bundled:")) { "Unrecognized typography identity" }
        return typography.identity.substringAfter(':').toDouble().also { require(it.isFinite() && it in 0.1..50.0) }
    }
    private fun runs(text: String, style: TextStyle, isCancelled: () -> Boolean): List<Run> {
        checkCancellation(isCancelled)
        val points = UnicodeSegmentation.points(text)
        require(points.none { it.code in 0x590..0x8ff || it.code in 0xfb1d..0xfdff || it.code in 0xfe70..0xfeff || it.code in 0x200e..0x200f || it.code in 0x202a..0x202e || it.code in 0x2066..0x206f }) { "Unsupported bidirectional content" }
        require(points.none { it.code < 0x20 || it.code in 0x7f..0x9f }) { "Control requires semantic layout" }
        val boundaries = UnicodeSegmentation.graphemeBoundaries(text)
        val result = ArrayList<Run>()
        var pointIndex = 0
        for (i in 0 until boundaries.lastIndex) {
            if (i % 64 == 0) checkCancellation(isCancelled)
            val start = boundaries[i]
            val end = boundaries[i + 1]
            var devanagari = false
            while (pointIndex < points.size && points[pointIndex].start < end) {
                val code = points[pointIndex++].code
                devanagari = devanagari || code in 0x900..0x97f || code in 0xa8e0..0xa8ff
            }
            require(!devanagari || !style.emphasis) { "Requested Devanagari italic face unavailable" }
            val selected = if (devanagari) "NotoSansDevanagari-${if (style.weight == TextWeight.BOLD) "Bold" else "Regular"}" else face(style)
            val previous = result.lastOrNull()
            if (previous?.font == selected) result[result.lastIndex] = previous.copy(end = end)
            else {
                if (result.size == MAX_FONT_RUNS) throw FontRunLimitExceeded()
                result.add(Run(start, end, selected))
            }
        }
        return result
    }
    override fun measure(request: TextMeasureRequest): TextMeasureResult = measure(request) { false }
    override fun measure(request: TextMeasureRequest, isCancelled: () -> Boolean): TextMeasureResult = try {
        checkCancellation(isCancelled)
        val selected = runs(request.text, request.typography.style, isCancelled)
        val sizeMm = size(request.typography)
        val boundaries = UnicodeSegmentation.graphemeBoundaries(request.text)
        val advances = LongArray(request.text.length + 1)
        val shapingStarts = BooleanArray(request.text.length + 1)
        shapingStarts[0] = true
        for (run in selected) {
            checkCancellation(isCancelled)
            val values = NativeTextBridge.shape(font(run.font), request.text.substring(run.start, run.end), sizeMm, 2540, 2540)
            var i = 0
            while (i < values.size) {
                val cluster = run.start + values[i].toInt()
                require(cluster in run.start until run.end)
                shapingStarts[cluster] = true
                advances[cluster] += values[i + 1]
                i += 2
            }
        }
        checkCancellation(isCancelled)
        val safe = boundaries.filter { it == request.text.length || shapingStarts[it] }
        val clusters = ArrayList<MeasuredCluster>()
        var total = 0L
        for (i in 0 until safe.lastIndex) {
            var advance = 0L
            for (offset in safe[i] until safe[i + 1]) advance += advances[offset]
            require(advance >= 0)
            val ticks = LogicalGeometry.ticks(advance.toDouble() / 6400.0)
            total += ticks
            clusters.add(MeasuredCluster(safe[i], safe[i + 1], LogicalGeometry.millimetres(ticks)))
        }
        val breaks = UnicodeLineBreaking.opportunities(request.text).filter { opportunity -> opportunity.offset < request.text.length && safe.binarySearch(opportunity.offset) >= 0 }
        checkCancellation(isCancelled)
        TextMeasureResult.Success(TextMeasurement(request.text.length, LogicalGeometry.millimetres(total),
            request.typography.metrics, SnapshotList(clusters), SnapshotList(breaks)))
    } catch (_: FontRunLimitExceeded) {
        TextMeasureResult.Failure(SnapshotList(listOf(TextDiagnostic("TXT120", "Controlled text exceeds font-run work limit"))))
    } catch (_: TextCancelled) {
        TextMeasureResult.Failure(SnapshotList(listOf(TextDiagnostic("TXT130", "Text measurement cancelled"))))
    } catch (e: IllegalArgumentException) {
        TextMeasureResult.Failure(SnapshotList(listOf(TextDiagnostic("TXT130", "Controlled text shaping unavailable for requested content or resources"))))
    }
    override fun rasterize(request: RasterTextRequest): RasterTextResult = try {
        require(request.widthDots in 1..32768 && request.heightDots in 1..32768)
        require(request.dpiX in 72..2400 && request.dpiY in 72..2400)
        require(request.baselineDots in 0..request.heightDots)
        val selected = runs(request.text, request.typography.style, request.isCancelled)
        val row = (request.widthDots + 7) / 8
        require(row.toLong() * request.heightDots <= 8 * 1024 * 1024)
        val bytes = ByteArray(row * request.heightDots)
        val sizeMm = size(request.typography)
        require(request.originDots in 0..request.widthDots)
        var advance = request.originDots.toLong() * 64
        for (run in selected) {
            checkCancellation(request.isCancelled)
            val text = request.text.substring(run.start, run.end)
            val bitmap = NativeTextBridge.raster(font(run.font), text, sizeMm, request.dpiX, request.dpiY,
                request.widthDots, request.heightDots, request.baselineDots, (advance / 64.0).roundToInt(), request.isCancelled)
            for (i in bytes.indices) bytes[i] = (bytes[i].toInt() or bitmap[i].toInt()).toByte()
            checkCancellation(request.isCancelled)
            val shaped = NativeTextBridge.shape(font(run.font), text, sizeMm, request.dpiX, request.dpiY)
            var i = 1
            while (i < shaped.size) { advance += shaped[i]; i += 2 }
        }
        checkCancellation(request.isCancelled)
        fun rule(y: Int) {
            require(y in 0 until request.heightDots) { "Decoration exceeds final box" }
            for (x in request.originDots until minOf(request.widthDots, (advance / 64.0).roundToInt()))
                bytes[y * row + x / 8] = (bytes[y * row + x / 8].toInt() or (0x80 ushr (x % 8))).toByte()
        }
        if (request.typography.style.underline) rule(request.baselineDots + max(1, (sizeMm * request.dpiY / 25.4 * 0.08).roundToInt()))
        if (request.typography.style.strike) rule(request.baselineDots - max(1, (sizeMm * request.dpiY / 25.4 * 0.3).roundToInt()))
        val fontIdentity = selected.map { it.font }.distinct().joinToString("+") { "$it@${hashes[it]}" }
        RasterTextResult.Success(TextBitmap(request.widthDots, request.heightDots, bytes), identity, fontIdentity)
    } catch (_: FontRunLimitExceeded) {
        RasterTextResult.Failure(SnapshotList(listOf(TextDiagnostic("TXT120", "Controlled text exceeds font-run work limit"))))
    } catch (_: TextCancelled) {
        RasterTextResult.Failure(SnapshotList(listOf(TextDiagnostic("TXT130", "Text rasterization cancelled"))))
    } catch (e: IllegalArgumentException) {
        if (e.message == "Text rasterization cancelled")
            RasterTextResult.Failure(SnapshotList(listOf(TextDiagnostic("TXT130", "Text rasterization cancelled"))))
        else RasterTextResult.Failure(SnapshotList(listOf(TextDiagnostic("TXT131", "Controlled text rasterization unavailable for requested content or resources"))))
    }
}
