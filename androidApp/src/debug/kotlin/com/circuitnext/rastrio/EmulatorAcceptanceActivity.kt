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

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.circuitnext.rastrio.core.profile.PrinterProfile
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import com.circuitnext.rastrio.core.raster.DecodedImage
import com.circuitnext.rastrio.printing.*
import kotlinx.coroutines.flow.MutableStateFlow

/** Debug-only emulator fixture: shared production UI, deterministic pixels, and no hardware transport. */
class EmulatorAcceptanceActivity : ComponentActivity() {
    private lateinit var fixture: PreviewFixture
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(Build.HARDWARE in listOf("ranchu", "goldfish")) { "Emulator acceptance fixture only" }
        fixture = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PreviewFixture() as T
            }
        })[PreviewFixture::class.java]
        setContent { RastrIOApp(printingPlatform = fixture, imagePlatform = fixture, printingSessions = fixture.sessions,
            backHandler = { enabled, back -> BackHandler(enabled, back) }) }
    }
    override fun onStart() { super.onStart(); fixture.foreground.value = true }
    override fun onStop() { fixture.foreground.value = false; super.onStop() }

    private class PreviewFixture : ViewModel(), PrintingPlatform, ImagePlatform {
        val sessions = PrinterSessionStore(::createTransport)
        override fun onCleared() { sessions.cancelAll(); foreground.value = false }
        private val narrow = SyntheticReferenceProfiles.narrow.copy(profileId = "org.rastrio.fixture.emulator.narrow")
        private val wide = SyntheticReferenceProfiles.wide80mm.copy(profileId = "org.rastrio.fixture.emulator.wide")
        private val first = PrinterInstance("preview-384", "Preview fixture 384 (no hardware)", PrinterTransportKind.DEVELOPMENT,
            "emulator-preview-384", narrow.profileId, narrow.profileRevision)
        private val second = PrinterInstance("preview-576", "Preview fixture 576 (no hardware)", PrinterTransportKind.DEVELOPMENT,
            "emulator-preview-576", wide.profileId, wide.profileRevision)
        override val profile = MutableStateFlow<PrinterProfile?>(narrow)
        override val selectedProfile = MutableStateFlow<PrinterProfile?>(narrow)
        override val selectedPrinter = MutableStateFlow<PrinterInstance?>(first)
        override val printers = MutableStateFlow(listOf(first, second))
        override val status = MutableStateFlow("Emulator fixture · hardware unavailable")
        override val foreground = MutableStateFlow(true)
        override val presence = MutableStateFlow(PrinterPresence.NOT_CHECKED)
        override val image = MutableStateFlow<ImageInput?>(null)
        override val imageStatus = MutableStateFlow("Choose image loads a deterministic local test pattern")
        override fun refreshPairedPrinters() = Unit
        override fun importProfile() = Unit
        override fun refreshPresence() { presence.value = PrinterPresence.NOT_DETECTED }
        override fun selectPrinter(instance: PrinterInstance) {
            require(instance in printers.value && instance.transportKind == PrinterTransportKind.DEVELOPMENT)
            selectedProfile.value = if (instance.id == first.id) narrow else wide
            selectedPrinter.value = instance
        }
        override fun createTransport(): PrinterTransport = FakePrinterTransport()
        override fun updatePrinterDefaults(instance: PrinterInstance, defaults: PrinterPrintDefaults) {
            val replacement = instance.copy(defaults = defaults)
            printers.value = printers.value.map { if (it == instance) replacement else it }
            if (selectedPrinter.value == instance) selectedPrinter.value = replacement
        }
        override fun importImage() {
            val width = 257
            val height = 193
            val pixels = IntArray(width * height) { index ->
                val x = index % width
                val y = index / width
                val value = when {
                    y < 64 -> x * 255 / (width - 1)
                    y < 128 -> ((x * 13 + y * 7) xor (x * y)).and(255)
                    else -> if ((x / 3 + y / 3) % 2 == 0) 70 else 185
                }
                (0xff000000.toInt() or (value shl 16) or (value shl 8) or value)
            }
            image.value = ImageInput(DecodedImage(width, height, pixels))
            imageStatus.value = "Deterministic gradient, texture and fine-detail test pattern"
        }
    }
}
