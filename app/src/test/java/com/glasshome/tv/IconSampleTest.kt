package com.glasshome.tv

import org.junit.Assert.assertEquals
import org.junit.Test

/** Icons are decoded no larger than the dock draws them, and never blurry. */
class IconSampleTest {

    @Test
    fun aStoredIconIsHalvedForA1080pScreen() {
        // 62 dp at density 2 is 124 px: 312 / 2 = 156 still covers it.
        assertEquals(2, IconStore.sampleFor(312, 124))
    }

    @Test
    fun itIsNotShrunkBelowWhatTheTileDraws() {
        // 62 dp at density 3 is 186 px: 156 would be blurry.
        assertEquals(1, IconStore.sampleFor(312, 186))
    }

    @Test
    fun aLargeUploadIsShrunkFurther() {
        assertEquals(8, IconStore.sampleFor(1024, 124))
    }

    @Test
    fun smallOrUnknownSizesAreLeftAlone() {
        assertEquals(1, IconStore.sampleFor(100, 124))
        assertEquals(1, IconStore.sampleFor(-1, 124))
        assertEquals(1, IconStore.sampleFor(312, 0))
    }
}
