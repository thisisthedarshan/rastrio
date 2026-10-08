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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.circuitnext.rastrio.core.markdown.MarkdownDiagnostic
import com.circuitnext.rastrio.core.markdown.MarkdownSeverity
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.profile.PrinterProfile
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import com.circuitnext.rastrio.presentation.*

private enum class AuthoringRoute { HOME, MARKDOWN, PREVIEW, PRINTER_SETUP, PHYSICAL_PREVIEW }

@Stable
internal class ObservablePhysicalPreview(private val session: PhysicalPreviewSession) {
    var state by mutableStateOf(session.state)
        private set
    fun invalidate() { session.invalidate(); state = session.state }
    fun prepare(authoring: MarkdownAuthoringResult, profile: PrinterProfile = state.profile,
        options: PrintOptions = state.options) {
        session.prepare(authoring, profile, options); state = session.state
    }
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
fun RastrIOApp(session: MarkdownAuthoringSession? = null) {
    val physical = remember(session) { ObservablePhysicalPreview(PhysicalPreviewSession()) }
    val authoring = remember(session) { ObservableAuthoring(session ?: createAuthoringSession(), physical) }
    var route by remember { mutableStateOf(AuthoringRoute.HOME) }
    AuthoringTheme {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.background) {
            when (route) {
                AuthoringRoute.HOME -> HomeScreen { route = AuthoringRoute.MARKDOWN }
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
                    onBack = { route = AuthoringRoute.MARKDOWN }, onPreview = { route = AuthoringRoute.PHYSICAL_PREVIEW })
                AuthoringRoute.PHYSICAL_PREVIEW -> {
                    val ready = physical.state.result as? PhysicalPreviewResult.Ready
                    if (ready != null) PhysicalPreviewScreen(ready.preview) { route = AuthoringRoute.PRINTER_SETUP }
                    else PrinterSetupScreen(authoring.state.result, physical,
                        { route = AuthoringRoute.MARKDOWN }, { route = AuthoringRoute.PHYSICAL_PREVIEW })
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onMarkdown: () -> Unit) {
    Column(Modifier.padding(24.dp).widthIn(max = 960.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("RastrIO", style = MaterialTheme.typography.headlineLarge)
        Text("Markdown document workspace", style = MaterialTheme.typography.titleLarge)
        Text("Write a document, compile it, and inspect its logical layout.")
        AuthoringButton("Open Markdown", onClick = onMarkdown)
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
        Text("Logical authoring canvas: 120 mm by default. Text coverage: printable ASCII.",
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
    onBack: () -> Unit, onPreview: () -> Unit) {
    val state = physical.state
    LazyColumn(Modifier.fillMaxSize().padding(16.dp).widthIn(max = 960.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Printer preview setup", style = MaterialTheme.typography.headlineMedium)
            AuthoringButton("Back to Markdown", onClick = onBack)
            Text("Synthetic test profiles only. These configurations are not verified commercial printers.",
                color = LocalAuthoringColors.current.warning, style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text("Selected profile: ${state.profile.display.name}", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((profile, label) in listOf(SyntheticReferenceProfiles.narrow to "Synthetic 384 dots",
                    SyntheticReferenceProfiles.wide80mm to "Synthetic 576 dots",
                    SyntheticReferenceProfiles.rasterOnly to "Synthetic raster only")) {
                    AuthoringButton(label, onClick = { physical.prepare(authoring, profile = profile) })
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
                AuthoringButton("No cut", onClick = { physical.prepare(authoring, options = state.options.copy(cutIntent = CutIntent.NONE)) })
                AuthoringButton("Manual guide", onClick = { physical.prepare(authoring, options = state.options.copy(cutIntent = CutIntent.MANUAL_GUIDE)) })
                AuthoringButton("Automatic full", onClick = { physical.prepare(authoring,
                    options = state.options.copy(cutIntent = CutIntent.AUTOMATIC, cutMode = CutMode.FULL)) })
                AuthoringButton("Automatic partial", onClick = { physical.prepare(authoring,
                    options = state.options.copy(cutIntent = CutIntent.AUTOMATIC, cutMode = CutMode.PARTIAL)) })
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
            AuthoringButton("Open physical preview", state.result is PhysicalPreviewResult.Ready, onPreview)
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
