package com.glasshome.tv

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun theLinkOpensInTheBrowser() {
        installBrowser()
        val result = LinkOpener(context).open("https://example.com/film")
        assertEquals(LinkOpener.Result.OPENED, result)
        val started = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://example.com/film", started.dataString)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Google TV has no browser of its own: say so instead of failing quietly. */
    @Test
    fun withoutAnAppForLinksNothingStarts() {
        val result = LinkOpener(context).open("https://example.com/film")
        assertEquals(LinkOpener.Result.NO_APP, result)
        assertNull(shadowOf(context).nextStartedActivity)
    }
}
