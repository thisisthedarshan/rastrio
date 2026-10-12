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

package com.circuitnext.rastrio.core.printer

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import java.io.File

/** Synthetic semantic fixtures only; this generator does not connect to hardware. */
object Phase111213HardwareFixtures {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1)
        val directory = File(args.single()).also { check(it.isDirectory) }
        val fixtures = listOf(
            "HW-017-native-qr.td" to listOf(QrCode("HW-017", Alignment.CENTER,
                QrErrorCorrection.MEDIUM, Length(20.0))),
            "HW-018-raster-qr.td" to listOf(QrCode("https://example.invalid/rastrio/hw-018",
                Alignment.CENTER, QrErrorCorrection.HIGH, Length(30.0))),
            "HW-019-qr-sizes.td" to listOf(12.0, 20.0, 30.0).map { size ->
                QrCode("HW-019", Alignment.CENTER, QrErrorCorrection.MEDIUM, Length(size))
            },
        )
        for ((name, blocks) in fixtures) {
            val document = ThermalDocument(blocks = blocks)
            val archive = TdArchive.save(document)
            check(archive.contentEquals(TdArchive.save(document)))
            val restored = TdArchive.load(archive).document
            check(restored == document)
            val strategy = if (name.startsWith("HW-017")) QrStrategy.NATIVE else QrStrategy.RASTER
            val result = BasicPreparationEngine().prepare(restored, SyntheticReferenceProfiles.wide80mm,
                PrintOptions(qrStrategy = strategy))
            check(result is PreparationResult.Success) { result.toString() }
            if (strategy == QrStrategy.NATIVE) check(result.print.operations.count { it is NativeQr } == 1)
            else check(result.print.operations.any { it is RasterQr })
            File(directory, name).writeBytes(archive)
        }
        println("Hardware semantic fixtures: 3 strict archive round-trips and synthetic preparations passed; no hardware run")
    }
}
