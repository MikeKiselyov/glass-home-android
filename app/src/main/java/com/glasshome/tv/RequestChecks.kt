package com.glasshome.tv

import java.net.InetAddress

/** Who may talk to the photo website. */
object RequestChecks {

    /** Phones on the home network (and the TV itself) only. */
    fun isHomeNetwork(remote: String): Boolean {
        if (remote.isEmpty()) {
            return false
        }
        return try {
            val address = InetAddress.getByName(remote)
            address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress
        } catch (error: Exception) {
            false
        }
    }

    /** Names only a home network hands out: nobody can register them on the internet. */
    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home", ".home.arpa", ".internal", ".localdomain")

    /**
     * The Host header must name the TV the way a home network does: an IP
     * address, a name without dots ("android-1a2b"), or a name under a local-only
     * ending (glasshome.local, tv.lan, tv.home.arpa). A web page that points its
     * own internet domain at the TV (DNS rebinding) sends that domain here and is
     * turned away.
     */
    fun isAllowedHost(hostHeader: String?): Boolean {
        if (hostHeader.isNullOrBlank()) {
            return false
        }
        val header = hostHeader.trim().lowercase()
        val host = hostName(header).removeSuffix(".")
        if (host.isEmpty()) {
            return false
        }
        if (header.startsWith("[")) {
            // Brackets hold an IPv6 address and nothing else.
            return isIpv6(host)
        }
        if (isIpAddress(host)) {
            return true
        }
        if (!isHostName(host)) {
            return false
        }
        if (!host.contains('.')) {
            return true
        }
        for (suffix in LOCAL_SUFFIXES) {
            if (host.endsWith(suffix)) {
                return true
            }
        }
        return false
    }

    /** Letters, digits, dashes and dots only. */
    private fun isHostName(host: String): Boolean {
        for (character in host) {
            val allowed = character in 'a'..'z' || character in '0'..'9' || character == '-' || character == '.'
            if (!allowed) {
                return false
            }
        }
        return true
    }

    /** "192.168.1.20:8090" -> "192.168.1.20", "[fe80::1]:8090" -> "fe80::1". */
    private fun hostName(host: String): String {
        if (host.startsWith("[")) {
            val end = host.indexOf(']')
            if (end < 0) {
                return ""
            }
            return host.substring(1, end)
        }
        val colon = host.indexOf(':')
        if (colon < 0) {
            return host
        }
        return host.substring(0, colon)
    }

    private fun isIpAddress(host: String): Boolean {
        if (host.contains(':')) {
            return isIpv6(host)
        }
        val parts = host.split(".")
        if (parts.size != 4) {
            return false
        }
        for (part in parts) {
            if (part.isEmpty() || part.length > 3) {
                return false
            }
            for (character in part) {
                if (character !in '0'..'9') {
                    return false
                }
            }
            if (part.toInt() > 255) {
                return false
            }
        }
        return true
    }

    private fun isIpv6(host: String): Boolean {
        val withoutZone = host.substringBefore('%')
        for (character in withoutZone) {
            val isHexDigit = character in '0'..'9' || character in 'a'..'f'
            if (!isHexDigit && character != ':' && character != '.') {
                return false
            }
        }
        return withoutZone.count { character -> character == ':' } >= 2
    }
}
