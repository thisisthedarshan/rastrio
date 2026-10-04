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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF315C73),
    onPrimary = Color.White,
    secondary = Color(0xFF8A5B22),
    onSecondary = Color.White,
    background = Color(0xFFF7F4EE),
    onBackground = Color(0xFF23282B),
    surface = Color(0xFFFBF9F4),
    onSurface = Color(0xFF23282B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF78AFC8),
    onPrimary = Color(0xFF10252F),
    secondary = Color(0xFFE3AE66),
    onSecondary = Color(0xFF332109),
    background = Color(0xFF141719),
    onBackground = Color(0xFFECE9E2),
    surface = Color(0xFF1A1E21),
    onSurface = Color(0xFFECE9E2),
)

@Composable
fun RastrioApp() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
        HomeScreen()
    }
}

@Composable
fun HomeScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Welcome to Rastrio", style = MaterialTheme.typography.headlineLarge)
            Text("Thermal document workspace", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
