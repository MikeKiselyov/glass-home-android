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

    /** Found on the TV: after standby the name was gone. A changed network means starting over. */
    @Test
    fun aChangedNetworkIsJoinedAgain() {
        assertFalse(LocalName.networkChanged("wlan0", "192.168.1.250", "wlan0", "192.168.1.250"))
        assertTrue(LocalName.networkChanged("wlan0", "192.168.1.250", null, null))
        assertTrue(LocalName.networkChanged("wlan0", "192.168.1.250", "eth0", "192.168.1.250"))
        assertTrue(LocalName.networkChanged("wlan0", "192.168.1.250", "wlan0", "192.168.1.31"))
    }
}
