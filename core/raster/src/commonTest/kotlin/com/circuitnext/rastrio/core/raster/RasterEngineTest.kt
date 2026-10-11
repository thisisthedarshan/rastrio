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

package com.circuitnext.rastrio.core.raster

import kotlin.test.*

class RasterEngineTest {
    private fun gray(value: Int) = (0xff000000L or (value.toLong() * 0x010101)).toInt()
    private fun image(w: Int, h: Int, values: IntArray) = DecodedImage(w, h, values.map(::gray).toIntArray())
    private fun bytes(source: DecodedImage, options: RasterOptions, policy: RasterResourcePolicy = RasterResourcePolicy()): ByteArray {
        val bands = mutableListOf<Byte>()
        val result = RasterEngine.process(source, options, policy) { bands.addAll(it.copyBytes().toList()) }
        assertIs<RasterResult.Completed>(result)
        return bands.toByteArray()
    }
    private fun hex(value: ByteArray) = value.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private val gradient = image(9, 6, IntArray(54) { (it % 9 * 29 + it / 9 * 17) % 256 })

    @Test fun allAlgorithmsMatchCheckedInGoldens() {
        val goldens = mapOf(
            DitherAlgorithm.THRESHOLD to "f800f000f080e080e180c380",
            DitherAlgorithm.BAYER to "d000ea00d480e8805100a380",
            DitherAlgorithm.ATKINSON to "f000ec00e0809080d180c380",
            DitherAlgorithm.FLOYD_STEINBERG to "f400d200e880a480a980a380"
        )
        for ((algorithm, expected) in goldens) {
            for (height in listOf(1, 2, 4, 256)) {
                assertEquals(expected, hex(bytes(gradient, RasterOptions(9, 6, algorithm = algorithm, bandHeight = height))), "$algorithm/$height")
            }
        }
    }
    @Test fun packingAndThresholdBoundary() {
        assertEquals("c080", hex(bytes(image(9, 1, intArrayOf(0,127,128,129,255,255,255,255,0)), RasterOptions(9,1))))
        assertEquals("00", hex(bytes(image(1,1,intArrayOf(0)), RasterOptions(1,1,threshold=0))))
        assertEquals("80", hex(bytes(image(1,1,intArrayOf(254)), RasterOptions(1,1,threshold=255))))
    }
    @Test fun everyWidthHasZeroPaddingIncludingNarrowAndFullWidth() {
        val black = image(1,1,intArrayOf(0))
        for (width in listOf(1,2,7,8,9,15,16,17,383,384,576,32768)) {
            val packed = bytes(black,RasterOptions(width,1))
            assertEquals((width+7)/8,packed.size)
            for (x in 0 until width) assertTrue((packed[x/8].toInt() and (128 ushr (x%8))) != 0)
            if (width%8 != 0) assertEquals(0,packed.last().toInt() and ((1 shl (8-width%8))-1))
        }
    }
    @Test fun alphaCompositesOnWhiteAndStraightChannelsAreUsed() {
        val source=DecodedImage(4,1,intArrayOf(0,0x7f000000,0x80000000.toInt(),0xff000000.toInt()))
        assertEquals("30",hex(bytes(source,RasterOptions(4,1))))
    }
    @Test fun integerLuminanceUsesAllChannels() {
        val source=DecodedImage(3,1,intArrayOf(0xffff0000.toInt(),0xff00ff00.toInt(),0xff0000ff.toInt()))
        assertEquals("a0",hex(bytes(source,RasterOptions(3,1))))
    }
    @Test fun allExifOrientationsNormalizeBeforeCropping() {
        val source=image(2,3,intArrayOf(0,255,255,0,0,255))
        val expected=listOf("804080","408040","408040","804080","a040","a040","40a0","40a0")
        for (orientation in 1..8) {
            val width=if(orientation>=5)3 else 2
            val height=if(orientation>=5)2 else 3
            assertEquals(expected[orientation-1],hex(bytes(source,RasterOptions(width,height,orientation=orientation))), "EXIF $orientation")
        }
        assertEquals("80",hex(bytes(source,RasterOptions(1,1,orientation=6,crop=ImageCrop(2,0,1,1)))))
    }
    @Test fun orientationCoordinateGoldensCoverEveryCornerAndTranspose() {
        val source=image(3,2,intArrayOf(0,50,100,150,200,250))
        val expected=listOf(
            listOf(0,50,100,150,200,250),listOf(100,50,0,250,200,150),
            listOf(250,200,150,100,50,0),listOf(150,200,250,0,50,100),
            listOf(0,150,50,200,100,250),listOf(150,0,200,50,250,100),
            listOf(250,100,200,50,150,0),listOf(100,250,50,200,0,150),
        )
        for(orientation in 1..8) {
            val width=if(orientation>=5)2 else 3
            for((index,value) in expected[orientation-1].withIndex()) {
                val crop=ImageCrop(index%width,index/width,1,1)
                assertEquals("00",hex(bytes(source,RasterOptions(1,1,orientation=orientation,crop=crop,threshold=value))))
                assertEquals("80",hex(bytes(source,RasterOptions(1,1,orientation=orientation,crop=crop,threshold=value+1))))
            }
        }
    }
    @Test fun nearestNeighborUsesPixelCentersAndCropBounds() {
        val source=image(4,1,intArrayOf(0,255,0,255))
        assertEquals("00",hex(bytes(source,RasterOptions(2,1))))
        assertEquals("f0",hex(bytes(source,RasterOptions(8,1,crop=ImageCrop(0,0,2,1)))))
    }
    @Test fun brightnessContrastAndGammaHaveExplicitBoundaries() {
        val source=image(3,1,intArrayOf(64,128,192))
        assertEquals("00",hex(bytes(source,RasterOptions(3,1,brightness=255))))
        assertEquals("e0",hex(bytes(source,RasterOptions(3,1,brightness=-255))))
        assertEquals("00",hex(bytes(source,RasterOptions(3,1,contrastPercent=0))))
        assertEquals("c0",hex(bytes(source,RasterOptions(3,1,gammaHundredths=50))))
        assertEquals("00",hex(bytes(source,RasterOptions(3,1,gammaHundredths=200))))
    }
    @Test fun inputAndOutputAreDefensiveSnapshots() {
        val sourcePixels=intArrayOf(gray(0))
        val source=DecodedImage(1,1,sourcePixels)
        sourcePixels[0]=gray(255)
        RasterEngine.process(source,RasterOptions(1,1)) {
            val mutable=it.copyBytes(); mutable[0]=0
            assertEquals("80",hex(it.copyBytes()))
        }
    }
    @Test fun invalidAndOverflowingParametersFailBeforeOutput() {
        val source=image(1,1,intArrayOf(0))
        val invalid=listOf(
            RasterOptions(0,1),RasterOptions(Int.MAX_VALUE,Int.MAX_VALUE),RasterOptions(1,0),
            RasterOptions(1,1,orientation=0),RasterOptions(1,1,orientation=9),
            RasterOptions(1,1,brightness=Int.MAX_VALUE),RasterOptions(1,1,contrastPercent=-1),
            RasterOptions(1,1,gammaHundredths=0),RasterOptions(1,1,threshold=256),RasterOptions(1,1,bandHeight=0),
            RasterOptions(1,1,crop=ImageCrop(0,0,0,1)),RasterOptions(1,1,crop=ImageCrop(-1,0,1,1)),
            RasterOptions(1,1,crop=ImageCrop(Int.MAX_VALUE,0,Int.MAX_VALUE,1)),
        )
        for(options in invalid) assertIs<RasterResult.Invalid>(RasterEngine.process(source,options){fail("Must not emit")})
        assertFailsWith<IllegalArgumentException>{DecodedImage(Int.MAX_VALUE,Int.MAX_VALUE,intArrayOf())}
        assertFailsWith<IllegalArgumentException>{DecodedImage(1,1,intArrayOf())}
    }
    @Test fun exactAndOverResourceLimits() {
        val source=image(1,1,intArrayOf(0))
        assertEquals("8080",hex(bytes(source,RasterOptions(1,2),RasterResourcePolicy(maxOutputPixels=2,maxPayloadBytes=2,maxBandBytes=1,maxScratchBytes=4098))))
        for(policy in listOf(RasterResourcePolicy(maxOutputPixels=1),RasterResourcePolicy(maxPayloadBytes=1),RasterResourcePolicy(maxScratchBytes=4097))) {
            assertIs<RasterResult.Invalid>(RasterEngine.process(source,RasterOptions(1,2),policy){fail("Must reject")})
        }
        assertFailsWith<IllegalArgumentException>{RasterResourcePolicy(maxBandBytes=Int.MAX_VALUE)}
    }
    @Test fun longOutputIsEmittedInBoundedOrderedBands() {
        val source=image(1,1,intArrayOf(0))
        var row=0;var count=0
        val result=RasterEngine.process(source,RasterOptions(9,100000,algorithm=DitherAlgorithm.FLOYD_STEINBERG,bandHeight=71)) {
            assertEquals(row,it.startRow); assertTrue(it.height<=71)
            assertEquals(it.height*2,it.copyBytes().size)
            row+=it.height;count++
        }
        assertEquals(100000,row)
        assertEquals(RasterResult.Completed(count,200000),result)
    }
    @Test fun profileMinimumBandRowsKeepDiffusionContinuous() {
        for(algorithm in DitherAlgorithm.entries) {
            val heights=mutableListOf<Int>()
            val joined=mutableListOf<Byte>()
            val source=image(9,10,IntArray(90){(it*29)%256})
            val result=RasterEngine.process(source,RasterOptions(9,10,algorithm=algorithm,bandHeight=4,minBandHeight=3)) {
                heights.add(it.height);joined.addAll(it.copyBytes().toList())
            }
            assertIs<RasterResult.Completed>(result)
            assertEquals(listOf(4,3,3),heights)
            assertContentEquals(bytes(source,RasterOptions(9,10,algorithm=algorithm)),joined.toByteArray())
        }
        val source=image(1,1,intArrayOf(0))
        val balanced=mutableListOf<Int>()
        assertIs<RasterResult.Completed>(RasterEngine.process(source,RasterOptions(1,9,bandHeight=4,minBandHeight=3)){balanced.add(it.height)})
        assertEquals(listOf(3,3,3),balanced)
        assertIs<RasterResult.Invalid>(RasterEngine.process(source,RasterOptions(1,5,bandHeight=4,minBandHeight=3)){fail("Impossible bands")})
    }
    @Test fun cancellationBeforeAndBetweenBandsNeverEmitsAnotherBand() {
        val source=image(1,1,intArrayOf(0));var bands=0
        assertEquals(RasterResult.Cancelled,RasterEngine.process(source,RasterOptions(1,1),isCancelled={true}){fail("Cancelled")})
        assertEquals(RasterResult.Cancelled,RasterEngine.process(source,RasterOptions(1,100,bandHeight=3),isCancelled={bands==1}){bands++})
        assertEquals(1,bands)
    }
    @Test fun callbackFailuresPropagateWithoutReplay() {
        val source=image(1,1,intArrayOf(0));var bands=0
        assertFailsWith<IllegalStateException>{RasterEngine.process(source,RasterOptions(1,20,bandHeight=1)){bands++;error("sink")}}
        assertEquals(1,bands)
    }
}
