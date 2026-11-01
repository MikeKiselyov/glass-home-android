package com.glasshome.tv

import android.app.Application
import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The settings screen on the TV only ever shows the TV's own website. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SettingsActivityTest {

    /** Other apps can't open it: it signs itself in without the code. */
    @Test
    fun otherAppsCannotOpenIt() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getActivityInfo(ComponentName(context, SettingsActivity::class.java), 0)
        assertFalse(info.exported)
    }


    /** The website starts in the background: settings opened right after the app starts wait for it. */
    @Test
    fun waitsForTheWebsiteButNotForever() {
        assertTrue(SettingsActivity.loadNow(true, 0))
        assertFalse(SettingsActivity.loadNow(false, 0))
        assertFalse(SettingsActivity.loadNow(false, 49))
        assertTrue(SettingsActivity.loadNow(false, 50))
    }

    /** A WebView stays in its process for good: settings get a process of their own that ends on close. */
    @Test
    fun runsInItsOwnProcess() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getActivityInfo(ComponentName(context, SettingsActivity::class.java), 0)
        assertEquals("com.glasshome.tv:settings", info.processName)
        assertTrue(GlassHomeApp.isSettingsProcess("com.glasshome.tv:settings"))
        assertFalse(GlassHomeApp.isSettingsProcess("com.glasshome.tv"))
    }

    /** The sign-in is made where the website runs and handed over with the intent. */
    @Test
    fun theSessionTravelsWithTheIntent() {
        val context = RuntimeEnvironment.getApplication()
        val intent = SettingsActivity.intent(context, "token-123")
        assertEquals(SettingsActivity::class.java.name, intent.component?.className)
        assertEquals("token-123", SettingsActivity.sessionOf(intent))
        assertEquals(null, SettingsActivity.sessionOf(SettingsActivity.intent(context, null)))
    }

    @Test
    fun opensTheTvWebsite() {
        assertTrue(SettingsActivity.isTvWebsite("http://127.0.0.1:8090"))
        assertTrue(SettingsActivity.isTvWebsite("http://127.0.0.1:8090/"))
        assertTrue(SettingsActivity.isTvWebsite("http://127.0.0.1:8090/#look"))
        assertTrue(SettingsActivity.isTvWebsite("http://127.0.0.1:8090/?code=1"))
    }

    @Test
    fun refusesEverythingElse() {
        assertFalse(SettingsActivity.isTvWebsite("https://open-meteo.com/"))
        assertFalse(SettingsActivity.isTvWebsite("http://127.0.0.1:80901/"))
        assertFalse(SettingsActivity.isTvWebsite("http://127.0.0.1:8090.evil.example/"))
        assertFalse(SettingsActivity.isTvWebsite("http://127.0.0.1:8090@evil.example/"))
        assertFalse(SettingsActivity.isTvWebsite("file:///sdcard/"))
    }
}
