package com.glasshome.tv

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A link from the phone opens in the TV app that handles it. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LinkOpenerTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun installBrowser() {
        val browser = ComponentName("com.phlox.tvwebbrowser", "com.phlox.tvwebbrowser.activity.main.MainActivity")
        val packageManager = shadowOf(context.packageManager)
        packageManager.addActivityIfNotPresent(browser)
        val filter = IntentFilter(Intent.ACTION_VIEW)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        filter.addCategory(Intent.CATEGORY_BROWSABLE)
        filter.addDataScheme("http")
        filter.addDataScheme("https")
        packageManager.addIntentFilterForActivity(browser, filter)
    }

    private var wakes = 0

    private fun opener(canStart: Boolean = true): LinkOpener {
        return LinkOpener(context, { canStart }, { wakes += 1 })
    }

    /** The screensaver goes first, or the app would open behind it. */
    @Test
    fun theScreenWakesBeforeTheAppOpens() {
        installBrowser()
        opener().open("https://example.com/film")
        assertEquals(1, wakes)
    }

    @Test
    fun nothingWakesWhenNothingOpens() {
        opener().open("https://example.com/film")
        installBrowser()
        opener(canStart = false).open("https://example.com/film")
        assertEquals(0, wakes)
    }

    @Test
    fun theLinkOpensInTheBrowser() {
        installBrowser()
        val result = opener().open("https://example.com/film")
        assertEquals(LinkOpener.Result.OPENED, result)
        val started = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://example.com/film", started.dataString)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Google TV has no browser of its own: say so instead of failing quietly. */
    @Test
    fun withoutAnAppForLinksNothingStarts() {
        val result = opener().open("https://example.com/film")
        assertEquals(LinkOpener.Result.NO_APP, result)
        assertNull(shadowOf(context).nextStartedActivity)
    }

    /**
     * Found in review: intent filters match the scheme case by case, so
     * "HTTPS://" found no app although a browser was there.
     */
    @Test
    fun anUppercaseSchemeStillOpens() {
        installBrowser()
        assertEquals(LinkOpener.Result.OPENED, opener().open("HTTPS://example.com/film"))
        assertEquals("https://example.com/film", shadowOf(context).nextStartedActivity.dataString)
    }

    /**
     * Found in review: with another app in front, Android lets Glass Home start
     * one only while its accessibility service is on, and drops the start
     * quietly otherwise. The phone heard "Opened" and nothing happened.
     */
    @Test
    fun withTheServiceOffBehindAnotherAppThePhoneIsTold() {
        installBrowser()
        assertEquals(LinkOpener.Result.SERVICE_OFF, opener(canStart = false).open("https://example.com/film"))
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test
    fun theServiceIsReadFromTheTvSettings() {
        val resolver = context.contentResolver
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
        assertFalse(LinkOpener.serviceOn(context))
        val ours = ComponentName(context, HomeOverrideService::class.java).flattenToString()
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "com.other/.Service:$ours")
        assertTrue(LinkOpener.serviceOn(context))
    }
}
