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

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Only the additional semantic roles consumed by this workflow; VISUAL_SYSTEM.md is authority. */
@Immutable
internal data class AuthoringColors(
    val disabledSurface: Color, val disabledText: Color, val editor: Color,
    val success: Color, val warning: Color, val error: Color,
)
internal val LocalAuthoringColors = staticCompositionLocalOf {
    AuthoringColors(Color(0xFFF0ECE4), Color(0xFF646C70), Color(0xFFFBF9F4),
        Color(0xFF2F6B4B), Color(0xFF7A5700), Color(0xFFB13A3A))
}

@Composable
internal fun AuthoringTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF78AFC8), onPrimary = Color(0xFF10252F),
        primaryContainer = Color(0xFF234A5D), onPrimaryContainer = Color(0xFFD9EEF7),
        secondary = Color(0xFFE3AE66), onSecondary = Color(0xFF332109),
        secondaryContainer = Color(0xFF523A1D), onSecondaryContainer = Color(0xFFF5E2C5),
        background = Color(0xFF141719), onBackground = Color(0xFFECE9E2),
        surface = Color(0xFF1A1E21), onSurface = Color(0xFFECE9E2),
        surfaceVariant = Color(0xFF22272A), onSurfaceVariant = Color(0xFFB8C0C3),
        outline = Color(0xFF657177), outlineVariant = Color(0xFF3B4348),
        error = Color(0xFFF28B82), onError = Color(0xFF511919),
        errorContainer = Color(0xFF5D2926), onErrorContainer = Color(0xFFFFE1DE),
    ) else lightColorScheme(
        primary = Color(0xFF315C73), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD7E8F0), onPrimaryContainer = Color(0xFF163441),
        secondary = Color(0xFF8A5B22), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF3E2C7), onSecondaryContainer = Color(0xFF3A240C),
        background = Color(0xFFF7F4EE), onBackground = Color(0xFF23282B),
        surface = Color(0xFFFBF9F4), onSurface = Color(0xFF23282B),
        surfaceVariant = Color(0xFFF0ECE4), onSurfaceVariant = Color(0xFF5E666B),
        outline = Color(0xFF81898D), outlineVariant = Color(0xFFD6D0C6),
        error = Color(0xFFB13A3A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF7DEDC), onErrorContainer = Color(0xFF511919),
    )
    val roles = if (dark) AuthoringColors(Color(0xFF2A2F32), Color(0xFF8D979B), Color(0xFF171B1D),
        Color(0xFF6EB88A), Color(0xFFE4B85F), Color(0xFFF28B82)) else LocalAuthoringColors.current
    CompositionLocalProvider(LocalAuthoringColors provides roles) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

/** Keyboard focus is explicit, rather than depending on a platform's subtle button overlay. */
internal fun Modifier.authoringFocus(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val ring = MaterialTheme.colorScheme.primary
    onFocusChanged { focused = it.isFocused }.then(if (focused) Modifier.border(2.dp, ring) else Modifier)
}

@Composable
internal fun AuthoringButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val roles = LocalAuthoringColors.current
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.authoringFocus(),
        colors = ButtonDefaults.buttonColors(disabledContainerColor = roles.disabledSurface,
            disabledContentColor = roles.disabledText)) { Text(label) }
}
