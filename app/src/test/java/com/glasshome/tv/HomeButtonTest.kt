package com.glasshome.tv

import android.view.KeyEvent
import com.glasshome.tv.HomeButton.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeButtonTest {

    private val down = KeyEvent.ACTION_DOWN
    private val up = KeyEvent.ACTION_UP
    private val home = KeyEvent.KEYCODE_HOME

    @Test
    fun homeOpensGlassHomeOnThePress() {
        val button = HomeButton()
        assertEquals(Action.OPEN_HOME, button.decide(home, down, 0, HomeButton.CATCH))
    }

    @Test
    fun releasingHomeIsSwallowedSoGoogleTvNeverSeesIt() {
        val button = HomeButton()
        button.decide(home, down, 0, HomeButton.CATCH)
        assertEquals(Action.SWALLOW, button.decide(home, up, 0, HomeButton.CATCH))
    }

    @Test
    fun holdingHomeOpensOnlyOnce() {
        val button = HomeButton()
        button.decide(home, down, 0, HomeButton.CATCH)
        assertEquals(Action.SWALLOW, button.decide(home, down, 3, HomeButton.CATCH))
    }

    @Test
    fun otherKeysGoToTheTv() {
        val button = HomeButton()
        val keys = listOf(
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_SETTINGS,
            KeyEvent.KEYCODE_SEARCH,
        )
        for (key in keys) {
            assertEquals(Action.PASS, button.decide(key, down, 0, HomeButton.CATCH))
            assertEquals(Action.PASS, button.decide(key, up, 0, HomeButton.CATCH))
        }
    }

    /** A swallowed Home would leave the screensaver on screen: it only closes on a key it gets. */
    @Test
    fun duringTheScreensaverHomeGoesToTheTv() {
        val button = HomeButton()
        assertEquals(Action.PASS, button.decide(home, down, 0, HomeButton.CATCH, screenAsleep = true))
        assertEquals(Action.PASS, button.decide(home, up, 0, HomeButton.CATCH, screenAsleep = true))
    }

    /** The screensaver closes on the press: its release must still reach the TV, or the TV gets half a press. */
    @Test
    fun aPressThatWentToTheTvIsReleasedThereToo() {
        val button = HomeButton()
        assertEquals(Action.PASS, button.decide(home, down, 0, HomeButton.CATCH, screenAsleep = true))
        assertEquals(Action.PASS, button.decide(home, up, 0, HomeButton.CATCH, screenAsleep = false))

        // The next press, with the screen awake, is caught again.
        assertEquals(Action.OPEN_HOME, button.decide(home, down, 0, HomeButton.CATCH))
        assertEquals(Action.SWALLOW, button.decide(home, up, 0, HomeButton.CATCH))
    }

    @Test
    fun afterModeLetsHomeReachGoogleTv() {
        val button = HomeButton()
        assertEquals(Action.PASS, button.decide(home, down, 0, HomeButton.AFTER))
        assertEquals(Action.PASS, button.decide(home, up, 0, HomeButton.AFTER))
    }
}
