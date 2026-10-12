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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job

private enum class AuthoringRoute { HOME, MARKDOWN, PREVIEW, PRINTER_SETUP, PHYSICAL_PREVIEW, IMAGE, IMAGE_PREVIEW, TEMPLATES, TEMPLATE_PREVIEW, TEMPLATE_LOGICAL_PREVIEW }

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
    printingSessions: PrinterSessionStore? = null, imagePlatform: ImagePlatform? = printingPlatform as? ImagePlatform) {
    val textBackend = remember { createPlatformRasterTextBackend() }
    val physical = remember(session) { ObservablePhysicalPreview(PhysicalPreviewSession(rasterTextBackend = textBackend)) }
    var templateForms by rememberTemplateFormsState()
    var templateDocument by remember { mutableStateOf<ThermalDocument?>(null) }
    var templateLogical by remember { mutableStateOf<LogicalPreview?>(null) }
    var templateStatus by remember { mutableStateOf("") }
    val authoring = remember(session) { ObservableAuthoring(session ?: createAuthoringSession(rasterTextBackend = textBackend), physical) }
    val image = remember { ObservableImagePrint(ImagePrintSession()) }
    val pickedImage = imagePlatform?.image?.collectAsState()?.value
    val imageStatus = imagePlatform?.imageStatus?.collectAsState()?.value.orEmpty()
    var resolutionStatus by remember { mutableStateOf("") }
    var route by rememberSaveable { mutableStateOf(AuthoringRoute.HOME) }
    val printing = remember(printingPlatform, printingSessions) { printingSessions ?: printingPlatform?.let { PrinterSessionStore(it::createTransport) } }
    var selectedLocator by remember(printingPlatform) { mutableStateOf<String?>(null) }
    val printers = printingPlatform?.printers?.collectAsState()?.value ?: emptyList()
    val importedProfile = printingPlatform?.profile?.collectAsState()?.value
    val platformStatus = printingPlatform?.status?.collectAsState()?.value.orEmpty()
    val foreground = printingPlatform?.foreground?.collectAsState()?.value ?: true
    val selected = printers.find { it.transportLocator == selectedLocator }
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
        val templateRoute = route == AuthoringRoute.TEMPLATES || route == AuthoringRoute.TEMPLATE_PREVIEW || route == AuthoringRoute.TEMPLATE_LOGICAL_PREVIEW
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
        route = destination
    }
    DisposableEffect(printing) { onDispose { printing?.cancelAll(); preparationJob?.cancel() } }
    LaunchedEffect(route, foreground) { printing?.cancelAll(); cancelPreparation() }
    LaunchedEffect(pickedImage) {
        if (image.state.input != pickedImage) {
            printing?.cancelAll(); cancelPreparation(); image.select(pickedImage); physical.invalidate()
            resolutionStatus = "Picking another image releases previous Markdown image bindings"
        }
    }
    LaunchedEffect(importedProfile) {
        if (!isSyntheticProfile(image.state.profile) && image.state.profile != importedProfile) {
            cancelPreparation(); image.invalidate()
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
                preparePhysical(physical.state.profile, physical.state.options)
            } else resolutionStatus = "Markdown image binding limit reached"
        }
    }
    val printingControls: @Composable () -> Unit = {
        if (printingPlatform != null) PrintingControls(
            printingPlatform, printers, importedProfile, platformStatus, foreground, selected, printSession, printState,
            currentResult as? PhysicalPreviewResult.Ready,
            currentProfile,
            onSelect = { instance ->
                printing?.cancelAll(); cancelPreparation()
                selectedLocator = instance.transportLocator
                printingPlatform.selectPrinter(instance)
                if (importedProfile != null) {
                    if (imageRoute) image.configure(importedProfile, PrintOptions())
                    else preparePhysical(importedProfile,
                        physical.state.options.copy(cutIntent = CutIntent.NONE))
                }
            },
            onPrepare = { if (importedProfile != null) {
                printing?.cancelAll()
                if (imageRoute) { image.configure(importedProfile); startPreparation { image.prepareAsync() } }
                else preparePhysical(importedProfile, physical.state.options)
            } },
            onPrint = {
                val prepared = (currentResult as? PhysicalPreviewResult.Ready)?.print
                val currentInput = image.state.input == pickedImage
                if (foreground && currentInput && canPrintPrepared(selected, importedProfile, prepared, currentProfile) && printSession != null) {
                    scope.launch { printSession.print(selected!!, prepared!!, importedProfile!!.writeGuidance()) }
                }
            },
            showSelection = route != AuthoringRoute.PHYSICAL_PREVIEW && route != AuthoringRoute.IMAGE_PREVIEW && route != AuthoringRoute.TEMPLATE_PREVIEW,
        )
    }
    AuthoringTheme {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.background) {
            when (route) {
                AuthoringRoute.HOME -> HomeScreen(onImage = { route = AuthoringRoute.IMAGE },
                    onTemplates = { enterAuthoring(AuthoringRoute.TEMPLATES) }) { enterAuthoring(AuthoringRoute.MARKDOWN) }
                AuthoringRoute.TEMPLATES -> TemplateScreen(templateForms,
                    onForms = { templateForms = it; templateDocument = null; templateLogical = null; templateStatus = ""; printing?.cancelAll(); cancelPreparation(); physical.invalidate() },
                    onPreview = { document ->
                        templateDocument = document
                        startPreparation { physical.prepareAsync(document, physical.state.profile, physical.state.options) }
                    },
                    onBack = { route = AuthoringRoute.HOME }, busy = printState.isActive || preparationJob != null,
                    targetName = physical.state.profile.display.name,
                    profileControls = {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AuthoringButton("Narrow preview", preparationJob == null && !printState.isActive) { preparePhysical(SyntheticReferenceProfiles.narrow, physical.state.options) }
                            AuthoringButton("Wide preview", preparationJob == null && !printState.isActive) { preparePhysical(SyntheticReferenceProfiles.wide80mm, physical.state.options) }
                            for (strategy in QrStrategy.entries) AuthoringButton("QR ${strategy.name.lowercase()}", preparationJob == null && !printState.isActive) {
                                preparePhysical(physical.state.profile, physical.state.options.copy(qrStrategy = strategy))
                            }
                            AuthoringButton("Open physical preview", physical.state.result is PhysicalPreviewResult.Ready && preparationJob == null) { route = AuthoringRoute.TEMPLATE_PREVIEW }
                        }
                    }, printingControls = printingControls,
                    previewStatus = when (val result = physical.state.result) {
                        is PhysicalPreviewResult.PreparationFailed -> result.diagnostics.joinToString("\n") { "${it.code}: ${it.message}" }
                        is PhysicalPreviewResult.PreviewFailed -> result.diagnostics.joinToString("\n") { "${it.code}: ${it.message}" }
                        is PhysicalPreviewResult.Ready -> "Physical preview ready. $templateStatus"
                        else -> templateStatus
                    }, onLogicalPreview = { document ->
                        when (val logical = createTemplateLogicalPreview(document, textBackend)) {
                            is TemplateLogicalResult.Ready -> { templateLogical = logical.preview; templateStatus = ""; route = AuthoringRoute.TEMPLATE_LOGICAL_PREVIEW }
                            is TemplateLogicalResult.Failed -> templateStatus = logical.message
                        }
                    })
                AuthoringRoute.TEMPLATE_LOGICAL_PREVIEW -> {
                    val preview = templateLogical
                    if (preview != null) PreviewScreen(preview, backLabel = "Back to templates") { route = AuthoringRoute.TEMPLATES }
                    else LaunchedEffect(Unit) { route = AuthoringRoute.TEMPLATES }
                }
                AuthoringRoute.TEMPLATE_PREVIEW -> {
                    val ready = physical.state.result as? PhysicalPreviewResult.Ready
                    if (ready != null) PhysicalPreviewScreen(ready.preview, syntheticTarget = isSyntheticProfile(physical.state.profile),
                        printingControls = printingControls, backLabel = "Back to templates") { route = AuthoringRoute.TEMPLATES }
                    else LaunchedEffect(Unit) { route = AuthoringRoute.TEMPLATES }
                }
                AuthoringRoute.IMAGE -> ImagePrintScreen(image.state, imagePlatform, imageStatus, importedProfile,
                    printState.isActive,
                    onEdit = { printing?.cancelAll(); cancelPreparation(); image.edit(it) },
                    onProfile = { printing?.cancelAll(); cancelPreparation(); image.configure(it, PrintOptions()) },
                    onPrepare = { startPreparation { image.prepareAsync() } },
                    onPreview = { route = AuthoringRoute.IMAGE_PREVIEW },
                    onBack = { route = AuthoringRoute.HOME }, printingControls = printingControls,
                    preparing = preparationJob != null)
                AuthoringRoute.IMAGE_PREVIEW -> {
                    val ready = image.state.result as? PhysicalPreviewResult.Ready
                    if (ready != null) PhysicalPreviewScreen(ready.preview,
                        syntheticTarget = isSyntheticProfile(image.state.profile), printingControls = printingControls,
                        backLabel = "Back to image settings") { route = AuthoringRoute.IMAGE }
                    else LaunchedEffect(Unit) { route = AuthoringRoute.IMAGE }
                }
                AuthoringRoute.MARKDOWN -> MarkdownScreen(authoring,
                    onHome = { route = AuthoringRoute.HOME }, onPreview = { route = AuthoringRoute.PREVIEW },
                    onPhysicalPreview = { physical.prepare(authoring.state.result); route = AuthoringRoute.PRINTER_SETUP })
                AuthoringRoute.PREVIEW -> {
                    val ready = authoring.state.result as? MarkdownAuthoringResult.Ready
                    if (ready != null) PreviewScreen(ready.preview) { route = AuthoringRoute.MARKDOWN }
                    else MarkdownScreen(authoring, { route = AuthoringRoute.HOME }, { route = AuthoringRoute.PREVIEW },
                        { physical.prepare(authoring.state.result); route = AuthoringRoute.PRINTER_SETUP })
                }
                AuthoringRoute.PRINTER_SETUP -> PrinterSetupScreen(authoring.state.result, physical,
                    onBack = { route = AuthoringRoute.MARKDOWN }, onPreview = { route = AuthoringRoute.PHYSICAL_PREVIEW },
                    printingControls = printingControls, printingBusy = printState.isActive || preparationJob != null,
                    imagePlatform = imagePlatform, pickedImage = pickedImage, resolutionStatus = resolutionStatus,
                    onResolve = resolveMarkdownImage, preparePhysical = preparePhysical)
                AuthoringRoute.PHYSICAL_PREVIEW -> {
                    val ready = physical.state.result as? PhysicalPreviewResult.Ready
                    if (ready != null) PhysicalPreviewScreen(ready.preview,
                        syntheticTarget = isSyntheticProfile(physical.state.profile), printingControls = printingControls) {
                        route = AuthoringRoute.PRINTER_SETUP
                    }
                    else PrinterSetupScreen(authoring.state.result, physical,
                        { route = AuthoringRoute.MARKDOWN }, { route = AuthoringRoute.PHYSICAL_PREVIEW }, printingControls,
                        printState.isActive || preparationJob != null, imagePlatform, pickedImage, resolutionStatus,
                        resolveMarkdownImage, preparePhysical)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onImage: (() -> Unit)? = null, onTemplates: (() -> Unit)? = null, onMarkdown: () -> Unit) {
    Column(Modifier.padding(24.dp).widthIn(max = 960.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("RastrIO", style = MaterialTheme.typography.headlineLarge)
        Text("Markdown document workspace", style = MaterialTheme.typography.titleLarge)
        Text("Write a document, compile it, and inspect its logical layout.")
        AuthoringButton("Open Markdown", onClick = onMarkdown)
        if (onImage != null) AuthoringButton("Open Image Print", onClick = onImage)
        if (onTemplates != null) AuthoringButton("Open Templates", onClick = onTemplates)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MarkdownScreen(authoring: ObservableAuthoring, onHome: () -> Unit, onPreview: () -> Unit,
    onPhysicalPreview: () -> Unit) {
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
        Diagnostics(state.result)
    }
}

private data class DiagnosticRow(val label: String, val error: Boolean, val message: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrinterSetupScreen(authoring: MarkdownAuthoringResult, physical: ObservablePhysicalPreview,
    onBack: () -> Unit, onPreview: () -> Unit, printingControls: @Composable () -> Unit = {}, printingBusy: Boolean = false,
    imagePlatform: ImagePlatform? = null, pickedImage: ImageInput? = null, resolutionStatus: String = "",
    onResolve: (AssetReference) -> Unit = {},
    preparePhysical: (PrinterProfile, PrintOptions) -> Unit = { profile, options -> physical.prepare(authoring, profile, options) }) {
    val state = physical.state
    val imageReferences = remember(authoring) { authoring.semanticDocument()?.let(::markdownImageReferences).orEmpty() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp).widthIn(max = 960.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Printer preview setup", style = MaterialTheme.typography.headlineMedium)
            AuthoringButton("Back to Markdown", onClick = onBack)
            Text(if (isSyntheticProfile(state.profile))
                "Synthetic test profiles only. These configurations are not verified commercial printers."
                else "Imported profile capabilities require physical verification on the selected printer.",
                color = LocalAuthoringColors.current.warning, style = MaterialTheme.typography.bodySmall)
        }
        item { printingControls() }
        if (imageReferences.isNotEmpty()) item {
            Text("Resolve Markdown images explicitly", style = MaterialTheme.typography.titleMedium)
            Text("No URL is fetched. Choose local pixels, then associate them with an image reference.",
                style = MaterialTheme.typography.bodySmall)
            AuthoringButton("Choose Markdown image", imagePlatform != null && !printingBusy,
                onClick = { imagePlatform?.importImage() })
            if (resolutionStatus.isNotEmpty()) Text(resolutionStatus, style = MaterialTheme.typography.bodySmall)
        }
        items(imageReferences) { reference ->
            val identity = when (reference) {
                is EmbeddedAssetReference -> reference.assetId
                is ExternalAssetReference -> reference.uri
            }
            AuthoringButton("Use selected image for $identity", pickedImage != null && !printingBusy,
                onClick = { onResolve(reference) })
        }
        item {
            Text("Selected profile: ${state.profile.display.name}", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((profile, label) in listOf(SyntheticReferenceProfiles.narrow to "Synthetic 384 dots",
                    SyntheticReferenceProfiles.wide80mm to "Synthetic 576 dots",
                    SyntheticReferenceProfiles.rasterOnly to "Synthetic raster only")) {
                    AuthoringButton(label, !printingBusy, onClick = { preparePhysical(profile, state.options) })
                }
            }
        }
        item {
            Text("Cut option", style = MaterialTheme.typography.titleMedium)
            Text(when (state.options.cutIntent) {
                CutIntent.NONE -> "No cut"
                CutIntent.MANUAL_GUIDE -> "Printed manual guide"
                CutIntent.AUTOMATIC -> "Automatic ${state.options.cutMode.name.lowercase()} cut · non-printing operation"
                CutIntent.AUTOMATIC_OR_MANUAL -> "Automatic cut or printed manual guide"
            }, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthoringButton("No cut", !printingBusy, onClick = { preparePhysical(state.profile, state.options.copy(cutIntent = CutIntent.NONE)) })
                AuthoringButton("Manual guide", !printingBusy, onClick = { preparePhysical(state.profile, state.options.copy(cutIntent = CutIntent.MANUAL_GUIDE)) })
                AuthoringButton("Automatic full", !printingBusy, onClick = { preparePhysical(state.profile,
                    state.options.copy(cutIntent = CutIntent.AUTOMATIC, cutMode = CutMode.FULL)) })
                AuthoringButton("Automatic partial", !printingBusy, onClick = { preparePhysical(state.profile,
                    state.options.copy(cutIntent = CutIntent.AUTOMATIC, cutMode = CutMode.PARTIAL)) })
            }
        }
        item {
            when (val result = state.result) {
                PhysicalPreviewResult.NotPrepared -> Text("Compile the source before preparing a physical preview")
                is PhysicalPreviewResult.Ready -> {
                    Text("Ready for physical preview", color = LocalAuthoringColors.current.success)
                    Text("Prepared width: ${result.preview.widthDots} dots · height: ${result.preview.heightDots} dots")
                }
                is PhysicalPreviewResult.PreparationFailed -> Text("Physical preparation failed", color = LocalAuthoringColors.current.error)
                is PhysicalPreviewResult.PreviewFailed -> Text("Physical preview failed", color = LocalAuthoringColors.current.error)
            }
            AuthoringButton("Open physical preview", state.result is PhysicalPreviewResult.Ready && !printingBusy, onPreview)
        }
        val rows = when (val result = state.result) {
            PhysicalPreviewResult.NotPrepared -> emptyList()
            is PhysicalPreviewResult.Ready -> result.preview.diagnostics.map { DiagnosticRow(it.code, false, it.message) }
            is PhysicalPreviewResult.PreparationFailed -> result.diagnostics.map { DiagnosticRow(it.code, true, it.message) }
            is PhysicalPreviewResult.PreviewFailed -> result.diagnostics.map { DiagnosticRow(it.code, true, it.message) }
        }
        items(rows) { row ->
            Column {
                Text(row.label, color = if (row.error) LocalAuthoringColors.current.error else LocalAuthoringColors.current.warning,
                    style = MaterialTheme.typography.labelLarge)
                Text(row.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

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
