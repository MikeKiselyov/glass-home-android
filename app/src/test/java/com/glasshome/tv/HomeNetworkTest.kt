package com.glasshome.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The network the phones are on: Wi-Fi or Ethernet, never a VPN tunnel. */
class HomeNetworkTest {

    private fun wifi(address: String = "192.168.1.250"): HomeNetwork.Candidate {
        return HomeNetwork.Candidate("wlan0", up = true, loopback = false, pointToPoint = false, siteLocalAddress = address)
    }

    /** Found on the TV: Surfshark's tunnel (10.14.0.2) took glasshome.local off the home Wi-Fi. */
    @Test
    fun aVpnTunnelIsSkipped() {
        val tunnel = HomeNetwork.Candidate("tun1", up = true, loopback = false, pointToPoint = true, siteLocalAddress = "10.14.0.2")
        assertEquals("wlan0", HomeNetwork.pick(listOf(tunnel, wifi()))?.name)
        assertEquals("wlan0", HomeNetwork.pick(listOf(wifi(), tunnel))?.name)
    }

    @Test
    fun loopbackDownAndAddresslessInterfacesAreSkipped() {
        val loopback = HomeNetwork.Candidate("lo", up = true, loopback = true, pointToPoint = false, siteLocalAddress = "127.0.0.1")
        val down = HomeNetwork.Candidate("eth0", up = false, loopback = false, pointToPoint = false, siteLocalAddress = "192.168.1.40")
        val noAddress = HomeNetwork.Candidate("p2p0", up = true, loopback = false, pointToPoint = false, siteLocalAddress = null)
        assertEquals("wlan0", HomeNetwork.pick(listOf(loopback, down, noAddress, wifi()))?.name)
    }

    @Test
    fun onlyATunnelMeansNoHomeNetwork() {
        val tunnel = HomeNetwork.Candidate("tun1", up = true, loopback = false, pointToPoint = true, siteLocalAddress = "10.14.0.2")
        assertNull(HomeNetwork.pick(listOf(tunnel)))
        assertNull(HomeNetwork.pick(emptyList()))
    }
}
