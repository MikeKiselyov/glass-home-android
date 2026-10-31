package com.glasshome.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlowZoomTest {

    private val turnMs = 46_600L

    @Test
    fun startsAtFullSizeAndEndsAtTheZoom() {
        assertEquals(1f, SlowZoom.scaleAt(0, turnMs, 1.03f), 0.0001f)
        assertEquals(1.03f, SlowZoom.scaleAt(turnMs, turnMs, 1.03f), 0.0001f)
        assertEquals(1.03f, SlowZoom.scaleAt(turnMs + 5_000, turnMs, 1.03f), 0.0001f)
    }

    @Test
    fun growsEvenly() {
        assertEquals(1.015f, SlowZoom.scaleAt(turnMs / 2, turnMs, 1.03f), 0.0001f)
    }

    @Test
    fun oddInputsDoNotBreakIt() {
        assertEquals(1f, SlowZoom.scaleAt(-10, turnMs, 1.03f), 0.0001f)
        assertEquals(1.03f, SlowZoom.scaleAt(10, 0, 1.03f), 0.0001f)
    }

    /** The reason steps are fine: on a 1920-pixel screen each step moves well under a pixel. */
    @Test
    fun aStepMovesLessThanAPixel() {
        val shortestTurnMs = 15_000L + 1_600L
        assertTrue(SlowZoom.pixelsPerStep(shortestTurnMs, 1.03f, 1920) < 1f)
    }
}
