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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import rastrio.shared.generated.resources.Res
import rastrio.shared.generated.resources.rastrio_symbol

private val launchMessages = listOf(
    "Teaching pixels how to dance…",
    "Putting tiny ideas on a roll…",
    "Warming up the paper magic…",
    "Lining up the little dots…",
    "Giving your pixels a paper route…",
    "Big ideas. Tiny paper. Let's go!",
    "Convincing dots to form an orderly queue…",
    "Rolling out the good stuff…",
    "Letting the tiny paper crew do their thing…",
)

/** A brief, skippable welcome shared by every platform entry point. */
@Composable
fun RastrIOLaunch(content: @Composable () -> Unit) {
    var welcomed by rememberSaveable { mutableStateOf(false) }
    val message = rememberSaveable { launchMessages.random() }
    val timeout = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
        1400, containsText = true, containsControls = true,
    ) ?: 1400
    if (welcomed) {
        content()
    } else {
        LaunchedEffect(timeout) { delay(timeout); welcomed = true }
        AuthoringTheme { LaunchScreen(message) { welcomed = true } }
    }
}

@Composable
internal fun LaunchScreen(message: String, onContinue: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding().padding(horizontal = 24.dp)) {
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().align(Alignment.Center)
                    .verticalScroll(rememberScrollState()).padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(painterResource(Res.drawable.rastrio_symbol), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(96.dp))
                Text("RastrIO", style = MaterialTheme.typography.displaySmall)
                Text("From pixels to paper.", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(Modifier.width(32.dp), thickness = 3.dp, color = MaterialTheme.colorScheme.primary)
                Text(message, modifier = Modifier.testTag("launch-message"),
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text("Made with ♥ · Free & Open Source :D", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                TextButton(onClick = onContinue, modifier = Modifier.heightIn(min = 48.dp).authoringFocus()) { Text("Continue") }
            }
        }
    }
}
