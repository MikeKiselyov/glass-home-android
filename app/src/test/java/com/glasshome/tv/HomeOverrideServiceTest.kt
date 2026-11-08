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

    private fun windowOpened(packageName: String, className: String? = null): AccessibilityEvent {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        event.packageName = packageName
        event.className = className
        return event
    }

    private fun googleTvHomeOpened(): AccessibilityEvent {
        return windowOpened(GOOGLE_TV, "$GOOGLE_TV.home.HomeActivity")
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

        service.onAccessibilityEvent(googleTvHomeOpened())
        assertOpenedGlassHome(shadowOf(service).nextStartedActivity)
    }

    /** Voice search results stay; Home or Back from them still leads to Glass Home at once. */
    @Test
    fun leavingVoiceSearchResultsLeadsToGlassHome() {
        val service = startService()
        HomeSettings(service).homeButton = HomeButton.AFTER
        for (leavingKey in listOf(KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK)) {
            service.onAccessibilityEvent(windowOpened(GOOGLE_TV, "com.google.apps.tiktok.nav.gateway.GatewayActivity"))
            service.onAccessibilityEvent(googleTvHomeOpened())
            assertNull(shadowOf(service).nextStartedActivity)

            assertFalse(service.onKeyEvent(key(KeyEvent.ACTION_DOWN, leavingKey)))
            service.onAccessibilityEvent(googleTvHomeOpened())
            assertOpenedGlassHome(shadowOf(service).nextStartedActivity)
        }
    }

    @Test
    fun theStockHomeOpeningAnotherWayStillLeadsToGlassHome() {
        val service = startService()
        service.onAccessibilityEvent(googleTvHomeOpened())
        assertOpenedGlassHome(shadowOf(service).nextStartedActivity)
    }

    /** Found on the TV: Google TV's home coming back is announced as a FrameLayout. */
    @Test
    fun googleTvHomeAsAPlainWindowLeadsToGlassHome() {
        val service = startService()
        service.onAccessibilityEvent(windowOpened(GOOGLE_TV, "android.widget.FrameLayout"))
        assertOpenedGlassHome(shadowOf(service).nextStartedActivity)
    }

    /** Found on the TV: Glass Home closed the panel the remote's Settings key opens. */
    @Test
    fun googleTvSettingsPanelStaysOnScreen() {
        val service = startService()
        service.onAccessibilityEvent(windowOpened(GOOGLE_TV, "$GOOGLE_TV.dashboard.DashboardActivity"))
        assertNull(shadowOf(service).nextStartedActivity)
    }

    @Test
    fun otherAppsOpeningAreLeftAlone() {
        val service = startService()
        service.onAccessibilityEvent(windowOpened("com.google.android.youtube.tv"))
        assertNull(shadowOf(service).nextStartedActivity)
    }
    /**
     * Found on the TV: after "Install", Android showed Google TV's home while
     * Glass Home was being replaced, before this service was connected again.
     */
    @Test
    fun afterAnUpdateAskedFromTheHomeScreenGlassHomeComesBack() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        context.getSharedPreferences("updates", android.content.Context.MODE_PRIVATE)
            .edit().putLong("reopen_home_at", System.currentTimeMillis()).commit()
        val service = startService()
        service.onServiceConnected()
        assertOpenedGlassHome(shadowOf(service).nextStartedActivity)
    }

    @Test
    fun connectingWithoutAnUpdateLeavesTheScreenAlone() {
        val service = startService()
        service.onServiceConnected()
        assertNull(shadowOf(service).nextStartedActivity)
    }
    /**
     * Found on the TV: a notification Google TV's launcher drew over Glass Home
     * made the service open Glass Home again, which closed the update question
     * (and counted it as Later for a day).
     */
    @Test
    fun aLauncherWindowOverGlassHomeLeavesItAlone() {
        val service = startService()
        HomeScreenState.inFront = true
        try {
            service.onAccessibilityEvent(googleTvHomeOpened())
            assertNull(shadowOf(service).nextStartedActivity)
        } finally {
            HomeScreenState.inFront = false
        }
        service.onAccessibilityEvent(googleTvHomeOpened())
        assertOpenedGlassHome(shadowOf(service).nextStartedActivity)
    }

    /** The sleep timer turns the screen off through the service's global action. */
    @Test
    fun sleepTurnsTheScreenOff() {
        val service = startService()
        service.onServiceConnected()
        assertTrue(HomeOverrideService.sleepTv())
        val actions = shadowOf(service).globalActionsPerformed
        assertEquals(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, actions.last())
    }

    @Test
    fun withoutTheServiceSleepSaysSo() {
        val service = startService()
        service.onServiceConnected()
        service.onDestroy()
        assertFalse(HomeOverrideService.sleepTv())
    }

    companion object {
        const val GOOGLE_TV = "com.google.android.apps.tv.launcherx"
    }
}
