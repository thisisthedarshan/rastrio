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

package com.circuitnext.rastrio.core.markdown

import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.file.Files
import com.circuitnext.rastrio.core.document.*
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownFixtureTest {
    @Test fun committedMarkdownMatchesPortableGoldens() {
        MarkdownGoldens.cases.forEach { case ->
            val source = checkNotNull(javaClass.getResource("/markdown/${case.name}.md")).readText()
            assertEquals(case.source, source, case.name)
            assertEquals(MarkdownCompilation(case.document, case.diagnostics), MarkdownCompiler.compile(source), case.name)
        }
    }
    @Test fun compilationDoesNotConnectToReferencedServerOrReadReferencedFiles() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 100
            val file = Files.createTempFile("rastrio-markdown-private-", ".png")
            try {
                Files.writeString(file, "Private non-image content")
                val url = "http://127.0.0.1:${server.localPort}/private.png"
                val sources = listOf(url, file.toUri().toString(), "file:/nonexistent/rastrio-private.png", "../private.png")
                sources.forEach { uri ->
                    val result = MarkdownCompiler.compile("![private]($uri)")
                    assertEquals(ThermalDocument(blocks = listOf(Image(ExternalAssetReference(uri), Alignment.LEFT, AutoSizing, "private"))), result.document)
                    assertEquals(listOf("MD201"), result.diagnostics.map { it.code })
                }
                MarkdownCompiler.compile("[link]($url)\n\n<script src=\"$url\"></script>")
                assertFailsWith<SocketTimeoutException> { server.accept().close() }
                assertEquals("Private non-image content", Files.readString(file))
            } finally { Files.delete(file) }
        }
    }
    @Test fun actualRepositoryReadmeCompilesDeterministicallyWithoutPrinterConfiguration() {
        val source = checkNotNull(javaClass.getResource("/repository/README.md")).readText()
        val first = MarkdownCompiler.compile(source)
        val document = assertNotNull(first.document)
        assertEquals(first, MarkdownCompiler.compile(source))
        assertTrue(document.blocks.any { it is Heading })
        assertTrue(document.blocks.any { it is Table })
        assertEquals(document, TdArchive.load(TdArchive.save(document)).document)
        assertTrue(first.diagnostics.none { it.severity == MarkdownSeverity.ERROR })
    }
}
