package com.glasshome.tv

import android.app.Application
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The Home button on Google TV leads to Glass Home. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class HomeOverrideServiceTest {

    private fun startService(): HomeOverrideService {
        return Robolectric.setupService(HomeOverrideService::class.java)
    }

    private fun windowOpened(packageName: String): AccessibilityEvent {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = packageName
        return event
    }

    private fun key(action: Int, keyCode: Int, repeat: Int = 0): KeyEvent {
        return KeyEvent(0, 0, action, keyCode, repeat)
    }

    private fun assertOpenedGlassHome(started: Intent?) {
        assertEquals(MainActivity::class.java.name, started?.component?.className)
        assertTrue(started!!.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION != 0)
    }

    @Test
    fun homeKeyOpensGlassHomeAndNeverReachesGoogleTv() {
        val service = startService()
        assertTrue(service.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME)))
        assertOpenedGlassHome(shadowOf(service).nextStartedActivity)
        assertTrue(service.onKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME)))
        assertNull(shadowOf(service).nextStartedActivity)
    }

    /** Found on the TV: a swallowed Home left the screensaver on screen. */
    @Test
    fun duringTheScreensaverHomeGoesToTheTvAndItsReleaseToo() {
        val service = startService()
        service.sendBroadcast(Intent(Intent.ACTION_DREAMING_STARTED))
        shadowOf(service.mainLooper).idle()
        assertFalse(service.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME)))

        service.sendBroadcast(Intent(Intent.ACTION_DREAMING_STOPPED))
        shadowOf(service.mainLooper).idle()
        assertFalse(service.onKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME)))
        assertNull(shadowOf(service).nextStartedActivity)

        // Awake again: the next press is caught.
        assertTrue(service.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME)))
    }

    @Test
    fun otherKeysAreLeftToTheTv() {
        val service = startService()
        assertFalse(service.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)))
        assertFalse(service.onKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)))
        assertNull(shadowOf(service).nextStartedActivity)
    }

    @Test
    fun afterModeLetsHomeGoToGoogleTvFirst() {
        val service = startService()
        HomeSettings(service).homeButton = HomeButton.AFTER
        assertFalse(service.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME)))
        assertNull(shadowOf(service).nextStartedActivity)

        service.onAccessibilityEvent(windowOpened("com.google.android.apps.tv.launcherx"))
        assertOpenedGlassHome(shadowOf(service).nextStartedActivity)
    }

    @Test
    fun theStockHomeOpeningAnotherWayStillLeadsToGlassHome() {
        val service = startService()
        service.onAccessibilityEvent(windowOpened("com.google.android.apps.tv.launcherx"))
        assertOpenedGlassHome(shadowOf(service).nextStartedActivity)
    }

    @Test
    fun otherAppsOpeningAreLeftAlone() {
        val service = startService()
        service.onAccessibilityEvent(windowOpened("com.google.android.youtube.tv"))
        assertNull(shadowOf(service).nextStartedActivity)
    }
}
