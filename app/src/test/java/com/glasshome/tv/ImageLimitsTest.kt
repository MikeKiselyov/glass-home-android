package com.glasshome.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageLimitsTest {

    @Test
    fun acceptsPhoneSizedPictures() {
        assertTrue(ImageLimits.isAcceptable(4032, 3024))
        assertTrue(ImageLimits.isAcceptable(10_000, 10_000))
    }

    @Test
    fun refusesPicturesOverTheLimit() {
        assertFalse(ImageLimits.isAcceptable(10_001, 10_000))
        assertFalse(ImageLimits.isAcceptable(40_000, 40_000))
        assertFalse(ImageLimits.isAcceptable(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test
    fun refusesMissingSizes() {
        assertFalse(ImageLimits.isAcceptable(0, 100))
        assertFalse(ImageLimits.isAcceptable(100, -1))
    }

    @Test
    fun sampleSizeKeepsTheShorterSideAboveTheTarget() {
        assertEquals(1, ImageLimits.sampleSize(500, 400, 312))
        assertEquals(2, ImageLimits.sampleSize(1000, 800, 312))
        assertEquals(8, ImageLimits.sampleSize(4032, 3024, 312))
        assertEquals(1, ImageLimits.sampleSize(100, 100, 312))
    }

    /** Sampling by the shorter side alone would decode this strip at 50000 x 500. */
    @Test
    fun longStripsAreSampledDownToTheDecodeLimit() {
        val sample = ImageLimits.sampleSize(100_000, 1000, 312)
        assertTrue(ImageLimits.decodedPixels(100_000, 1000, sample) <= ImageLimits.MAX_DECODED_PIXELS)
    }

    @Test
    fun everyAcceptedPictureDecodesUnderTheLimit() {
        val sizes = listOf(intArrayOf(10_000, 10_000), intArrayOf(4032, 3024), intArrayOf(1, 100_000_000), intArrayOf(9999, 1))
        for (size in sizes) {
            val sample = ImageLimits.sampleSize(size[0], size[1], 312)
            assertTrue(ImageLimits.decodedPixels(size[0], size[1], sample) <= ImageLimits.MAX_DECODED_PIXELS)
        }
    }
}
