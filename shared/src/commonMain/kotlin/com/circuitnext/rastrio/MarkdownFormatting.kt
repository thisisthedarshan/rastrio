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

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal enum class MarkdownFormat { HEADING, BOLD, LIST }

/** Source editing only. Geometry, interpretation and diagnostics remain owned by Core. */
internal fun formatMarkdown(value: TextFieldValue, format: MarkdownFormat): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val selected = value.text.substring(start, end)
    val replacement = when (format) {
        MarkdownFormat.BOLD -> "**${selected.ifEmpty { "text" }}**"
        MarkdownFormat.HEADING -> (if (start > 0 && value.text[start - 1] != '\n') "\n" else "") + "## ${selected.ifEmpty { "Heading" }}"
        MarkdownFormat.LIST -> (if (start > 0 && value.text[start - 1] != '\n') "\n" else "") + "- ${selected.ifEmpty { "List item" }}"
    }
    return TextFieldValue(value.text.replaceRange(start, end, replacement), TextRange(start, start + replacement.length))
}
