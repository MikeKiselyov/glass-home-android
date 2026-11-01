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

    /** Longer photo turns step less often: 45 s every 0.8 s, ten minutes every 2 s. */
    @Test
    fun longTurnsStepLessOften() {
        assertEquals(809L, SlowZoom.stepMs(turnMs, 1.03f, 1920))
        assertEquals(SlowZoom.LONGEST_STEP_MS, SlowZoom.stepMs(600_000L + 1_600L, 1.03f, 1920))
        assertEquals(SlowZoom.STEP_MS, SlowZoom.stepMs(5_000L, 1.03f, 1920))
    }

    /** Whatever the step, the edge never jumps more than half a pixel. */
    @Test
    fun noStepMovesMoreThanHalfAPixelWhereItCanBeAvoided() {
        for (seconds in listOf(30L, 45L, 60L, 120L, 300L)) {
            val turn = seconds * 1000 + 1_600L
            assertTrue(SlowZoom.pixelsPerStep(turn, 1.03f, 1920) <= 0.5001f)
        }
    }

    @Test
    fun noZoomStepsRarely() {
        assertEquals(SlowZoom.LONGEST_STEP_MS, SlowZoom.stepMs(turnMs, 1f, 1920))
        assertEquals(SlowZoom.LONGEST_STEP_MS, SlowZoom.stepMs(0, 1.03f, 1920))
    }
}
