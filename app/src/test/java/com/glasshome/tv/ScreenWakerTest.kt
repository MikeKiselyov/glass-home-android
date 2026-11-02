package com.glasshome.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A link sent while the screensaver runs (or the screen is off) shows up on screen. */
class ScreenWakerTest {

    @Test
    fun theScreensaverIsEndedFirst() {
        assertTrue(ScreenWaker.needsWaking(interactive = true, dreaming = true))
    }

    @Test
    fun aScreenThatIsOffComesOn() {
        assertTrue(ScreenWaker.needsWaking(interactive = false, dreaming = false))
    }

    @Test
    fun anAwakeScreenIsLeftAlone() {
        assertFalse(ScreenWaker.needsWaking(interactive = true, dreaming = false))
    }
}
