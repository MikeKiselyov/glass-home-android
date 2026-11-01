package com.glasshome.tv

import android.view.KeyEvent

/**
 * What the Home override does with a key from the remote. Kept out of the
 * accessibility service so it can be tested without Android.
 *
 * "catch" (the default): Home never reaches Google TV. Glass Home opens on
 * the key press itself, without Google TV's screen flashing first and
 * without Google TV starting up behind it. Holding Home does the same; the
 * Google TV panel that a long press opens is not shown.
 *
 * While the screensaver runs or the screen is off, Home is left to the TV:
 * the screensaver only closes on a key that reaches it, so a swallowed Home
 * would leave it on screen. The TV then opens Google TV and Glass Home comes
 * in front of it the "after" way.
 *
 * "after": Home goes to Google TV as usual and Glass Home comes in front as
 * soon as Google TV's screen opens (the older way, for a TV where catching
 * the key does not work).
 *
 * One press is decided once, when it goes down: holding and releasing it go
 * the same way, so the TV never gets half a press.
 */
class HomeButton {

    enum class Action {
        /** Not ours: the key goes on to the TV as usual. */
        PASS,

        /** Swallow it and do nothing (the rest of a caught press). */
        SWALLOW,

        /** Swallow it and open Glass Home. */
        OPEN_HOME,
    }

    /** The press in progress goes on to the TV. */
    private var passingPress = false

    fun decide(keyCode: Int, action: Int, repeatCount: Int, mode: String, screenAsleep: Boolean = false): Action {
        if (keyCode != KeyEvent.KEYCODE_HOME) {
            return Action.PASS
        }
        if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
            passingPress = mode != CATCH || screenAsleep
            if (passingPress) {
                return Action.PASS
            }
            // The first DOWN is the earliest moment, and a held key only repeats DOWN.
            return Action.OPEN_HOME
        }
        if (passingPress) {
            return Action.PASS
        }
        return Action.SWALLOW
    }

    companion object {
        const val CATCH = "catch"
        const val AFTER = "after"
        val MODES = listOf(CATCH, AFTER)
    }
}
