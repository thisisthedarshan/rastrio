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
import com.circuitnext.rastrio.presentation.*

private enum class AuthoringRoute { HOME, MARKDOWN, PREVIEW }

/** Observation only. The session remains the sole owner of edits, outcomes and invalidation. */
@Stable
internal class ObservableAuthoring(private val session: MarkdownAuthoringSession) {
    var state by mutableStateOf(session.state)
        private set
    fun edit(source: String) { session.edit(source); state = session.state }
    fun compile() { session.compile(); state = session.state }
}

@Composable
fun RastrIOApp(session: MarkdownAuthoringSession? = null) {
    val authoring = remember(session) { ObservableAuthoring(session ?: createAuthoringSession()) }
    var route by remember { mutableStateOf(AuthoringRoute.HOME) }
    AuthoringTheme {
        Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.background) {
            when (route) {
                AuthoringRoute.HOME -> HomeScreen { route = AuthoringRoute.MARKDOWN }
                AuthoringRoute.MARKDOWN -> MarkdownScreen(authoring,
                    onHome = { route = AuthoringRoute.HOME }, onPreview = { route = AuthoringRoute.PREVIEW })
                AuthoringRoute.PREVIEW -> {
                    val ready = authoring.state.result as? MarkdownAuthoringResult.Ready
                    if (ready != null) PreviewScreen(ready.preview) { route = AuthoringRoute.MARKDOWN }
                    else MarkdownScreen(authoring, { route = AuthoringRoute.HOME }, { route = AuthoringRoute.PREVIEW })
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
internal fun MarkdownScreen(authoring: ObservableAuthoring, onHome: () -> Unit, onPreview: () -> Unit) {
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
        }
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
