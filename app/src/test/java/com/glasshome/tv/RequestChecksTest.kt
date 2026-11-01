package com.glasshome.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestChecksTest {

    // ---------- Host header ----------

    @Test
    fun acceptsTheTvIpWithAndWithoutPort() {
        assertTrue(RequestChecks.isAllowedHost("192.168.1.20:8090"))
        assertTrue(RequestChecks.isAllowedHost("192.168.1.20"))
        assertTrue(RequestChecks.isAllowedHost("10.0.0.5:8090"))
    }

    @Test
    fun acceptsTheLocalNameAndLocalhost() {
        assertTrue(RequestChecks.isAllowedHost("glasshome.local:8090"))
        assertTrue(RequestChecks.isAllowedHost("GlassHome.Local:8090"))
        assertTrue(RequestChecks.isAllowedHost("localhost:8090"))
    }

    @Test
    fun acceptsIpv6Literals() {
        assertTrue(RequestChecks.isAllowedHost("[fe80::1]:8090"))
        assertTrue(RequestChecks.isAllowedHost("[fe80::1%wlan0]:8090"))
        assertTrue(RequestChecks.isAllowedHost("[::1]"))
    }

    @Test
    fun acceptsNamesOnlyAHomeNetworkHandsOut() {
        assertTrue(RequestChecks.isAllowedHost("android-1a2b3c:8090"))
        assertTrue(RequestChecks.isAllowedHost("tv.lan:8090"))
        assertTrue(RequestChecks.isAllowedHost("tv.home.arpa"))
        assertTrue(RequestChecks.isAllowedHost("glasshome.local.:8090"))
    }

    @Test
    fun refusesOtherNamesAsUsedByDnsRebinding() {
        assertFalse(RequestChecks.isAllowedHost("evil.example.com:8090"))
        assertFalse(RequestChecks.isAllowedHost("192.168.1.20.nip.io:8090"))
        assertFalse(RequestChecks.isAllowedHost("glasshome.local.evil.com"))
        assertFalse(RequestChecks.isAllowedHost("tv.lan.evil.com"))
        assertFalse(RequestChecks.isAllowedHost("evil.com."))
        assertFalse(RequestChecks.isAllowedHost("tv_lan"))
    }

    @Test
    fun refusesMissingOrBrokenHosts() {
        assertFalse(RequestChecks.isAllowedHost(null))
        assertFalse(RequestChecks.isAllowedHost(""))
        assertFalse(RequestChecks.isAllowedHost("   "))
        assertFalse(RequestChecks.isAllowedHost("[fe80::1"))
        assertFalse(RequestChecks.isAllowedHost("300.1.1.1"))
        assertFalse(RequestChecks.isAllowedHost("1.2.3"))
        assertFalse(RequestChecks.isAllowedHost("[abc]"))
    }

    // ---------- home network ----------

    @Test
    fun acceptsHomeNetworkAddresses() {
        assertTrue(RequestChecks.isHomeNetwork("192.168.1.30"))
        assertTrue(RequestChecks.isHomeNetwork("10.1.2.3"))
        assertTrue(RequestChecks.isHomeNetwork("172.16.0.9"))
        assertTrue(RequestChecks.isHomeNetwork("127.0.0.1"))
        assertTrue(RequestChecks.isHomeNetwork("fe80::1"))
    }

    @Test
    fun refusesInternetAddresses() {
        assertFalse(RequestChecks.isHomeNetwork("8.8.8.8"))
        assertFalse(RequestChecks.isHomeNetwork("2001:4860:4860::8888"))
        assertFalse(RequestChecks.isHomeNetwork(""))
    }
}
