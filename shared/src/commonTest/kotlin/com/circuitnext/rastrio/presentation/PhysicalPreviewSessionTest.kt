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

package com.circuitnext.rastrio.presentation

import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import kotlin.test.*

class PhysicalPreviewSessionTest {
    private fun authoring(source: String): MarkdownAuthoringSession = createAuthoringSession().also {
        it.edit(source)
        it.compile()
    }
    private fun ready(session: PhysicalPreviewSession) = assertIs<PhysicalPreviewResult.Ready>(session.state.result)

    @Test fun unchangedInputsReuseTheExactPlanAndPreview() {
        var calls = 0
        val session = PhysicalPreviewSession(preparePrint = { document, profile, options ->
            calls++
            BasicPreparationEngine().prepare(document, profile, options)
        })
        val authoring = authoring("Receipt")
        session.prepare(authoring.state.result)
        val first = ready(session)
        session.prepare(authoring.state.result)
        assertEquals(1, calls)
        assertSame(first, session.state.result)
        assertSame(first.print, ready(session).print)
        assertSame(first.preview, ready(session).preview)
    }

    @Test fun profileChangeRepreparesSemanticInputAtTheNewWidth() {
        val authoring = authoring(List(20) { "item" }.joinToString(" "))
        val logical = assertIs<MarkdownAuthoringResult.Ready>(authoring.state.result)
        val session = PhysicalPreviewSession()
        session.prepare(logical)
        val narrow = ready(session)
        session.prepare(logical, profile = SyntheticReferenceProfiles.wide80mm)
        val wide = ready(session)
        assertEquals(384, narrow.preview.widthDots)
        assertEquals(576, wide.preview.widthDots)
        assertTrue(narrow.print.operations.filterIsInstance<NativeText>().size >
            wide.print.operations.filterIsInstance<NativeText>().size)
        assertNotSame(narrow.print, wide.print)
        assertEquals(120.0, logical.preview.widthMm)
        assertEquals(384, narrow.print.target.printableWidthDots)
    }

    @Test fun cutOptionsReplaceThePlanWithoutChangingOldSnapshots() {
        val input = authoring("Receipt").state.result
        val session = PhysicalPreviewSession()
        session.prepare(input, profile = SyntheticReferenceProfiles.wide80mm)
        val original = ready(session)
        session.prepare(input, options = PrintOptions(cutIntent = CutIntent.MANUAL_GUIDE))
        val manual = ready(session)
        assertTrue(manual.print.operations.any { it is ManualCutGuide })
        assertTrue(manual.print.operations.none { it is AutomaticCut })
        for (mode in CutMode.entries) {
            session.prepare(input, options = PrintOptions(cutIntent = CutIntent.AUTOMATIC, cutMode = mode))
            val automatic = ready(session)
            assertNotSame(manual.print, automatic.print)
            assertEquals(mode, automatic.print.operations.filterIsInstance<AutomaticCut>().single().mode)
            assertTrue(automatic.print.operations.none { it is ManualCutGuide })
        }
        assertTrue(original.print.operations.none { it is AutomaticCut || it is ManualCutGuide })
        assertTrue(manual.print.operations.any { it is ManualCutGuide })
    }

    @Test fun invalidationClearsOutputAndRepreparesEvenAnEquivalentInput() {
        var calls = 0
        val session = PhysicalPreviewSession(preparePrint = { document, profile, options ->
            calls++
            BasicPreparationEngine().prepare(document, profile, options)
        })
        val input = authoring("Receipt").state.result
        session.prepare(input)
        val old = ready(session)
        session.invalidate()
        assertSame(PhysicalPreviewResult.NotPrepared, session.state.result)
        session.prepare(input)
        assertEquals(2, calls)
        assertNotSame(old.print, ready(session).print)
        assertEquals("Receipt", old.print.operations.filterIsInstance<NativeText>().single().text)
    }

    @Test fun cp437UsesTheSemanticDocumentEvenAfterAsciiLogicalLayoutFails() {
        val authoring = authoring("Café")
        val input = assertIs<MarkdownAuthoringResult.LayoutFailed>(authoring.state.result)
        val session = PhysicalPreviewSession()
        session.prepare(input)
        val text = ready(session).print.operations.filterIsInstance<NativeText>().single()
        assertEquals("Café", text.text)
        assertEquals("cp437", text.codePage.id)
        assertEquals(listOf(67, 97, 102, 130), text.characterBytes)
        assertSame(input.document, input.semanticDocument())
        assertEquals("Café", authoring.state.source)
        assertSame(input, authoring.state.result)
    }

    @Test fun unsupportedContentReplacesSuccessAndCachesItsStructuredFailure() {
        var calls = 0
        val session = PhysicalPreviewSession(preparePrint = { document, profile, options ->
            calls++
            BasicPreparationEngine().prepare(document, profile, options)
        })
        session.prepare(authoring("Receipt").state.result)
        val authoring = authoring("世界")
        session.prepare(authoring.state.result)
        val failure = assertIs<PhysicalPreviewResult.PreparationFailed>(session.state.result)
        assertTrue(failure.diagnostics.isNotEmpty())
        session.prepare(authoring.state.result)
        assertSame(failure, session.state.result)
        assertEquals(2, calls)
        assertEquals("世界", authoring.state.source)
    }

    @Test fun absentSemanticInputClearsThePreviousPlanWithoutPreparing() {
        val session = PhysicalPreviewSession()
        session.prepare(authoring("Receipt").state.result)
        session.prepare(MarkdownAuthoringResult.NotCompiled)
        assertSame(PhysicalPreviewResult.NotPrepared, session.state.result)
        assertNull(MarkdownAuthoringResult.NotCompiled.semanticDocument())
    }

    @Test fun reentrantInvalidationCannotPublishAnObsoletePlan() {
        lateinit var session: PhysicalPreviewSession
        session = PhysicalPreviewSession(preparePrint = { document, profile, options ->
            session.invalidate()
            BasicPreparationEngine().prepare(document, profile, options)
        })
        session.prepare(authoring("Receipt").state.result)
        assertSame(PhysicalPreviewResult.NotPrepared, session.state.result)
    }
}
