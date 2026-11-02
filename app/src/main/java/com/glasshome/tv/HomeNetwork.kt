package com.glasshome.tv

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * The network the phones are on: the TV's Wi-Fi or Ethernet, with its
 * home-network IPv4 address. A VPN on the TV (Surfshark, for example) adds a
 * tunnel interface with an address like 10.14.0.2; phones can't reach that,
 * and mDNS joined through it never hears them. Tunnels are point-to-point
 * links, so those are skipped.
 */
object HomeNetwork {

    class Candidate(
        val name: String,
        val up: Boolean,
        val loopback: Boolean,
        val pointToPoint: Boolean,
        val siteLocalAddress: String?,
    )

    class Found(val networkInterface: NetworkInterface, val address: Inet4Address)

    fun pick(candidates: List<Candidate>): Candidate? {
        for (candidate in candidates) {
            if (!candidate.up || candidate.loopback || candidate.pointToPoint) {
                continue
            }
            if (candidate.siteLocalAddress != null) {
                return candidate
            }
        }
        return null
    }

    /** The home network on this TV now, or null when there is none. */
    fun find(): Found? {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        val candidates = ArrayList<Candidate>()
        val found = HashMap<String, Found>()
        for (networkInterface in interfaces) {
            val address = siteLocalAddress(networkInterface)
            val candidate = Candidate(
                networkInterface.name,
                up = networkInterface.isUp,
                loopback = networkInterface.isLoopback,
                pointToPoint = networkInterface.isPointToPoint,
                siteLocalAddress = address?.hostAddress,
            )
            candidates.add(candidate)
            if (address != null) {
                found[networkInterface.name] = Found(networkInterface, address)
            }
        }
        val picked = pick(candidates) ?: return null
        return found[picked.name]
    }

    private fun siteLocalAddress(networkInterface: NetworkInterface): Inet4Address? {
        for (inetAddress in networkInterface.inetAddresses) {
            if (inetAddress is Inet4Address && inetAddress.isSiteLocalAddress) {
                return inetAddress
            }
        }
        return null
    }
}
