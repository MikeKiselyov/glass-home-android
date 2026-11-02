package com.glasshome.tv

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Found on the TV: what the updater remembers between checks and across an update. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class UpdaterTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    /**
     * The TV was asleep (no network) when the check ran. That must not count as
     * the day's check, or the next try would be a whole day later.
     */
    @Test
    fun aCheckGitHubDidNotAnswerIsTriedAgainSoon() {
        val updater = Updater(context, fetchForTests = { null })
        updater.checkNow()
        assertEquals(Updater.STATE_FAILED, updater.state)
        assertEquals(0L, updater.lastCheckMillis)
        assertTrue(UpdateCheck.isDue(updater.lastCheckMillis, System.currentTimeMillis()))
    }

    @Test
    fun aCheckGitHubAnsweredCountsForTheDay() {
        val reply = """{"tag_name": "v0.0.1", "assets": []}"""
        val updater = Updater(context, fetchForTests = { reply })
        updater.checkNow()
        assertEquals(Updater.STATE_IDLE, updater.state)
        assertFalse(UpdateCheck.isDue(updater.lastCheckMillis, System.currentTimeMillis()))
    }

    @Test
    fun aReopenRequestIsTakenOnceAndOnlyWhileFresh() {
        val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        val now = 1_000_000_000_000L
        prefs.edit().putLong("reopen_home_at", now - 30_000L).commit()
        assertTrue(Updater.takeReopenRequest(context, now))
        assertFalse(Updater.takeReopenRequest(context, now))

        prefs.edit().putLong("reopen_home_at", now - 10 * 60_000L).commit()
        assertFalse(Updater.takeReopenRequest(context, now))
    }

    @Test
    fun reopenCountsForAFewMinutesOnly() {
        val now = 1_000_000_000_000L
        assertTrue(UpdateCheck.isRecentReopen(now, now))
        assertTrue(UpdateCheck.isRecentReopen(now - 4 * 60_000L, now))
        assertFalse(UpdateCheck.isRecentReopen(now - 6 * 60_000L, now))
        assertFalse(UpdateCheck.isRecentReopen(now + 60_000L, now))
    }
}
