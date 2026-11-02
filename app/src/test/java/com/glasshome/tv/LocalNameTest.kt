package com.glasshome.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** glasshome.local is announced again every minute, so a wrong cached address goes away. */
class LocalNameTest {

    @Test
    fun theNameIsAnnouncedEveryMinute() {
        assertFalse(LocalName.isAnnounceDue(0L, LocalName.ANNOUNCE_EVERY_MS - 1))
        assertTrue(LocalName.isAnnounceDue(0L, LocalName.ANNOUNCE_EVERY_MS))
    }

    /** The TV's clock set back (network time after boot): announce now rather than in hours. */
    @Test
    fun aClockSetBackDoesNotStopAnnouncing() {
        assertTrue(LocalName.isAnnounceDue(10_000_000L, 5_000L))
    }
}
