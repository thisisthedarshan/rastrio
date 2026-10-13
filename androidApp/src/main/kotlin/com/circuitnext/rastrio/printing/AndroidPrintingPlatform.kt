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

package com.circuitnext.rastrio.printing

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.circuitnext.rastrio.core.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun readProfileBytes(input: InputStream): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer, 0, minOf(buffer.size, 1024 * 1024 + 1 - output.size()))
        if (count < 0) break
        require(count > 0) { "Profile stream made no progress" }
        output.write(buffer, 0, count)
        require(output.size() <= 1024 * 1024) { "Profile input limit exceeded" }
    }
    val bytes = output.toByteArray()
    require(bytes.size <= 1024 * 1024) { "Profile input limit exceeded" }
    return bytes
}

internal suspend fun readProfileStream(open: () -> InputStream?): ByteArray = suspendCancellableCoroutine { continuation ->
    val active = AtomicReference<InputStream?>()
    continuation.invokeOnCancellation { try { active.get()?.close() } catch (_: java.io.IOException) { } }
    try {
        val input = open() ?: throw IllegalArgumentException("Unreadable profile")
        active.set(input)
        if (!continuation.isActive) { input.close(); return@suspendCancellableCoroutine }
        val bytes = input.use(::readProfileBytes)
        continuation.resume(bytes)
    } catch (failure: Exception) {
        if (continuation.isActive) continuation.resumeWithException(failure)
    } finally { active.set(null) }
}

internal suspend fun writeProfileStream(bytes: ByteArray, open: () -> java.io.OutputStream?) {
    require(bytes.size in 1..1024 * 1024) { "Profile output limit exceeded" }
    suspendCancellableCoroutine<Unit> { continuation ->
        val active = AtomicReference<java.io.OutputStream?>()
        continuation.invokeOnCancellation { try { active.get()?.close() } catch (_: java.io.IOException) { } }
        try {
            val output = open() ?: throw IllegalArgumentException("Unwritable profile destination")
            active.set(output)
            if (!continuation.isActive) { output.close(); return@suspendCancellableCoroutine }
            output.use {
                var offset = 0
                while (offset < bytes.size) {
                    if (!continuation.isActive) throw CancellationException("Profile export cancelled")
                    val count = minOf(8192, bytes.size - offset)
                    it.write(bytes, offset, count)
                    offset += count
                }
                it.flush()
            }
            continuation.resume(Unit)
        } catch (failure: Exception) {
            if (continuation.isActive) continuation.resumeWithException(failure)
        } finally { active.set(null) }
    }
}

/** Private fixed-path storage; acquisition and strict decoding precede publication. */
class AndroidPrintingPlatform(
    private val context: Context,
    private val scope: CoroutineScope,
    private var requestPermission: () -> Unit,
    private var chooseProfile: () -> Unit,
) : PrintingPlatform, ImagePlatform {
    private val instances = PrinterInstanceStore(context)
    private val selectionPersistence = PrinterSelectionPersistence(instances::save)
    private val profileFileLock = Any()
    override val profileDraftStorage: ProfileDraftStorage = AndroidProfileDraftStorage(context)
    private val mutablePairedDevices = MutableStateFlow<List<PairedPrinterDevice>>(emptyList())
    override val pairedDevices: StateFlow<List<PairedPrinterDevice>> = mutablePairedDevices
    private val mutableSelectedPrinter = MutableStateFlow<PrinterInstance?>(null)
    override val selectedPrinter: StateFlow<PrinterInstance?> = mutableSelectedPrinter
    private val savedProfile = AtomicFile(File(context.filesDir, "selected-printer.tcfg"))
    private var chooseProfileExport: () -> Unit = {}
    private var profileExportSnapshot: ByteArray? = null
    private var profileExportJob: Job? = null
    private val profileExportMutex = Mutex()
    private val mutablePrinters = MutableStateFlow<List<PrinterInstance>>(emptyList())
    private val mutableProfile = MutableStateFlow<PrinterProfile?>(null)
    private var profileGeneration = 0L
    private var profileJob: Job? = null
    private val profileMutex = Mutex()
    private val mutableStatus = MutableStateFlow("Add a paired device, then create or select a validated profile")
    override val printers: StateFlow<List<PrinterInstance>> = mutablePrinters
    override val profile: StateFlow<PrinterProfile?> = mutableProfile
    override val status: StateFlow<String> = mutableStatus
    private val mutableForeground = MutableStateFlow(false)
    override val foreground: StateFlow<Boolean> = mutableForeground
    private val connection = ForegroundPrintConnection { mutableForeground.value }
    private var chooseImage: () -> Unit = {}
    private val decoder = AndroidImageDecoder(context.contentResolver)
    private var imageJob: Job? = null
    private val mutableImage = MutableStateFlow<ImageInput?>(null)
    private val mutableImageStatus = MutableStateFlow("Choose a JPEG or PNG image")
    override val image: StateFlow<ImageInput?> = mutableImage
    override val imageStatus: StateFlow<String> = mutableImageStatus
    override fun importImage() {
        imageJob?.cancel()
        mutableImage.value = null
        mutableImageStatus.value = "Choose a JPEG or PNG image"
        chooseImage()
    }
    fun bindImageAction(action: () -> Unit) { chooseImage = action }
    fun imageResult(uri: Uri?) {
        if (uri == null) { mutableImageStatus.value = "Image selection cancelled"; return }
        imageJob?.cancel()
        mutableImage.value = null
        imageJob = scope.launch {
            mutableImageStatus.value = "Importing image"
            try {
            when (val result = withTimeout(30_000) { decoder.decode(uri) }) {
                is AndroidImageDecodeResult.Success -> {
                    mutableImage.value = result.input
                    mutableImageStatus.value = if (result.sampled) "Image imported at a bounded working resolution" else "Image imported"
                }
                is AndroidImageDecodeResult.Invalid -> mutableImageStatus.value = "${result.code}: ${result.message}"
            }
            } catch (_: TimeoutCancellationException) {
                mutableImageStatus.value = "Image import timed out"
            } finally {
                if (!isActive && mutableImage.value == null) mutableImageStatus.value = "Image import cancelled"
            }
        }
    }
    fun setForeground(value: Boolean) {
        mutableForeground.value = value
        if (!value) { connection.close(); imageJob?.cancel() }
    }

    init {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                try { synchronized(profileFileLock) { savedProfile.openRead().use { decode(readProfileBytes(it)) } } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            }
            val selected = if (loaded == null) null else withContext(Dispatchers.IO) { instances.load() }
                ?.takeIf { it.profileId == loaded.profileId && it.profileRevision == loaded.profileRevision }
            if (profileGeneration != 0L) return@launch
            mutableProfile.value = loaded
            mutableSelectedPrinter.value = selected
            if (loaded != null) mutableStatus.value = "Saved profile loaded; refresh paired printers"
        }
    }

    fun bindActions(permission: () -> Unit, profile: () -> Unit) {
        requestPermission = permission; chooseProfile = profile
    }
    override fun refreshPairedPrinters() = requestPermission()
    override fun importProfile() = chooseProfile()
    override val supportsProfileExport: Boolean get() = true
    fun bindProfileExport(action: () -> Unit) { chooseProfileExport = action }
    override fun exportProfile() {
        val profile = mutableProfile.value ?: run {
            mutableStatus.value = "APP140: Select a validated physical profile before export"; return
        }
        when (val result = PrinterProfileFiles.encode(profile)) {
            is ProfileValidationResult.Failure -> mutableStatus.value = result.diagnostics.joinToString("\n") { "${it.code}: ${it.message}" }
            is ProfileValidationResult.Success -> {
                profileExportSnapshot = result.value
                chooseProfileExport()
            }
        }
    }
    fun profileExportResult(uri: Uri?) {
        val bytes = profileExportSnapshot
        profileExportSnapshot = null
        if (uri == null) { mutableStatus.value = "Profile export cancelled"; return }
        if (bytes == null) { mutableStatus.value = "APP143: No validated profile export pending"; return }
        if (uri.scheme != "content") { mutableStatus.value = "APP143: Export requires a document-provider destination"; return }
        profileExportJob?.cancel()
        profileExportJob = scope.launch {
            profileExportMutex.withLock {
            try {
                withContext(Dispatchers.IO) {
                    withTimeout(30_000) { writeProfileStream(bytes) { context.contentResolver.openOutputStream(uri, "wt") } }
                }
                mutableStatus.value = "Validated profile exported; no printer identity included. Physical capabilities remain unverified."
            } catch (_: TimeoutCancellationException) {
                mutableStatus.value = "APP143: Profile export timed out; destination may contain partial data"
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableStatus.value = "APP143: Profile export failed; destination may contain partial data"
            }
            }
        }
    }
    private var discoveryJob: Job? = null
    fun permissionResult(granted: Boolean) {
        discoveryJob?.cancel()
        if (!granted) {
            mutablePairedDevices.value = emptyList()
            mutablePrinters.value = emptyList()
            mutableStatus.value = "BLUETOOTH_PERMISSION_REQUIRED: Allow Nearby devices to list paired printers"
            return
        }
        discoveryJob = scope.launch {
            try {
                val paired = withContext(Dispatchers.IO) { pairedPrinters(context) }
                // Preferences expose pending writes in memory before commit confirms disk persistence.
                // Discovery must retain only the association already confirmed by selection persistence.
                val previous = mutableSelectedPrinter.value
                mutablePairedDevices.value = paired
                updateInstances(previous)
                mutableStatus.value = when {
                    paired.isEmpty() -> "BLUETOOTH_DEVICE_ABSENT: No paired devices; pair the printer in Android settings"
                    paired.none { it.supportsClassic } -> "BLUETOOTH_BLE_INCOMPATIBLE: Paired devices are BLE-only; Classic SPP is required"
                    mutableProfile.value == null -> "Choose a paired device, then create or select a validated profile"
                    else -> "Choose a paired Classic device and save its profile association"
                }
            } catch (failure: PrinterTransportException) {
                mutablePairedDevices.value = emptyList(); mutablePrinters.value = emptyList()
                mutableStatus.value = bluetoothDiagnostic(failure.failure)
            } catch (_: SecurityException) {
                mutablePairedDevices.value = emptyList(); mutablePrinters.value = emptyList()
                mutableStatus.value = bluetoothDiagnostic(TransportFailure.BLUETOOTH_PERMISSION_REQUIRED)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutablePairedDevices.value = emptyList(); mutablePrinters.value = emptyList()
                mutableStatus.value = "BLUETOOTH_DISCOVERY_FAILED: Paired device list could not be read"
            }
        }
    }

    private fun updateInstances(previous: PrinterInstance? = mutableSelectedPrinter.value) {
        val profile = mutableProfile.value
        mutablePrinters.value = if (profile == null) emptyList() else mutablePairedDevices.value
            .filter { it.supportsClassic }.map { device ->
                PrinterInstance(previous?.takeIf { it.transportLocator == device.locator }?.id ?: UUID.randomUUID().toString(),
                    device.displayName, PrinterTransportKind.BLUETOOTH_CLASSIC, device.locator,
                    profile.profileId, profile.profileRevision)
            }
        mutableSelectedPrinter.value = previous?.takeIf { it in mutablePrinters.value }
    }

    override fun activateProfile(profile: PrinterProfile): Boolean {
        val bytes = when (val result = PrinterProfileFiles.encode(profile)) {
            is ProfileValidationResult.Failure -> {
                mutableStatus.value = result.diagnostics.joinToString("\n") { "${it.code}: ${it.message}" }
                return false
            }
            is ProfileValidationResult.Success -> result.value
        }
        ++profileGeneration
        profileJob?.cancel()
        try {
            synchronized(profileFileLock) {
                val output = savedProfile.startWrite()
                try { output.write(bytes); savedProfile.finishWrite(output) }
                catch (failure: Exception) { savedProfile.failWrite(output); throw failure }
            }
        } catch (_: Exception) {
            mutableStatus.value = "APP144: Validated profile could not be saved privately"
            return false
        }
        mutableProfile.value = profile
        updateInstances()
        mutableStatus.value = "Validated profile saved; save the chosen printer association. Hardware capabilities remain operator supplied."
        return true
    }

    override fun savePrinter(device: PairedPrinterDevice, profile: PrinterProfile) {
        if (device !in mutablePairedDevices.value) {
            mutableStatus.value = bluetoothDiagnostic(TransportFailure.BLUETOOTH_DEVICE_ABSENT); return
        }
        if (!device.supportsClassic) {
            mutableStatus.value = bluetoothDiagnostic(TransportFailure.BLUETOOTH_BLE_INCOMPATIBLE); return
        }
        if (profile != mutableProfile.value && !activateProfile(profile)) return
        val instance = mutablePrinters.value.firstOrNull { it.transportLocator == device.locator } ?: return
        selectPrinter(instance)
    }

    fun profileResult(uri: Uri?) {
        if (uri == null) return
        val generation = ++profileGeneration
        profileJob?.cancel()
        profileJob = scope.launch {
            profileMutex.withLock {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    withTimeout(30_000) { readProfileStream { context.contentResolver.openInputStream(uri) } }
                }
                val decoded = withContext(Dispatchers.Default) { PrinterProfileFiles.decode(bytes) }
                val validated = when (decoded) {
                    is ProfileValidationResult.Success -> decoded.value.profile
                    is ProfileValidationResult.Failure -> {
                        if (generation == profileGeneration) mutableStatus.value = decoded.diagnostics.joinToString("\n") { "${it.code}: ${it.message}" }
                        return@launch
                    }
                }
                if (generation != profileGeneration) return@launch
                withContext(Dispatchers.IO) {
                    synchronized(profileFileLock) {
                        if (generation == profileGeneration) {
                            val output = savedProfile.startWrite()
                            try { output.write(bytes); savedProfile.finishWrite(output) }
                            catch (failure: Exception) { savedProfile.failWrite(output); throw failure }
                        }
                    }
                }
                if (generation != profileGeneration) return@launch
                mutableProfile.value = validated
                updateInstances()
                mutableStatus.value = "Validated profile imported; choose a device and save its association"
            } catch (_: TimeoutCancellationException) {
                mutableStatus.value = "Profile import timed out"
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableStatus.value = "Profile import failed strict validation or resource checks"
            }
            }
        }
    }

    private fun decode(bytes: ByteArray): PrinterProfile? = when (val result = PrinterProfileFiles.decode(bytes)) {
        is ProfileValidationResult.Success -> result.value.profile
        is ProfileValidationResult.Failure -> null
    }

    override fun selectPrinter(instance: PrinterInstance) {
        if (instance !in mutablePrinters.value) return
        val request = selectionPersistence.request()
        scope.launch {
            val saved = selectionPersistence.save(request, instance)
            if (!selectionPersistence.isCurrent(request)) return@launch
            if (saved && instance in mutablePrinters.value) mutableSelectedPrinter.value = instance
            mutableStatus.value = if (saved) "Printer association saved privately; no bytes transmitted" else "Printer association could not be saved"
        }
    }

    override fun createTransport(): PrinterTransport =
        connection.wrap(BluetoothPrinterTransport(context, TransportConstraints(1024)))
}

/** Cached sessions reactivate their own socket; foreground loss closes the current connection. */
internal class ForegroundPrintConnection(private val foreground: () -> Boolean) {
    private var active: PrinterTransport? = null

    fun wrap(transport: PrinterTransport): PrinterTransport = object : PrinterTransport {
            override val constraints = transport.constraints
            override suspend fun connect(instance: PrinterInstance) {
                check(foreground()) { "Printing requires a foreground application" }
                if (active !== this) active?.close()
                active = this
                transport.connect(instance)
            }
            override suspend fun write(bytes: ByteArray, offset: Int, length: Int): Int {
                check(foreground()) { "Printing requires a foreground application" }
                return transport.write(bytes, offset, length)
            }
            override fun close() = transport.close()
        }

    fun close() { active?.close() }
}

internal fun bluetoothDiagnostic(failure: TransportFailure): String = "${failure.name}: " + when (failure) {
    TransportFailure.BLUETOOTH_PERMISSION_REQUIRED -> "Allow Nearby devices permission to access paired printers"
    TransportFailure.BLUETOOTH_UNAVAILABLE -> "This Android device has no available Bluetooth adapter"
    TransportFailure.BLUETOOTH_DISABLED -> "Turn Bluetooth on in Android settings"
    TransportFailure.BLUETOOTH_DEVICE_ABSENT -> "The selected device is absent; pair it in Android settings and refresh"
    TransportFailure.BLUETOOTH_BLE_INCOMPATIBLE -> "BLE-only devices need a verified GATT print path; Classic SPP is required here"
    TransportFailure.BLUETOOTH_SPP_CONNECTION_FAILED -> "Classic SPP socket connection failed; verify pairing and printer availability"
    TransportFailure.TRANSPORT_INCOMPATIBLE -> "The selected printer does not use Bluetooth Classic"
}
