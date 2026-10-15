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
    private var configurations = emptyList<ConfiguredPrinter>()
    private val configurationLoaded = CompletableDeferred<Unit>()
    private val mutableSelectedProfile = MutableStateFlow<PrinterProfile?>(null)
    override val selectedProfile: StateFlow<PrinterProfile?> = mutableSelectedProfile
    private val mutablePresence = MutableStateFlow(PrinterPresence.NOT_CHECKED)
    override val presence: StateFlow<PrinterPresence> = mutablePresence
    private var presenceJob: Job? = null
    private val presenceChecks = Mutex()
    private var requestPresencePermission: () -> Unit = {}
    private val profileFileLock = Any()
    override val profileDraftStorage: ProfileDraftStorage = AndroidProfileDraftStorage(context)
    private val mutableDiscovery = MutableStateFlow(DeviceDiscoveryState())
    override val discoveryState: StateFlow<DeviceDiscoveryState> = mutableDiscovery
    private val inspectionMutex = Mutex()
    override suspend fun inspectPrinter(device: PairedPrinterDevice): PrinterInspection = inspectionMutex.withLock {
        if (device !in mutablePairedDevices.value) return@withLock metadataInspection(device, ServiceDiscoveryStatus.UNAVAILABLE)
        try {
            val remote = requireBluetooth(context).bondedDevices.firstOrNull { it.address == device.locator }
                ?: return@withLock metadataInspection(device, ServiceDiscoveryStatus.UNAVAILABLE)
            withContext(Dispatchers.Main.immediate) { inspectBluetoothServices(device, AndroidSdpAccess(context, remote)) }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: SecurityException) { metadataInspection(device, ServiceDiscoveryStatus.UNAVAILABLE)
        } catch (_: Exception) { metadataInspection(device, ServiceDiscoveryStatus.UNAVAILABLE) }
    }
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
    @Volatile private var profileGeneration = 0L
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
        if (!value) { cancelPresence(); connection.close(); imageJob?.cancel() }
    }

    init {
        scope.launch {
            try {
                val configuration = withContext(Dispatchers.IO) {
                    instances.loadConfiguration() ?: run {
                        val legacy = try { synchronized(profileFileLock) { savedProfile.openRead().use { decode(readProfileBytes(it)) } } }
                            catch (_: Exception) { null }
                        legacy?.let(instances::migrate)
                    }
                }
                if (profileGeneration == 0L) mutableProfile.value = configuration?.importedProfile
                if (configuration != null) {
                    if (configurationGeneration == 0L) publishConfiguration(configuration)
                    else { configurations = configuration.printers; confirmedSelectedId = configuration.selectedId }
                }
                if (profileGeneration == 0L && configurationGeneration == 0L) mutableStatus.value = when {
                    configuration != null -> "Saved printer configuration loaded"
                    withContext(Dispatchers.IO) { instances.hasStoredState() || savedProfile.baseFile.exists() } -> "Saved printer configuration is unavailable; review it before replacing it"
                    else -> "Add a paired device, then create or select a validated profile"
                }
            } finally { configurationLoaded.complete(Unit) }
        }
    }

    fun bindActions(permission: () -> Unit, profile: () -> Unit) {
        requestPermission = permission; chooseProfile = profile
    }
    override fun refreshPairedPrinters() {
        mutableDiscovery.value = DeviceDiscoveryState(DeviceDiscoveryStatus.LOADING)
        requestPermission()
    }
    override fun importProfile() = chooseProfile()
    override val supportsProfileExport: Boolean get() = true
    override val supportsProfileDeletion: Boolean get() = true
    override fun deleteProfile() {
        val generation = ++profileGeneration
        profileJob?.cancel()
        profileExportSnapshot = null
        val configurationRequest = ++configurationGeneration
        mutableSelectedProfile.value = null
        cancelPresence()
        connection.close()
        profileJob = scope.launch {
            configurationLoaded.await()
            configurationWrites.withLock {
                val deleted = withContext(Dispatchers.IO) { instances.deleteImportedProfile { generation == profileGeneration } }
                if (deleted != null) {
                    configurations = deleted.printers; confirmedSelectedId = deleted.selectedId
                    if (generation == profileGeneration) mutableProfile.value = deleted.importedProfile
                    if (configurationRequest == configurationGeneration) publishConfiguration(deleted)
                    if (generation == profileGeneration) mutableStatus.value = "Saved profile and matching printer assignments deleted; Bluetooth pairing preserved"
                } else {
                    if (configurationRequest == configurationGeneration) publishConfiguration(PrinterConfiguration(configurations, confirmedSelectedId))
                    if (generation == profileGeneration) mutableStatus.value = "APP146: Profile retained because its printer assignments could not be saved"
                }
            }
        }
    }
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
            mutableDiscovery.value = DeviceDiscoveryState(DeviceDiscoveryStatus.PERMISSION_REQUIRED)
            mutablePairedDevices.value = emptyList()
            mutableStatus.value = "BLUETOOTH_PERMISSION_REQUIRED: Allow Nearby devices to list paired printers"
            return
        }
        mutableDiscovery.value = DeviceDiscoveryState(DeviceDiscoveryStatus.LOADING)
        discoveryJob = scope.launch {
            try {
                val paired = withContext(Dispatchers.IO) { boundedPairedInventory(pairedPrinters(context)) }
                // Preferences expose pending writes in memory before commit confirms disk persistence.
                // Discovery must retain only the association already confirmed by selection persistence.
                mutablePairedDevices.value = paired
                mutableDiscovery.value = DeviceDiscoveryState(DeviceDiscoveryStatus.READY)
                mutableStatus.value = when {
                    paired.isEmpty() -> "BLUETOOTH_DEVICE_ABSENT: No paired devices; pair the printer in Android settings"
                    paired.none { it.supportsClassic } -> "BLUETOOTH_BLE_INCOMPATIBLE: Paired devices are BLE-only; Classic SPP is required"
                    mutableProfile.value == null -> "Choose a paired device, then create or select a validated profile"
                    else -> "Choose a paired Classic device and save its profile association"
                }
            } catch (failure: PrinterTransportException) {
                mutableDiscovery.value = DeviceDiscoveryState(discoveryStatus(failure.failure))
                mutablePairedDevices.value = emptyList()
                mutableStatus.value = bluetoothDiagnostic(failure.failure)
            } catch (_: SecurityException) {
                mutableDiscovery.value = DeviceDiscoveryState(DeviceDiscoveryStatus.PERMISSION_REQUIRED)
                mutablePairedDevices.value = emptyList()
                mutableStatus.value = bluetoothDiagnostic(TransportFailure.BLUETOOTH_PERMISSION_REQUIRED)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutablePairedDevices.value = emptyList()
                mutableDiscovery.value = DeviceDiscoveryState(DeviceDiscoveryStatus.FAILED)
                mutableStatus.value = "BLUETOOTH_DISCOVERY_FAILED: Paired device list could not be read"
            }
        }
    }

    override fun activateProfile(profile: PrinterProfile): Boolean {
        if (PrinterProfileFiles.validate(profile) !is ProfileValidationResult.Success) {
            mutableStatus.value = "Profile failed strict validation"; return false
        }
        ++profileGeneration
        profileJob?.cancel()
        val saved = instances.saveImportedProfile(profile)
        if (!saved) { mutableStatus.value = "APP144: Validated profile could not be saved privately"; return false }
        mutableProfile.value = profile
        instances.loadConfiguration()?.let(::publishConfiguration)
        mutableStatus.value = "Validated profile saved; assign it to a configured printer. Hardware capabilities remain operator supplied."
        return true
    }

    override fun savePrinter(device: PairedPrinterDevice, profile: PrinterProfile) {
        if (device !in mutablePairedDevices.value) {
            mutableStatus.value = bluetoothDiagnostic(TransportFailure.BLUETOOTH_DEVICE_ABSENT); return
        }
        if (!device.supportsClassic) {
            mutableStatus.value = bluetoothDiagnostic(TransportFailure.BLUETOOTH_BLE_INCOMPATIBLE); return
        }
        if (PrinterProfileFiles.validate(profile) !is ProfileValidationResult.Success) {
            mutableStatus.value = "Profile is not a validated physical target"; return
        }
        changeConfiguration { current ->
            val old = current.printers.firstOrNull { it.instance.transportLocator == device.locator }
            val instance = old?.instance?.copy(displayName = device.displayName,
                profileId = profile.profileId, profileRevision = profile.profileRevision, overrides = null,
                defaults = old.instance.defaults.takeIf { it.supports(profile) } ?: PrinterPrintDefaults())
                ?: PrinterInstance(UUID.randomUUID().toString(), device.displayName,
                    PrinterTransportKind.BLUETOOTH_CLASSIC, device.locator, profile.profileId, profile.profileRevision)
            val next = ConfiguredPrinter(instance, profile)
            PrinterConfiguration(current.printers.filterNot { it.instance.id == instance.id } + next, instance.id)
        }
    }

    override fun assignProfile(instance: PrinterInstance, profile: PrinterProfile) {
        if (instance !in mutablePrinters.value || PrinterProfileFiles.validate(profile) !is ProfileValidationResult.Success) return
        changeConfiguration { current ->
            PrinterConfiguration(current.printers.map { configured ->
                if (configured.instance.id != instance.id) configured else ConfiguredPrinter(
                    configured.instance.copy(profileId = profile.profileId, profileRevision = profile.profileRevision,
                        overrides = null, defaults = configured.instance.defaults.takeIf { it.supports(profile) } ?: PrinterPrintDefaults()), profile)
            }, current.selectedId)
        }
    }

    fun profileResult(uri: Uri?) {
        if (uri == null) return
        val generation = ++profileGeneration
        profileJob?.cancel()
        profileJob = scope.launch {
            configurationLoaded.await()
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
                val saved = withContext(Dispatchers.IO) { instances.saveImportedProfile(validated) { generation == profileGeneration } }
                if (!saved) {
                    if (generation == profileGeneration) mutableStatus.value = "APP144: Validated profile could not be saved privately"
                    return@launch
                }
                if (generation != profileGeneration) return@launch
                mutableProfile.value = validated
                mutableStatus.value = "Validated profile imported; choose a device and save its association"
            } catch (_: TimeoutCancellationException) {
                if (generation == profileGeneration) mutableStatus.value = "Profile import timed out"
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                if (generation == profileGeneration) mutableStatus.value = "Profile import failed strict validation or resource checks"
            }
            }
        }
    }

    private fun decode(bytes: ByteArray): PrinterProfile? = when (val result = PrinterProfileFiles.decode(bytes)) {
        is ProfileValidationResult.Success -> result.value.profile
        is ProfileValidationResult.Failure -> null
    }

    private var confirmedSelectedId: String? = null
    private var configurationGeneration = 0L
    private val configurationWrites = Mutex()
    private fun publishConfiguration(configuration: PrinterConfiguration) {
        configurations = configuration.printers
        confirmedSelectedId = configuration.selectedId
        mutablePrinters.value = configurations.map { it.instance }
        val selected = configurations.firstOrNull { it.instance.id == configuration.selectedId }
        mutableSelectedProfile.value = selected?.effectiveProfile()
        mutableSelectedPrinter.value = selected?.instance
    }

    private fun changeConfiguration(change: (PrinterConfiguration) -> PrinterConfiguration) {
        val generation = ++configurationGeneration
        mutableSelectedProfile.value = null
        cancelPresence()
        connection.close()
        scope.launch {
            configurationLoaded.await()
            configurationWrites.withLock {
                // Every write derives from the latest confirmed state, including writes superseded while in flight.
                val current = withContext(Dispatchers.IO) { instances.loadConfiguration() } ?: PrinterConfiguration(configurations, confirmedSelectedId)
                val proposed = change(current)
                val saved = withContext(Dispatchers.IO) { instances.saveConfiguration(proposed.printers, proposed.selectedId) }
                if (saved) { configurations = proposed.printers; confirmedSelectedId = proposed.selectedId }
                if (generation == configurationGeneration) publishConfiguration(PrinterConfiguration(configurations, confirmedSelectedId))
                if (generation == configurationGeneration) mutableStatus.value = if (saved)
                    "Printer configuration saved privately; no bytes transmitted" else "Printer configuration could not be saved; previous selection retained"
            }
        }
    }
    override fun selectPrinter(instance: PrinterInstance) {
        if (instance !in mutablePrinters.value) return
        changeConfiguration { current -> current.copy(selectedId = instance.id.takeIf { id -> current.printers.any { it.instance.id == id } }) }
    }
    override fun removePrinter(instance: PrinterInstance) {
        if (instance !in mutablePrinters.value) return
        changeConfiguration { current -> PrinterConfiguration(current.printers.filterNot { it.instance.id == instance.id },
            current.selectedId.takeUnless { it == instance.id }) }
    }
    override fun updatePrinterDefaults(instance: PrinterInstance, defaults: PrinterPrintDefaults) {
        if (instance !in mutablePrinters.value) return
        changeConfiguration { current -> current.copy(printers = current.printers.map { configured ->
            if (configured.instance.id != instance.id) configured else configured.copy(instance = configured.instance.copy(defaults = defaults))
        }) }
    }
    fun bindPresencePermission(action: () -> Unit) { requestPresencePermission = action }
    override fun refreshPresence() {
        if (mutableSelectedPrinter.value == null || !mutableForeground.value || connection.isActive) return
        cancelPresence()
        requestPresencePermission()
    }
    fun presencePermissionResult(granted: Boolean) {
        cancelPresence()
        if (!granted) { mutablePresence.value = PrinterPresence.PERMISSION_NEEDED; return }
        val selected = mutableSelectedPrinter.value ?: return
        if (!mutableForeground.value || connection.isActive) return
        mutablePresence.value = PrinterPresence.CHECKING
        presenceJob = scope.launch {
            presenceChecks.withLock {
            try {
                val adapter = requireBluetooth(context)
                val result = checkBluetoothPresence(selected.transportLocator, AndroidBluetoothPresenceAccess(context, adapter))
                if (selected == mutableSelectedPrinter.value) mutablePresence.value = result
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: PrinterTransportException) {
                mutablePresence.value = when (failure.failure) {
                    TransportFailure.BLUETOOTH_DISABLED -> PrinterPresence.BLUETOOTH_DISABLED
                    TransportFailure.BLUETOOTH_PERMISSION_REQUIRED -> PrinterPresence.PERMISSION_NEEDED
                    else -> PrinterPresence.UNABLE_TO_CHECK
                }
            } catch (_: SecurityException) { mutablePresence.value = PrinterPresence.PERMISSION_NEEDED
            } catch (_: Exception) { mutablePresence.value = PrinterPresence.UNABLE_TO_CHECK }
            }
        }
    }
    override fun cancelPresence() {
        presenceJob?.cancel()
        mutablePresence.value = PrinterPresence.NOT_CHECKED
    }
    override fun createTransport(): PrinterTransport {
        val delegate = connection.wrap(BluetoothPrinterTransport(context, TransportConstraints(1024)))
        return object : PrinterTransport by delegate {
            override suspend fun connect(instance: PrinterInstance) {
                val checking = presenceJob
                cancelPresence()
                checking?.join()
                presenceChecks.withLock { }
                try { requireBluetooth(context).cancelDiscovery() } catch (_: SecurityException) { }
                check(instance == mutableSelectedPrinter.value && mutableSelectedProfile.value != null) { "Printer configuration changed before connection" }
                delegate.connect(instance)
            }
        }
    }

}

/** Cached sessions reactivate their own socket; foreground loss closes the current connection. */
internal class ForegroundPrintConnection(private val foreground: () -> Boolean) {
    private val active = AtomicReference<PrinterTransport?>()
    val isActive: Boolean get() = active.get() != null

    fun wrap(transport: PrinterTransport): PrinterTransport = object : PrinterTransport {
            override val constraints = transport.constraints
            override suspend fun connect(instance: PrinterInstance) {
                check(foreground()) { "Printing requires a foreground application" }
                active.getAndSet(this)?.takeIf { it !== this }?.close()
                transport.connect(instance)
            }
            override suspend fun write(bytes: ByteArray, offset: Int, length: Int): Int {
                check(foreground()) { "Printing requires a foreground application" }
                return transport.write(bytes, offset, length)
            }
            override fun close() {
                active.compareAndSet(this, null)
                transport.close()
            }
        }

    fun close() { active.getAndSet(null)?.close() }
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
