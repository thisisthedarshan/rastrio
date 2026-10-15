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

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import com.circuitnext.rastrio.printing.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ProfileEditorRemediationUiTest {
    @Test fun basicConfigurationHidesProtocolIdentifiersAndEmptyDraftSavesNormally() = runComposeUiTest {
        var draft by mutableStateOf(ProfileDraft.fresh("user.editor"))
        var saves = 0
        setContent { ProfileEditorScreen(draft, { draft = it }, { saves++ }, {}, {}, {}, "") }
        onNodeWithTag("profile-field-display.name").assertExists()
        onNodeWithTag("profile-field-profileId").assertDoesNotExist()
        onNodeWithTag("profile-field-protocol.dialectId").assertDoesNotExist()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Save local draft"))
        onNodeWithText("Save local draft").performClick()
        runOnIdle { assertEquals(1, saves); assertEquals("", draft["geometry.horizontalDpi"]) }
    }

    @Test fun validationSummaryKeepsCodesInTechnicalDetailsAndErrorsInline() = runComposeUiTest {
        val draft = ProfileDraft.fresh("user.invalid")
        setContent { ProfileEditorScreen(draft, {}, {}, {}, {}, {}, "") }
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Validate profile"))
        onNodeWithText("Validate profile").performClick()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Profile needs more information before activation"))
        onNodeWithText("Profile needs more information before activation").assertExists()
        onAllNodes(hasText("PRF151", substring = true)).assertCountEquals(0)
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Save and select validated profile"))
        onNodeWithText("Save and select validated profile").assertIsNotEnabled()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Technical Details"))
        onNodeWithText("Technical Details").performClick()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("PRF151 $.geometry.horizontalDpi:", substring = true))
        onNode(hasText("PRF151 $.geometry.horizontalDpi:", substring = true)).assertExists()
    }

    @Test fun conflictingSuggestionRequiresExplicitChoiceAndRetainsProvenance() = runComposeUiTest {
        val device = PairedPrinterDevice("test-device", "Paired printer", BluetoothDeviceKind.CLASSIC)
        val proposal = ProfileSuggestion("display.name", "Reported name", InspectionEvidence.DEVICE_REPORTED, "Paired metadata", device.locator)
        var draft by mutableStateOf(ProfileDraft.fresh("user.proposal").withField("display.name", "My printer"))
        setContent { ProfileEditorScreen(draft, { draft = it }, {}, {}, {}, {}, "", device, listOf(proposal)) }
        runOnIdle { assertEquals("My printer", draft["display.name"]) }
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Your value: My printer. Choose whether to replace it."))
        onNodeWithText("Your value: My printer. Choose whether to replace it.").assertExists()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Use suggested Name"))
        onNodeWithText("Use suggested Name").performClick()
        runOnIdle {
            assertEquals("Reported name", draft["display.name"])
            assertEquals(proposal, draft.suggestions.single())
            assertTrue(draft.verifications.isEmpty())
        }
    }

    @Test fun advancedConfigurationUsesOneOpenAreaAndKeepsGeometryEvidenceAvailable() = runComposeUiTest {
        val draft = ProfileDraft.fresh("user.advanced")
        setContent { ProfileEditorScreen(draft, {}, {}, {}, {}, {}, "") }
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Advanced configuration"))
        onNodeWithText("Advanced configuration").performClick()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Print command protocol"))
        onNodeWithText("Print command protocol").performClick()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasTestTag("profile-field-protocol.dialectId"))
        onNodeWithTag("profile-field-protocol.dialectId").assertExists()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Print geometry"))
        onNodeWithText("Print geometry").performClick()
        onNodeWithTag("profile-field-protocol.dialectId").assertDoesNotExist()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Procedure performed"))
        onNodeWithText("Procedure performed").assertExists()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Record verification"))
        onNodeWithText("Record verification").assertIsNotEnabled()
    }
    @Test fun storageFailureKeepsTechnicalCodeOutOfPrimaryStatus() = runComposeUiTest {
        val draft = ProfileDraft.fresh("user.unsaved")
        setContent { ProfileEditorScreen(draft, {}, {}, {}, {}, {}, "APP144 Draft could not be saved") }
        onNodeWithText("Draft could not be saved").assertExists()
        onAllNodes(hasText("APP144", substring = true)).assertCountEquals(0)
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Technical Details"))
        onNodeWithText("Technical Details").performClick()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("APP144 Draft could not be saved"))
        onNodeWithText("APP144 Draft could not be saved").assertExists()
    }

    @Test fun reportedCharsetDropdownPreservesCustomValueAndDoesNotInventCommands() = runComposeUiTest {
        var draft by mutableStateOf(ProfileDraft.fresh("user.choices").withField("worksheet.charset", "Vendor charset"))
        setContent { ProfileEditorScreen(draft, { draft = it }, {}, {}, {}, {}, "") }
        onNodeWithTag("profile-editor-list").performScrollToNode(hasTestTag("profile-choice-worksheet.charset"))
        onNodeWithTag("profile-choice-worksheet.charset").performClick()
        onNodeWithText("CP437", useUnmergedTree = true).performClick()
        runOnIdle {
            assertEquals("CP437", draft["worksheet.charset"])
            assertEquals("", draft["nativeText.codePages[0].selector.parameter"])
            assertTrue(draft.verifications.isEmpty())
        }
        onNodeWithTag("profile-field-worksheet.charset").performTextReplacement("Custom report")
        runOnIdle { assertEquals("Custom report", draft["worksheet.charset"]) }
    }

    @Test fun registeredStrategyDropdownChangesOnlySelectedField() = runComposeUiTest {
        var draft by mutableStateOf(ProfileDraft.fresh("user.strategy"))
        setContent { ProfileEditorScreen(draft, { draft = it }, {}, {}, {}, {}, "") }
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Advanced configuration"))
        onNodeWithText("Advanced configuration").performClick()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Print command protocol"))
        onNodeWithText("Print command protocol").performClick()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasTestTag("profile-choice-protocol.initializationStrategy"))
        onNodeWithTag("profile-choice-protocol.initializationStrategy").performClick()
        onNodeWithText("escpos.init.standard", useUnmergedTree = true).performClick()
        runOnIdle {
            assertEquals("escpos.init.standard", draft["protocol.initializationStrategy"])
            assertEquals("", draft["geometry.horizontalDpi"])
            assertTrue(draft.verifications.isEmpty())
        }
    }

    @Test fun capabilityListChoicesRetainOtherEntriesAndRequireExplicitSelection() = runComposeUiTest {
        var draft by mutableStateOf(ProfileDraft.fresh("user.multiple").withField("nativeQr.errorCorrection", "L,M"))
        setContent { ProfileEditorScreen(draft, { draft = it }, {}, {}, {}, {}, "") }
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Advanced configuration"))
        onNodeWithText("Advanced configuration").performClick()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Native QR", substring = false))
        onNodeWithText("Native QR", substring = false).performClick()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasTestTag("profile-choice-nativeQr.errorCorrection"))
        onNodeWithTag("profile-choice-nativeQr.errorCorrection").performClick()
        onNodeWithText("Q", useUnmergedTree = true).performClick()
        runOnIdle {
            assertEquals("L,M,Q", draft["nativeQr.errorCorrection"])
            assertEquals("", draft["nativeQr.supported"])
            assertTrue(draft.verifications.isEmpty())
        }
    }

    @Test fun reportedFontChoiceKeepsDotGeometryUnknownAndCustomValuesEditable() = runComposeUiTest {
        var draft by mutableStateOf(ProfileDraft.fresh("user.font").withField("worksheet.nativeFont", "Vendor font"))
        setContent { ProfileEditorScreen(draft, { draft = it }, {}, {}, {}, {}, "") }
        onNodeWithTag("profile-editor-list").performScrollToNode(hasTestTag("profile-choice-worksheet.nativeFont"))
        runOnIdle { assertEquals("Vendor font", draft["worksheet.nativeFont"]) }
        onNodeWithTag("profile-choice-worksheet.nativeFont").performClick()
        onNodeWithText("12×24", useUnmergedTree = true).performClick()
        runOnIdle {
            assertEquals("12×24", draft["worksheet.nativeFont"])
            assertEquals("", draft["geometry.printableWidthDots"])
            assertEquals("", draft["nativeText.fonts[0].cellWidthDots"])
            assertTrue(draft.verifications.isEmpty())
        }
        onNodeWithTag("profile-field-worksheet.nativeFont").performTextReplacement("Custom font")
        runOnIdle { assertEquals("Custom font", draft["worksheet.nativeFont"]) }
    }

}
