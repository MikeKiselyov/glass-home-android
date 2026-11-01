package com.glasshome.tv

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Pressing Home does not ask for the weather again when the last reading is recent. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class WeatherFreshTest {

    private val minute = 60_000L

    @Test
    fun aReadingFromMinutesAgoIsFresh() {
        val now = 1_000_000_000L
        assertTrue(Weather.isFresh(now - 1 * minute, now))
        assertTrue(Weather.isFresh(now - 24 * minute, now))
    }

    @Test
    fun anOlderReadingIsFetchedAgain() {
        val now = 1_000_000_000L
        assertFalse(Weather.isFresh(now - 25 * minute, now))
        assertFalse(Weather.isFresh(now - 3 * 60 * minute, now))
    }

    /** A clock set back (the TV getting its time after a cold boot) must not keep old weather forever. */
    @Test
    fun aReadingFromTheFutureIsNotTrusted() {
        val now = 1_000_000_000L
        assertFalse(Weather.isFresh(now + 10 * minute, now))
    }

    /** Home at 24 minutes must not push the next reading to 54 minutes. */
    @Test
    fun theNextFetchCountsFromTheReadingNotFromHome() {
        val now = 1_000_000_000L
        val every = 30 * minute
        assertEquals(6 * minute, Weather.nextFetchIn(now - 24 * minute, now, every))
        assertEquals(0L, Weather.nextFetchIn(now - 40 * minute, now, every))
        assertEquals(every, Weather.nextFetchIn(now + 10 * minute, now, every))
    }

    @Test
    fun noReadingYetIsNotFresh() {
        assertFalse(Weather(RuntimeEnvironment.getApplication()).isFresh())
    }
}
