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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import java.io.File
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class LaunchScreenTest {
    @Test fun messageStaysStableAndIntroCanBeSkipped() = runComposeUiTest {
        mainClock.autoAdvance = false
        val revision = mutableStateOf(0)
        setContent {
            Text("Revision ${revision.value}")
            RastrIOLaunch { Text("Workspace ready") }
        }
        val message = onNodeWithTag("launch-message").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        runOnIdle { revision.value++ }
        mainClock.advanceTimeByFrame()
        onNodeWithText(message).assertIsDisplayed()
        onNodeWithText("Continue").performClick()
        mainClock.advanceTimeByFrame()
        onNodeWithText("Workspace ready").assertIsDisplayed()
        mainClock.advanceTimeBy(2000)
        onNodeWithTag("launch-message").assertDoesNotExist()
    }

    @Test fun introAutomaticallyOpensWorkspace() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { RastrIOLaunch { Text("Workspace ready") } }
        onNodeWithTag("launch-message").assertIsDisplayed()
        onNodeWithText("Workspace ready").assertDoesNotExist()
        mainClock.advanceTimeBy(1600)
        waitForIdle()
        onNodeWithText("Workspace ready").assertIsDisplayed()
    }

    @Test fun brandingAndContinueFitPhonesAndDesktopInBothThemes() {
        for (dark in listOf(false, true)) for ((width, height, fontScale) in listOf(
            Triple(480, 320, 1f), Triple(320, 640, 2f), Triple(390, 740, 1f), Triple(1280, 800, 1f),
        )) runComposeUiTest {
            setContent { AuthoringTheme(dark) {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                    Box(Modifier.requiredSize(width.dp, height.dp)) {
                        LaunchScreen("Teaching pixels how to dance…") {}
                    }
                }
            } }
            onNodeWithText("Teaching pixels how to dance…").performScrollTo().assertIsDisplayed()
            onNodeWithText("Continue").performScrollTo().assertIsDisplayed()
            onNodeWithText("RastrIO").assertExists()
            if (System.getenv("RASTRIO_UI_CAPTURE") == "1") {
                val data = assertNotNull(Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData())
                File("/tmp/rastrio-launch-$dark-$width.png").writeBytes(data.bytes)
            }
        }
    }
}
