package com.glasshome.tv

import android.content.Context
import android.os.PowerManager

/**
 * Before a link from the phone opens: ends the screensaver, or turns the
 * screen on, so the app doesn't open behind it. A wake lock that causes a
 * wake-up does both (Android leaves the screensaver when it wakes; checked on
 * the TV). Taken every time: on an awake screen it changes nothing, and
 * Glass Home can't always tell that the screensaver is on.
 */
class ScreenWaker(private val context: Context) {

    fun wake() {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager? ?: return
        // The only public way to wake the screen. The flags are deprecated for
        // keeping it on, not for waking it.
        @Suppress("DEPRECATION")
        val lock = power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "glasshome:link")
        lock.acquire(WAKE_MS)
    }

    companion object {
        private const val WAKE_MS = 3_000L
    }
}
