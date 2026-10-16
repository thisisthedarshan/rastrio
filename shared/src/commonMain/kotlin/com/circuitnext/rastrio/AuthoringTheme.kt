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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import rastrio.shared.generated.resources.Res
import rastrio.shared.generated.resources.noto_sans_regular
import rastrio.shared.generated.resources.noto_sans_bold

/** Only the additional semantic roles consumed by this workflow; VISUAL_SYSTEM.md is authority. */
@Immutable
internal data class AuthoringColors(
    val disabledSurface: Color, val disabledText: Color, val editor: Color,
    val success: Color, val warning: Color, val error: Color,
)
private val lightAuthoringColors = AuthoringColors(Color(0xFFF2F1ED), Color(0xFF646C70), Color(0xFFF7F7F5),
    Color(0xFF2F6B4B), Color(0xFF7A5700), Color(0xFFB13A3A))
internal val LocalAuthoringColors = staticCompositionLocalOf { lightAuthoringColors }

@Composable
internal fun AuthoringTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFFF2A08C), onPrimary = Color(0xFF3D160D),
        primaryContainer = Color(0xFF5F2D20), onPrimaryContainer = Color(0xFFFFE4DA),
        secondary = Color(0xFFE3AE66), onSecondary = Color(0xFF332109),
        secondaryContainer = Color(0xFF523A1D), onSecondaryContainer = Color(0xFFF5E2C5),
        background = Color(0xFF171916), onBackground = Color(0xFFF3F3EC),
        surface = Color(0xFF1F211D), onSurface = Color(0xFFF3F3EC),
        surfaceVariant = Color(0xFF282B25), onSurfaceVariant = Color(0xFFBFC2B8),
        outline = Color(0xFF7B8272), outlineVariant = Color(0xFF454A3F),
        error = Color(0xFFF28B82), onError = Color(0xFF511919),
        errorContainer = Color(0xFF5D2926), onErrorContainer = Color(0xFFFFE1DE),
    ) else lightColorScheme(
        primary = Color(0xFFB8442E), onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFBEDE7), onPrimaryContainer = Color(0xFF9E3825),
        secondary = Color(0xFF805B20), onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF3E2C7), onSecondaryContainer = Color(0xFF3A240C),
        background = Color(0xFFFFFFFF), onBackground = Color(0xFF252622),
        surface = Color(0xFFF7F7F5), onSurface = Color(0xFF252622),
        surfaceVariant = Color(0xFFF2F1ED), onSurfaceVariant = Color(0xFF686A63),
        outline = Color(0xFF898D82), outlineVariant = Color(0xFFE5E5DF),
        error = Color(0xFFB13A3A), onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF7DEDC), onErrorContainer = Color(0xFF511919),
    )
    val roles = if (dark) AuthoringColors(Color(0xFF2A2F32), Color(0xFF8D979B), Color(0xFF1C1E1A),
        Color(0xFF6EB88A), Color(0xFFE4B85F), Color(0xFFF28B82)) else lightAuthoringColors
    val font = FontFamily(Font(Res.font.noto_sans_regular, FontWeight.Normal), Font(Res.font.noto_sans_bold, FontWeight.Bold))
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = font),
        displayMedium = base.displayMedium.copy(fontFamily = font),
        displaySmall = base.displaySmall.copy(fontFamily = font),
        headlineLarge = base.headlineLarge.copy(fontFamily = font, fontSize = 32.sp, lineHeight = 40.sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = font, fontSize = 28.sp, lineHeight = 36.sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = font, fontSize = 24.sp, lineHeight = 32.sp),
        titleLarge = base.titleLarge.copy(fontFamily = font, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = base.titleMedium.copy(fontFamily = font),
        titleSmall = base.titleSmall.copy(fontFamily = font),
        bodyLarge = base.bodyLarge.copy(fontFamily = font),
        bodyMedium = base.bodyMedium.copy(fontFamily = font),
        bodySmall = base.bodySmall.copy(fontFamily = font),
        labelLarge = base.labelLarge.copy(fontFamily = font),
        labelMedium = base.labelMedium.copy(fontFamily = font),
        labelSmall = base.labelSmall.copy(fontFamily = font),
    )
    CompositionLocalProvider(LocalAuthoringColors provides roles) {
        MaterialTheme(colorScheme = colors, typography = typography,
            shapes = Shapes(RoundedCornerShape(6.dp), RoundedCornerShape(12.dp), RoundedCornerShape(16.dp),
                RoundedCornerShape(20.dp), RoundedCornerShape(24.dp)), content = content)
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
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).authoringFocus(),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(disabledContainerColor = roles.disabledSurface,
            disabledContentColor = roles.disabledText)) { Text(label) }
}
