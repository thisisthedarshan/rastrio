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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.circuitnext.rastrio.core.markdown.MarkdownDiagnostic
import com.circuitnext.rastrio.core.markdown.MarkdownSeverity
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.document.ThermalDocument
import com.circuitnext.rastrio.core.text.createPlatformRasterTextBackend
import com.circuitnext.rastrio.core.preview.LogicalPreview
import com.circuitnext.rastrio.core.templates.*
import com.circuitnext.rastrio.core.document.AssetReference
import com.circuitnext.rastrio.core.document.EmbeddedAssetReference
import com.circuitnext.rastrio.core.document.ExternalAssetReference
import com.circuitnext.rastrio.core.raster.RasterOptions
import com.circuitnext.rastrio.core.profile.PrinterProfile
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import com.circuitnext.rastrio.presentation.*
import com.circuitnext.rastrio.printing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job



@Stable
internal class ObservablePhysicalPreview(private val session: PhysicalPreviewSession) {
    var state by mutableStateOf(session.state)
        private set
    fun invalidate() { session.invalidate(); state = session.state }
    fun configure(profile: PrinterProfile, options: PrintOptions) {
        session.configure(profile, options); state = session.state
    }
    fun resolveImage(reference: AssetReference, image: ResolvedRasterImage): Boolean {
        val resolved = session.resolveImage(reference, image); state = session.state; return resolved
    }
    fun prepare(authoring: MarkdownAuthoringResult, profile: PrinterProfile = state.profile,
        options: PrintOptions = state.options) {
        session.prepare(authoring, profile, options); state = session.state
    }
    suspend fun prepareAsync(document: ThermalDocument, profile: PrinterProfile = state.profile,
        options: PrintOptions = state.options) {
        try { session.prepareAsync(document, profile, options) } finally { state = session.state }
    }
    suspend fun prepareAsync(authoring: MarkdownAuthoringResult, profile: PrinterProfile = state.profile,
        options: PrintOptions = state.options) {
        try { session.prepareAsync(authoring, profile, options) } finally { state = session.state }
    }
}

@Stable
internal class ObservableImagePrint(private val session: ImagePrintSession) {
    var state by mutableStateOf(session.state)
        private set
    fun select(input: ImageInput?) { session.select(input); state = session.state }
    fun edit(edits: ImagePrintEdits) { session.edit(edits); state = session.state }
    fun configure(profile: PrinterProfile, options: PrintOptions = state.options) {
        session.configure(profile, options); state = session.state
    }
    fun invalidate() { session.invalidate(); state = session.state }
    fun prepare() { session.prepare(); state = session.state }
    suspend fun prepareAsync() { try { session.prepareAsync() } finally { state = session.state } }
}

/** Observation only. The session remains the sole owner of edits, outcomes and invalidation. */
@Stable
internal class ObservableAuthoring(private val session: MarkdownAuthoringSession,
    private val physical: ObservablePhysicalPreview) {
    var state by mutableStateOf(session.state)
        private set
    fun edit(source: String) {
        if (source != state.source) physical.invalidate()
        session.edit(source); state = session.state
    }
    fun compile() { session.compile(); state = session.state }
}

@Composable
fun RastrIOApp(session: MarkdownAuthoringSession? = null, printingPlatform: PrintingPlatform? = null,
    printingSessions: PrinterSessionStore? = null, imagePlatform: ImagePlatform? = printingPlatform as? ImagePlatform,
    backHandler: @Composable (Boolean, () -> Unit) -> Unit = { _, _ -> }) {
    val textBackend = remember { createPlatformRasterTextBackend() }
    val physical = remember(session) { ObservablePhysicalPreview(PhysicalPreviewSession(rasterTextBackend = textBackend)) }
    var templateForms by rememberTemplateFormsState()
    var templateDocument by remember { mutableStateOf<ThermalDocument?>(null) }
    var templateLogical by remember { mutableStateOf<LogicalPreview?>(null) }
    var templateStatus by remember { mutableStateOf("") }
    val authoring = remember(session) { ObservableAuthoring(session ?: createAuthoringSession(rasterTextBackend = textBackend), physical) }
    val pickedImage = imagePlatform?.image?.collectAsState()?.value
    var savedImageEdits by rememberSaveable(stateSaver = ImagePrintEditsSaver) { mutableStateOf(ImagePrintEdits()) }
    val image = remember {
        ObservableImagePrint(ImagePrintSession().apply {
            select(pickedImage)
            if (pickedImage != null) edit(savedImageEdits)
        })
    }
    val imageStatus = imagePlatform?.imageStatus?.collectAsState()?.value.orEmpty()
    var resolutionStatus by remember { mutableStateOf("") }
    var navigation by rememberSaveable(stateSaver = Saver<AppNavigation, List<String>>(
        save = { it.pages.map(AuthoringRoute::name) }, restore = { AppNavigation.restore(it) },
    )) { mutableStateOf(AppNavigation()) }
    val route = navigation.route
    val navigate: (AuthoringRoute) -> Unit = { navigation = navigation.open(it) }
    val printing = remember(printingPlatform, printingSessions) { printingSessions ?: printingPlatform?.let { PrinterSessionStore(it::createTransport) } }
    var selectedLocator by remember(printingPlatform) { mutableStateOf<String?>(null) }
    val printers = printingPlatform?.printers?.collectAsState()?.value ?: emptyList()
    val pairedDevices = printingPlatform?.pairedDevices?.collectAsState()?.value ?: emptyList()
    val restoredPrinter = printingPlatform?.selectedPrinter?.collectAsState()?.value
    var addingPrinter by rememberSaveable { mutableStateOf(false) }
    var savingPrinter by rememberSaveable { mutableStateOf(false) }
    var setupStep by rememberSaveable { mutableStateOf(PrinterSetupStep.DEVICE) }
    var deviceListExpanded by rememberSaveable { mutableStateOf(true) }
    var pendingDevice by rememberSaveable(stateSaver = Saver<PairedPrinterDevice?, List<String>>(
        save = { it?.let { listOf(it.locator, it.displayName, it.kind.name) } ?: emptyList() },
        restore = { values -> runCatching { if (values.size != 3) null else PairedPrinterDevice(values[0], values[1], BluetoothDeviceKind.valueOf(values[2])) }.getOrNull() },
    )) { mutableStateOf<PairedPrinterDevice?>(null) }
    val discoveryState = printingPlatform?.discoveryState?.collectAsState()?.value ?: DeviceDiscoveryState()
    var inspection by remember { mutableStateOf<PrinterInspection?>(null) }
    var inspectionStatus by remember { mutableStateOf("") }
    var inspecting by remember { mutableStateOf(false) }
    var inspectionJob by remember { mutableStateOf<Job?>(null) }
    var inspectionGeneration by remember { mutableLongStateOf(0L) }
    val localDraftStorage = remember(printingPlatform) { printingPlatform?.profileDraftStorage ?: MemoryProfileDraftStorage() }
    var profileDrafts by remember(localDraftStorage) { mutableStateOf(localDraftStorage.loadDrafts()) }
    var editingDraft by rememberSaveable(stateSaver = Saver<ProfileDraft?, String>(
        save = { draft -> draft?.let { runCatching { it.encodeLocal() }.getOrNull() } },
        restore = { ProfileDraft.decodeLocal(it) },
    )) { mutableStateOf<ProfileDraft?>(null) }
    var profileEditorStatus by remember { mutableStateOf("") }
    val saveDraft: () -> Boolean = {
        val draft = editingDraft
        if (draft != null) {
            val next = profileDrafts.filterNot { it.draftId == draft.draftId } + draft
            val saved = runCatching { localDraftStorage.saveDrafts(next) }.getOrDefault(false)
            if (saved) profileDrafts = next
            profileEditorStatus = if (saved) "Local draft saved; no printer connection or output was started"
                else "APP145: Draft could not be saved (maximum 32 bounded drafts)"
            saved
        } else false
    }
    val newDraftId = { "org.rastrio.local.p" + kotlin.random.Random.nextLong().toULong().toString(16) }
    val importedProfile = printingPlatform?.profile?.collectAsState()?.value
    val boundProfile = printingPlatform?.selectedProfile?.collectAsState()?.value
    val presence = printingPlatform?.presence?.collectAsState()?.value ?: PrinterPresence.NOT_CHECKED
    val platformStatus = printingPlatform?.status?.collectAsState()?.value.orEmpty()
    val foreground = printingPlatform?.foreground?.collectAsState()?.value ?: true
    // Only a confirmed saved selection may drive a hardware print; development fixtures have no persistence.
    val selected = printers.find { it == restoredPrinter } ?: printers.find {
        it.transportKind == PrinterTransportKind.DEVELOPMENT && it.transportLocator == selectedLocator
    }
    var jobOptions by remember(selected) { mutableStateOf<PrintOptions?>(null) }
    val resolvedOptions = jobOptions ?: selected?.defaults?.toPrintOptions() ?: PrintOptions()
    val printSession = selected?.let { printing?.session(it) }
    val printState = printSession?.state?.collectAsState()?.value ?: PrintSessionState()
    val scope = rememberCoroutineScope()
    var preparationJob by remember { mutableStateOf<Job?>(null) }
    val cancelPreparation = { preparationJob?.cancel(); preparationJob = null }
    val startPreparation: (suspend () -> Unit) -> Unit = { work ->
        cancelPreparation()
        val task = scope.launch(start = CoroutineStart.LAZY) {
            val running = currentCoroutineContext().job
            try { work() } finally { if (preparationJob === running) preparationJob = null }
        }
        preparationJob = task
        task.start()
    }
    val preparePhysical: (PrinterProfile, PrintOptions) -> Unit = { profile, options ->
        val document = templateDocument
        val templateRoute = route == AuthoringRoute.TEMPLATES || route == AuthoringRoute.TEMPLATE_PREVIEW || route == AuthoringRoute.TEMPLATE_CONFIRMATION || route == AuthoringRoute.TEMPLATE_LOGICAL_PREVIEW || route == AuthoringRoute.TEMPLATE_SETTINGS
        if (templateRoute) {
            if (document != null) startPreparation { physical.prepareAsync(document, profile, options) }
            else {
                cancelPreparation()
                physical.configure(profile, options)
                templateStatus = "Preview template to prepare its current content"
            }
        } else {
            val request = authoring.state.result
            startPreparation { physical.prepareAsync(request, profile, options) }
        }
    }
    val enterAuthoring: (AuthoringRoute) -> Unit = { destination ->
        printing?.cancelAll(); cancelPreparation(); physical.invalidate()
        navigate(destination)
    }
    DisposableEffect(printing) { onDispose { printing?.cancelAll(); preparationJob?.cancel(); inspectionJob?.cancel(); printingPlatform?.cancelPresence() } }
    LaunchedEffect(route, foreground) {
        printing?.cancelAll(); cancelPreparation(); printingPlatform?.cancelPresence()
        if (route != AuthoringRoute.ADD_PRINTER || !foreground) {
            if (inspecting) inspectionStatus = "Inspection cancelled"
            inspectionGeneration++; inspectionJob?.cancel(); inspecting = false
        }
    }
    LaunchedEffect(printingPlatform, route, foreground, discoveryState.status) {
        if (route == AuthoringRoute.ADD_PRINTER && foreground && discoveryState.status == DeviceDiscoveryStatus.IDLE)
            printingPlatform?.refreshPairedPrinters()
    }
    LaunchedEffect(route, editingDraft == null) {
        if (route == AuthoringRoute.PROFILE_EDITOR && editingDraft == null) navigate(AuthoringRoute.PRINTER_MANAGEMENT)
    }
    LaunchedEffect(restoredPrinter, savingPrinter) {
        if (restoredPrinter != null) {
            selectedLocator = restoredPrinter.transportLocator
            if (savingPrinter && pendingDevice?.locator == restoredPrinter.transportLocator &&
                pendingDevice?.displayName == restoredPrinter.displayName && importedProfile?.profileId == restoredPrinter.profileId &&
                importedProfile.profileRevision == restoredPrinter.profileRevision) {
                savingPrinter = false; addingPrinter = false; pendingDevice = null
                if (route == AuthoringRoute.ADD_PRINTER) navigate(AuthoringRoute.PRINTER_MANAGEMENT)
            }
        }
    }
    LaunchedEffect(pickedImage) {
        if (image.state.input != pickedImage) {
            printing?.cancelAll(); cancelPreparation(); image.select(pickedImage); physical.invalidate()
            savedImageEdits = ImagePrintEdits()
            resolutionStatus = "Picking another image releases previous Markdown image bindings"
        }
    }
    val imageRoute = route == AuthoringRoute.IMAGE || route == AuthoringRoute.IMAGE_PREVIEW
    val currentResult = if (preparationJob != null) PhysicalPreviewResult.NotPrepared
        else if (imageRoute) image.state.result else physical.state.result
    val currentProfile = if (imageRoute) image.state.profile else physical.state.profile
    val resolveMarkdownImage: (AssetReference) -> Unit = { reference ->
        val input = pickedImage
        if (input != null) {
            printing?.cancelAll(); cancelPreparation()
            if (physical.resolveImage(reference, ResolvedRasterImage(input.image,
                    RasterOptions(input.image.width, input.image.height, orientation = input.orientation)))) {
                resolutionStatus = "Selected image explicitly associated with this Markdown image reference"
            } else resolutionStatus = "Markdown image binding limit reached"
        }
    }
    val choosePrinter: (PrinterInstance) -> Unit = { instance ->
        printing?.cancelAll(); cancelPreparation(); printingPlatform?.cancelPresence()
        physical.configure(physical.state.profile, physical.state.options); image.invalidate()
        if (instance.transportKind == PrinterTransportKind.DEVELOPMENT) selectedLocator = instance.transportLocator
        printingPlatform?.selectPrinter(instance)
    }
    val printingControls: @Composable () -> Unit = {
        if (printingPlatform != null) PrinterConnectionDetails(platformStatus, selected, printingPlatform::refreshPairedPrinters)
    }
    val confirmationRoute = route in listOf(AuthoringRoute.PRINTER_SETUP, AuthoringRoute.PHYSICAL_PREVIEW,
        AuthoringRoute.TEMPLATE_CONFIRMATION, AuthoringRoute.IMAGE_PREVIEW)
    val confirmationDocument = if (route == AuthoringRoute.TEMPLATE_CONFIRMATION) templateDocument else authoring.state.result.semanticDocument()
    var preparedSelection by remember { mutableStateOf<PrinterInstance?>(null) }
    var preparedDocument by remember { mutableStateOf<ThermalDocument?>(null) }
    LaunchedEffect(confirmationRoute, route, selected, boundProfile, resolvedOptions, confirmationDocument, foreground) {
        if (confirmationRoute) {
            cancelPreparation(); preparedSelection = null; preparedDocument = null
            if (selected != null && boundProfile != null && foreground) {
                if (imageRoute) image.configure(boundProfile, resolvedOptions)
                else {
                    physical.configure(boundProfile, resolvedOptions)
                    confirmationDocument?.let { document ->
                        startPreparation {
                            physical.prepareAsync(document, boundProfile, resolvedOptions)
                            preparedSelection = selected; preparedDocument = document
                        }
                    }
                }
            } else { physical.invalidate(); image.invalidate() }
        }
    }
    LaunchedEffect(route, imageRoute, selected, foreground, image.state.input, image.state.edits, image.state.profile, image.state.options) {
        if (imageRoute && foreground && image.state.input != null) {
            startPreparation {
                delay(120)
                image.prepareAsync()
                if (route == AuthoringRoute.IMAGE_PREVIEW) preparedSelection = selected
            }
        }
    }
    val changeJobOptions: (PrintOptions) -> Unit = { options ->
        printing?.cancelAll(); cancelPreparation(); physical.configure(physical.state.profile, options); image.invalidate()
        jobOptions = options
    }
    val confirmationResult = if (!confirmationRoute || selected == null || boundProfile == null ||
        preparationJob != null || preparedSelection != selected || currentProfile != boundProfile ||
        (if (imageRoute) image.state.options != resolvedOptions else physical.state.options != resolvedOptions || preparedDocument != confirmationDocument))
        PhysicalPreviewResult.NotPrepared else currentResult
    val confirmationReady = confirmationResult as? PhysicalPreviewResult.Ready
    val confirmationEligible = foreground && canPrintPrepared(selected, boundProfile, confirmationReady?.print, currentProfile) && printSession != null
    val transmitConfirmation: () -> Unit = {
        val ready = confirmationReady
        if (confirmationEligible && ready != null && selected != null && boundProfile != null && printSession != null &&
            preparationJob == null && (if (imageRoute) image.state.result === ready else physical.state.result === ready) &&
            printingPlatform?.foreground?.value == true && printingPlatform.selectedProfile.value == boundProfile &&
            (printingPlatform.selectedPrinter.value == selected || selected.transportKind == PrinterTransportKind.DEVELOPMENT)) {
            printingPlatform.cancelPresence()
            scope.launch {
                // Recheck after dispatch: a selection/content edit can happen before this coroutine starts.
                if (preparationJob == null && printingPlatform.foreground.value &&
                    printingPlatform.selectedProfile.value == boundProfile &&
                    (printingPlatform.selectedPrinter.value == selected || selected.transportKind == PrinterTransportKind.DEVELOPMENT) &&
                    (if (imageRoute) image.state.result === ready else physical.state.result === ready))
                    printSession.print(selected, ready.print, boundProfile.writeGuidance())
            }
        }
    }
    val goBack: () -> Unit = {
        printing?.cancelAll(); cancelPreparation()
        inspectionGeneration++; inspectionJob?.cancel(); inspecting = false
        if (route == AuthoringRoute.ADD_PRINTER && setupStep != PrinterSetupStep.DEVICE) {
            setupStep = PrinterSetupStep.entries[setupStep.ordinal - 1]
            if (setupStep == PrinterSetupStep.DEVICE) deviceListExpanded = true
        } else {
            navigation = navigation.back()
            if (navigation.route == AuthoringRoute.PRINTER_MANAGEMENT) { addingPrinter = false; savingPrinter = false }
        }
    }
    backHandler(navigation.canGoBack, goBack)
    val markdownAssets: @Composable () -> Unit = {
        val references = remember(authoring.state.result) {
            authoring.state.result.semanticDocument()?.let(::markdownImageReferences).orEmpty()
        }
        var expanded by remember { mutableStateOf(false) }
        if (references.isNotEmpty()) {
            TextButton(onClick = { expanded = !expanded }) { Text("Resolve document images (${references.size})") }
            if (expanded) Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                Text("Images are resolved explicitly from local pixels; links are never fetched.", style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = imagePlatform != null, onClick = { imagePlatform?.importImage() }) { Text("Choose Markdown image") }
                if (resolutionStatus.isNotBlank()) Text(resolutionStatus, style = MaterialTheme.typography.bodySmall)
                for ((index, reference) in references.withIndex()) TextButton(enabled = pickedImage != null,
                    onClick = { resolveMarkdownImage(reference) }) { Text("Use selected pixels for image ${index + 1}") }
            }
        }
    }
    val confirmation: @Composable () -> Unit = {
        PrintConfirmationScreen(printers, selected, boundProfile, confirmationResult,
            preparationJob != null, confirmationEligible, printSession, printState, presence,
            onSelect = choosePrinter, onRefresh = { printingPlatform?.refreshPresence() }, onPrint = transmitConfirmation,
            onBack = goBack, onManage = { navigate(AuthoringRoute.PRINTER_MANAGEMENT) },
            onAdd = { addingPrinter = true; setupStep = PrinterSetupStep.DEVICE; navigate(AuthoringRoute.ADD_PRINTER) },
            options = resolvedOptions, onOptions = changeJobOptions,
            imageOptions = if (imageRoute) ({ ImageAdjustmentControls(image.state, onEdit = {
                printing?.cancelAll(); cancelPreparation(); savedImageEdits = it; image.edit(it)
            }, enabled = !printState.isActive) }) else null,
            showQrOptions = route == AuthoringRoute.TEMPLATE_CONFIRMATION && templateForms.selected == TemplateKind.QR,
            contentError = if (imageRoute) image.state.editDiagnostic else null,
            configurationStatus = platformStatus.takeIf { "could not" in it || "failed" in it.lowercase() }.orEmpty())
    }
    AuthoringTheme {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.background) {
            when (route) {
                AuthoringRoute.HOME -> HomeScreen(onImage = { navigate(AuthoringRoute.IMAGE) },
                    onTemplates = { enterAuthoring(AuthoringRoute.TEMPLATE_GALLERY) },
                    onPrinters = { enterAuthoring(AuthoringRoute.PRINTER_MANAGEMENT) }) { enterAuthoring(AuthoringRoute.MARKDOWN) }
                AuthoringRoute.PRINTER_MANAGEMENT -> PrinterManagementScreen(physical.state.profile,
                    printingControls = printingControls, printers = printers, onSelect = { choosePrinter(it) },
                    selectedPrinter = selected, selectedProfile = boundProfile,
                    onDefaults = { printer, defaults -> printingPlatform?.updatePrinterDefaults(printer, defaults) },
                    onRemove = { printer -> printingPlatform?.removePrinter(printer) },
                    onAssignProfile = { printer, profile -> printingPlatform?.assignProfile(printer, profile) },
                    importedProfile = importedProfile, onImport = { printingPlatform?.importProfile() },
                    onExport = if (printingPlatform?.supportsProfileExport == true) ({ printingPlatform.exportProfile() }) else null,
                    onDeleteProfile = if (printingPlatform?.supportsProfileDeletion == true) ({
                        printing?.cancelAll(); cancelPreparation(); profileEditorStatus = ""; printingPlatform.deleteProfile()
                    }) else null,
                    onDeleteDraft = { draft ->
                        val next = profileDrafts.filterNot { it.draftId == draft.draftId }
                        if (runCatching { localDraftStorage.saveDrafts(next) }.getOrDefault(false)) {
                            profileDrafts = next
                            if (editingDraft?.draftId == draft.draftId) editingDraft = null
                            profileEditorStatus = "Local draft deleted"
                        } else profileEditorStatus = "APP145: Draft could not be deleted; it was retained"
                    },
                    managementStatus = profileEditorStatus.ifBlank { platformStatus },
                    onBack = { navigate(AuthoringRoute.HOME) },
                    onLab = { navigate(AuthoringRoute.PRINTER_LAB) },
                    onCreate = {
                        editingDraft = ProfileDraft.fresh(newDraftId()); profileEditorStatus = ""
                        enterAuthoring(AuthoringRoute.PROFILE_EDITOR)
                    },
                    drafts = profileDrafts,
                    onEdit = { editingDraft = it; profileEditorStatus = ""; enterAuthoring(AuthoringRoute.PROFILE_EDITOR) },
                    onDuplicate = {
                        editingDraft = ProfileDraft.fromProfile(importedProfile ?: physical.state.profile, newDraftId())
                        profileEditorStatus = "Duplicated into a local draft; review identity and evidence"
                        enterAuthoring(AuthoringRoute.PROFILE_EDITOR)
                    },
                    onDuplicateDraft = {
                        val id = newDraftId()
                        editingDraft = it.copy(draftId = id).withField("profileId", id)
                        profileEditorStatus = "Duplicated local draft; synthetic origin and evidence are preserved"
                        enterAuthoring(AuthoringRoute.PROFILE_EDITOR)
                    },
                    onAdd = {
                        addingPrinter = true; savingPrinter = false; setupStep = PrinterSetupStep.DEVICE; deviceListExpanded = true
                        enterAuthoring(AuthoringRoute.ADD_PRINTER); printingPlatform?.refreshPairedPrinters()
                    },
                    onProfile = {
                        printing?.cancelAll(); cancelPreparation()
                        physical.configure(it, physical.state.options.copy(cutIntent = CutIntent.NONE))
                        image.configure(it)
                    })
                AuthoringRoute.ADD_PRINTER -> AddPrinterScreen(
                    devices = pairedDevices, discovery = discoveryState, selected = pendingDevice,
                    step = setupStep, onStep = {
                        inspectionGeneration++; inspectionJob?.cancel(); inspecting = false; setupStep = it
                    }, listExpanded = deviceListExpanded,
                    onExpand = {
                        deviceListExpanded = it
                        if (it && inspecting) { inspectionGeneration++; inspectionJob?.cancel(); inspecting = false; inspectionStatus = "Inspection cancelled" }
                    },
                    onDevice = { device ->
                        if (pendingDevice?.locator != device.locator) {
                            savingPrinter = false;
                            inspectionGeneration++; inspectionJob?.cancel(); inspecting = false; inspection = null; inspectionStatus = ""
                            printing?.cancelAll(); cancelPreparation(); physical.invalidate(); image.invalidate(); selectedLocator = null
                        }
                        pendingDevice = device
                    },
                    onRefresh = { printingPlatform?.refreshPairedPrinters() }, inspection = inspection,
                    inspecting = inspecting, inspectionStatus = inspectionStatus,
                    onInspect = {
                        val device = pendingDevice
                        if (device != null && printingPlatform != null && foreground) {
                            inspectionGeneration++; val request = inspectionGeneration
                            inspectionJob?.cancel(); inspection = null; inspectionStatus = "Inspecting advertised services…"; inspecting = true
                            inspectionJob = scope.launch {
                                try {
                                    val result = withTimeout(10_000) { printingPlatform.inspectPrinter(device) }
                                    if (request == inspectionGeneration && pendingDevice == device && result.device == device) {
                                        inspection = result
                                        inspectionStatus = when (result.serviceDiscovery) {
                                            ServiceDiscoveryStatus.TIMEOUT -> "Inspection timed out. Cached observations may be shown; no automatic retry started."
                                            ServiceDiscoveryStatus.MALFORMED -> "The service response could not be read. Continue with an incomplete draft."
                                            ServiceDiscoveryStatus.UNAVAILABLE, ServiceDiscoveryStatus.UNSUPPORTED -> "Only paired metadata is available. Printer queries are unsupported."
                                            else -> "Inspection finished · observations may be cached; no print commands sent"
                                        }
                                    }
                                } catch (_: TimeoutCancellationException) {
                                    if (request == inspectionGeneration) inspectionStatus = "Inspection timed out. No automatic retry was started."
                                } catch (cancelled: CancellationException) { throw cancelled
                                } catch (_: Exception) {
                                    if (request == inspectionGeneration) inspectionStatus = "Inspection unavailable. Continue with an incomplete draft."
                                } finally { if (request == inspectionGeneration) inspecting = false }
                            }
                        }
                    },
                    onCancelInspection = { inspectionGeneration++; inspectionJob?.cancel(); inspecting = false; inspectionStatus = "Inspection cancelled" },
                    profileName = importedProfile?.display?.name,
                    onCreate = {
                        val fresh = ProfileDraft.fresh(newDraftId())
                        val name = pendingDevice?.let { metadataInspection(it).suggestions.single() }
                        editingDraft = if (name == null) fresh else fresh.withSuggestions(listOf(name)).acceptSuggestion(name)
                        profileEditorStatus = "Paired-device name prefilled; model and print capabilities remain unknown."
                        enterAuthoring(AuthoringRoute.PROFILE_EDITOR)
                    },
                    onImport = { printingPlatform?.importProfile() },
                    onSelfTestDraft = {
                        val device = pendingDevice
                        if (device != null) {
                            val suggestions = metadataInspection(device).suggestions + genericTwoInchSuggestions(device)
                            editingDraft = suggestions.fold(ProfileDraft.fresh(newDraftId()).withSuggestions(suggestions)) { value, suggestion -> value.acceptSuggestion(suggestion) }
                            profileEditorStatus = "2-inch starter suggestions need review. Geometry and host commands remain unknown."
                            enterAuthoring(AuthoringRoute.PROFILE_EDITOR)
                        }
                    }, drafts = profileDrafts,
                    onEdit = { editingDraft = it; profileEditorStatus = ""; enterAuthoring(AuthoringRoute.PROFILE_EDITOR) },
                    onLab = { if (editingDraft == null) editingDraft = ProfileDraft.fresh(newDraftId()); navigate(AuthoringRoute.PRINTER_LAB) },
                    onSavePrinter = {
                        val device = pendingDevice
                        if (device != null && importedProfile != null) { savingPrinter = true; printingPlatform?.savePrinter(device, importedProfile) }
                    },
                    onBack = goBack, technicalStatus = platformStatus,
                )
                AuthoringRoute.PROFILE_EDITOR -> editingDraft?.let { draft ->
                    ProfileEditorScreen(draft, onDraft = { editingDraft = it; profileEditorStatus = "" },
                        onSave = { saveDraft() },
                        onActivate = { profile ->
                            if (saveDraft()) {
                            if (printingPlatform?.activateProfile(profile) == true) {
                                physical.configure(profile, physical.state.options.copy(cutIntent = CutIntent.NONE))
                                image.configure(profile)
                                setupStep = PrinterSetupStep.VERIFY
                                navigate(if (addingPrinter) AuthoringRoute.ADD_PRINTER else AuthoringRoute.PRINTER_MANAGEMENT)
                            } else profileEditorStatus = "APP144: This platform could not activate the validated profile"
                            }
                        },
                        onBack = { goBack() },
                        onLab = { setupStep = PrinterSetupStep.VERIFY; navigate(AuthoringRoute.PRINTER_LAB) }, status = profileEditorStatus,
                        selectedDevice = if (addingPrinter) pendingDevice else null, suggestions = if (addingPrinter) inspection?.suggestions.orEmpty() else emptyList())
                }
                AuthoringRoute.PRINTER_LAB -> PrinterLabScreen(
                    calibrationControls = { CalibrationControls(editingDraft, printingPlatform, onEdit = {
                        if (editingDraft == null) editingDraft = ProfileDraft.fresh(newDraftId())
                        enterAuthoring(AuthoringRoute.PROFILE_EDITOR)
                    }, setupDevice = if (addingPrinter) pendingDevice else null, onDraft = {
                        editingDraft = it; profileEditorStatus = "Measurements applied to draft; repeat and verify before activation."
                        saveDraft()
                    }) },
                    onBack = { goBack() },
                    onQrFixture = {
                        enterAuthoring(AuthoringRoute.TEMPLATES)
                        templateForms = templateForms.copy(selected = TemplateKind.QR,
                            qr = QrMessageState("https://example.invalid/rastrio/hw-018", errorCorrection = com.circuitnext.rastrio.core.document.QrErrorCorrection.HIGH),
                            qrSizeMm = "30")
                        templateDocument = null; templateLogical = null
                        physical.configure(physical.state.profile, physical.state.options.copy(qrStrategy = QrStrategy.RASTER, cutIntent = CutIntent.NONE))
                        templateStatus = "Raster QR test draft. Prepare and inspect before explicitly printing once."
                    })
                AuthoringRoute.TEMPLATE_GALLERY -> TemplateGallery(onSelect = { kind ->
                    templateForms = templateForms.copy(selected = kind)
                    enterAuthoring(AuthoringRoute.TEMPLATES)
                }, onBack = { navigate(AuthoringRoute.HOME) })
                AuthoringRoute.TEMPLATES -> TemplateScreen(templateForms,
                    onForms = { templateForms = it; templateDocument = null; templateLogical = null; templateStatus = ""; printing?.cancelAll(); cancelPreparation(); physical.invalidate() },
                    onPreview = { document ->
                        templateDocument = document
                        startPreparation {
                            physical.prepareAsync(document, boundProfile ?: physical.state.profile, resolvedOptions)
                            if (physical.state.result is PhysicalPreviewResult.Ready) navigate(AuthoringRoute.TEMPLATE_PREVIEW)
                        }
                    },
                    onQrPreview = { document ->
                        templateDocument = document
                        startPreparation {
                            physical.prepareAsync(document, boundProfile ?: physical.state.profile, resolvedOptions)
                            if (physical.state.result is PhysicalPreviewResult.Ready) navigate(AuthoringRoute.TEMPLATE_PREVIEW)
                        }
                    },
                    onBack = goBack, busy = printState.isActive || preparationJob != null,
                    previewStatus = when (val result = physical.state.result) {
                        is PhysicalPreviewResult.PreparationFailed -> result.diagnostics.joinToString("\n") { "${it.code}: ${it.message}" }
                        is PhysicalPreviewResult.PreviewFailed -> result.diagnostics.joinToString("\n") { "${it.code}: ${it.message}" }
                        is PhysicalPreviewResult.Ready -> "Physical preview ready. $templateStatus"
                        else -> templateStatus
                    })
                AuthoringRoute.TEMPLATE_SETTINGS -> {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Printer settings", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = goBack) { Text("Back to preview") }
                        Text("Preview target: ${physical.state.profile.display.name}")
                        if (importedProfile != null) TextButton(enabled = preparationJob == null && !printState.isActive,
                            onClick = { preparePhysical(importedProfile, physical.state.options) }) { Text("Preview imported profile") }
                        if (boundProfile != null) TextButton(enabled = preparationJob == null && !printState.isActive,
                            onClick = { preparePhysical(boundProfile, resolvedOptions) }) { Text("Prepare selected printer") }
                        if (preparationJob != null) Text("Preparing preview…") else when (val result = physical.state.result) {
                            is PhysicalPreviewResult.PreparationFailed -> result.diagnostics.forEach { Text("${it.code}: ${it.message}") }
                            is PhysicalPreviewResult.PreviewFailed -> result.diagnostics.forEach { Text("${it.code}: ${it.message}") }
                            is PhysicalPreviewResult.Ready -> Text("Physical preview ready.")
                            else -> Text("Preview template to prepare its current content")
                        }
                        Text("QR strategy", style = MaterialTheme.typography.titleMedium)
                        Row {
                            for (strategy in QrStrategy.entries) TextButton(enabled = preparationJob == null && !printState.isActive, onClick = { preparePhysical(physical.state.profile, physical.state.options.copy(qrStrategy = strategy)) }) { Text("QR ${strategy.name.lowercase()}") }
                        }
                        Text("Software preview targets", style = MaterialTheme.typography.titleMedium)
                        Row {
                            TextButton(enabled = preparationJob == null && !printState.isActive, onClick = { preparePhysical(SyntheticReferenceProfiles.narrow, physical.state.options) }) { Text("Narrow preview") }
                            TextButton(enabled = preparationJob == null && !printState.isActive, onClick = { preparePhysical(SyntheticReferenceProfiles.wide80mm, physical.state.options) }) { Text("Wide preview") }
                        }
                    }
                }
                AuthoringRoute.TEMPLATE_LOGICAL_PREVIEW -> {
                    val preview = templateLogical
                    if (preview != null) PreviewScreen(preview, backLabel = "Back to preview", onBack = goBack)
                    else LaunchedEffect(Unit) { navigate(AuthoringRoute.TEMPLATES) }
                }
                AuthoringRoute.TEMPLATE_CONFIRMATION -> confirmation()
                AuthoringRoute.TEMPLATE_PREVIEW -> {
                    val ready = physical.state.result as? PhysicalPreviewResult.Ready
                    if (ready != null) PhysicalPreviewScreen(ready.preview, syntheticTarget = isSyntheticProfile(physical.state.profile), compact = true,
                        printingControls = {
                            Row {
                                TextButton(onClick = { navigate(AuthoringRoute.TEMPLATE_CONFIRMATION) }) { Text("Print") }
                                TextButton(onClick = { navigate(AuthoringRoute.TEMPLATE_SETTINGS) }) { Text("Preview settings") }
                                TextButton(onClick = {
                                    templateDocument?.let { document ->
                                        when (val logical = createTemplateLogicalPreview(document, textBackend)) {
                                            is TemplateLogicalResult.Ready -> { templateLogical = logical.preview; navigate(AuthoringRoute.TEMPLATE_LOGICAL_PREVIEW) }
                                            is TemplateLogicalResult.Failed -> templateStatus = logical.message
                                        }
                                    }
                                }) { Text("Open logical preview") }
                            }
                        }, backLabel = "Back to templates", onBack = goBack)
                    else LaunchedEffect(Unit) { navigate(AuthoringRoute.TEMPLATES) }
                }
                AuthoringRoute.IMAGE -> ImagePrintScreen(image.state, imagePlatform, imageStatus,
                    printState.isActive,
                    onEdit = { printing?.cancelAll(); cancelPreparation(); savedImageEdits = it; image.edit(it) },
                    onPreview = { navigate(AuthoringRoute.IMAGE_PREVIEW) },
                    onBack = { navigate(AuthoringRoute.HOME) }, preparing = preparationJob != null)
                AuthoringRoute.IMAGE_PREVIEW -> confirmation()
                AuthoringRoute.MARKDOWN -> MarkdownScreen(authoring,
                    onHome = { navigate(AuthoringRoute.HOME) }, onPreview = { navigate(AuthoringRoute.PREVIEW) },
                    onPhysicalPreview = { navigate(AuthoringRoute.PRINTER_SETUP) }, assetControls = markdownAssets)
                AuthoringRoute.PREVIEW -> {
                    val ready = authoring.state.result as? MarkdownAuthoringResult.Ready
                    if (ready != null) PreviewScreen(ready.preview) { navigate(AuthoringRoute.MARKDOWN) }
                    else MarkdownScreen(authoring, { navigate(AuthoringRoute.HOME) }, { navigate(AuthoringRoute.PREVIEW) },
                        { navigate(AuthoringRoute.PRINTER_SETUP) }, assetControls = markdownAssets)
                }
                AuthoringRoute.PRINTER_SETUP, AuthoringRoute.PHYSICAL_PREVIEW -> confirmation()
            }
        }
    }
}

@Composable
fun HomeScreen(onImage: (() -> Unit)? = null, onTemplates: (() -> Unit)? = null,
    onPrinters: (() -> Unit)? = null, onMarkdown: () -> Unit) {
    Column(Modifier.widthIn(max = 960.dp).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("RastrIO", style = MaterialTheme.typography.headlineMedium)
            Text("Create something to print.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeDestination("Open Markdown", "Write a document", onMarkdown)
            if (onImage != null) HomeDestination("Open Image Print", "Prepare a photo or graphic", onImage)
            if (onTemplates != null) HomeDestination("Open Templates", "Lists, notes, memos and QR messages", onTemplates)
        }
        HorizontalDivider()
        if (onPrinters != null) HomeDestination("Open Printers", "Connections, profiles and calibration", onPrinters)
    }
}

@Composable
private fun HomeDestination(title: String, description: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(description, style = MaterialTheme.typography.bodyMedium) },
        trailingContent = { Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).clickable(role = Role.Button, onClick = onClick).authoringFocus())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MarkdownScreen(authoring: ObservableAuthoring, onHome: () -> Unit, onPreview: () -> Unit,
    onPhysicalPreview: () -> Unit, assetControls: @Composable () -> Unit = {}) {
    val state = authoring.state
    Column(Modifier.padding(16.dp).widthIn(max = 960.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Markdown", style = MaterialTheme.typography.headlineMedium)
            AuthoringButton("Back to Home", onClick = onHome)
        }
        Text("Logical authoring canvas: 120 mm by default. Controlled bundled font coverage; unsupported content produces diagnostics.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = state.source, onValueChange = authoring::edit,
            label = { Text("Markdown source") }, placeholder = { Text("# Your document") },
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("markdown-source")
                .semantics { contentDescription = "Markdown source" },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = LocalAuthoringColors.current.editor,
                unfocusedContainerColor = LocalAuthoringColors.current.editor))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthoringButton("Compile", onClick = authoring::compile)
            AuthoringButton("Open logical preview", state.result is MarkdownAuthoringResult.Ready, onPreview)
            AuthoringButton("Printer preview", state.result.semanticDocument() != null, onPhysicalPreview)
        }
        assetControls()
        Diagnostics(state.result)
    }
}

private data class DiagnosticRow(val label: String, val error: Boolean, val message: String)

@Composable
private fun Diagnostics(result: MarkdownAuthoringResult) {
    val rows = remember(result) {
        fun markdown(values: List<MarkdownDiagnostic>) = values.map {
            val severity = if (it.severity == MarkdownSeverity.ERROR) "Error" else "Warning"
            DiagnosticRow("$severity · Markdown · ${it.code}${if (it.startOffset != null) " · normalized UTF-16 range ${it.startOffset}–${it.endOffset ?: it.startOffset}" else ""}",
                it.severity == MarkdownSeverity.ERROR, it.message)
        }
        fun layout(values: List<com.circuitnext.rastrio.core.layout.LayoutDiagnostic>, failure: Boolean) = values.map {
            DiagnosticRow("${if (failure) "Error" else "Warning"} · Layout · ${it.code}" +
                (it.sourceBlockIndex?.let { index -> " · block $index" } ?: ""), failure, it.message)
        }
        when (result) {
            MarkdownAuthoringResult.NotCompiled -> emptyList()
            is MarkdownAuthoringResult.CompilationFailed -> markdown(result.diagnostics)
            is MarkdownAuthoringResult.LayoutFailed -> markdown(result.markdownDiagnostics) + layout(result.diagnostics, true)
            is MarkdownAuthoringResult.Ready -> markdown(result.markdownDiagnostics) + layout(result.preview.diagnostics, false)
        }
    }
    val roles = LocalAuthoringColors.current
    Text(when (result) {
        MarkdownAuthoringResult.NotCompiled -> "Compile to update the preview"
        is MarkdownAuthoringResult.CompilationFailed -> "Markdown compilation failed"
        is MarkdownAuthoringResult.LayoutFailed -> "Layout failed"
        is MarkdownAuthoringResult.Ready -> "Ready for logical preview"
    }, color = when (result) {
        is MarkdownAuthoringResult.Ready -> roles.success
        is MarkdownAuthoringResult.CompilationFailed, is MarkdownAuthoringResult.LayoutFailed -> roles.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    })
    if (rows.isNotEmpty()) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
        items(rows) { row ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(row.label, color = if (row.error) roles.error else roles.warning,
                    style = MaterialTheme.typography.labelLarge)
                Text(row.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
