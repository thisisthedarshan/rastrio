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

package com.circuitnext.rastrio.printing

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.io.IOException
import java.util.concurrent.CountDownLatch

class AndroidImageDecoderTest {
    @Test fun imageAcquisitionCountsActualBytesAtTheBoundary() {
        assertEquals(IMAGE_INPUT_LIMIT, readImageBytes(ByteArrayInputStream(ByteArray(IMAGE_INPUT_LIMIT))).size)
        assertFailsWith<RuntimeException> { readImageBytes(ByteArrayInputStream(ByteArray(IMAGE_INPUT_LIMIT + 1))) }
        assertFailsWith<RuntimeException> { readImageBytes(ByteArrayInputStream(byteArrayOf())) }
    }
    @Test fun imageAcquisitionRejectsNonProgressAndChecksCancellation() {
        assertFailsWith<RuntimeException> { readImageBytes(object : InputStream() {
            override fun read(): Int = 0
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = 0
        }) }
        var checks = 0
        assertFailsWith<IllegalStateException> {
            readImageBytes(ByteArrayInputStream(ByteArray(200_000))) { if (++checks == 2) error("cancelled") }
        }
        assertEquals(2, checks)
    }
    @Test fun dimensionsAndProductsAreRejectedBeforeFullDecode() {
        for ((width, height) in listOf(0 to 1, -1 to 10, 32769 to 1, 32768 to 32768, Int.MAX_VALUE to Int.MAX_VALUE))
            assertFailsWith<RuntimeException> { imageDecodeSample(width, height, 1) }
        assertFailsWith<RuntimeException> { imageDecodeSample(1, 1, IMAGE_INPUT_LIMIT + 1) }
        assertEquals(1, imageDecodeSample(100, 100, 1000))
    }
    @Test fun sampledDecodeAccountsForBitmapPixelArrayDefensiveCopyAndEncodedSnapshots() {
        for ((width, height) in listOf(4000 to 3000, 10000 to 10000, 32768 to 3000)) {
            val sample = imageDecodeSample(width, height, IMAGE_INPUT_LIMIT)
            assertTrue(sample and (sample - 1) == 0)
            val w = (width.toLong() + sample - 1) / sample
            val h = (height.toLong() + sample - 1) / sample
            assertTrue(w * h <= 16_000_000)
            assertTrue(w * h * 12 + IMAGE_INPUT_LIMIT.toLong() * 2 + IMAGE_DECODE_SCRATCH <= IMAGE_DECODE_BUDGET)
        }
    }
    @Test fun orientationIsRetainedForCoreNormalizationAndInvalidMetadataFails() {
        assertEquals(1, imageOrientation(null))
        for (orientation in 1..8) assertEquals(orientation, imageOrientation(orientation.toString()))
        for (invalid in listOf("0", "9", "garbage", "")) assertFailsWith<RuntimeException> { imageOrientation(invalid) }
    }
    private val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    private fun chunk(tag: String, payload: ByteArray = byteArrayOf()): ByteArray =
        byteArrayOf(0, 0, 0, payload.size.toByte()) + tag.toByteArray() + payload + ByteArray(4)
    @Test fun pngOrientationMetadataAndAnimationHaveExplicitHandling() {
        assertFalse(inspectPngImage(signature + chunk("IEND")))
        assertTrue(inspectPngImage(signature + chunk("eXIf") + chunk("IEND")))
        assertFailsWith<RuntimeException> { inspectPngImage(signature + chunk("acTL") + chunk("IEND")) }
        assertFailsWith<RuntimeException> { inspectPngImage(signature + chunk("IEND").copyOf(11)) }
        assertFailsWith<RuntimeException> { inspectPngImage(signature + byteArrayOf(127, -1, -1, -1) + chunk("IEND")) }
        assertFailsWith<RuntimeException> { inspectPngImage(byteArrayOf()) }
    }
    @Test fun cancellationClosesAStalledSelectedStream() = runBlocking {
        val started = CountDownLatch(1)
        val released = CountDownLatch(1)
        val stream = object : InputStream() {
            override fun read(): Int = error("Use bounded buffer")
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                started.countDown()
                released.await()
                throw IOException("closed")
            }
            override fun close() { released.countDown() }
        }
        val job = launch(Dispatchers.IO) { readImageStream { stream } }
        withTimeout(1000) {
            while (started.count > 0) yield()
            job.cancelAndJoin()
        }
        assertEquals(0L, released.count)
    }

}
