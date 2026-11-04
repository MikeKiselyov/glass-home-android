package com.glasshome.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Tells the home screen whether a VPN is on, for the "VPN" mark at the top.
 * Every VPN network counts, not only the one Glass Home's own traffic uses:
 * an app left out of the tunnel still sees the TV's VPN. [onChange] runs on
 * a system thread.
 */
class VpnWatch(context: Context, private val onChange: (Boolean) -> Unit) {

    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
    private val vpns = HashSet<Network>()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            update(network, true)
        }

        override fun onLost(network: Network) {
            update(network, false)
        }
    }

    fun start() {
        connectivity?.registerNetworkCallback(request(), callback)
    }

    fun stop() {
        try {
            connectivity?.unregisterNetworkCallback(callback)
        } catch (notRegistered: IllegalArgumentException) {
            // Never started.
        }
    }

    @Synchronized
    private fun update(network: Network, up: Boolean) {
        val wasOn = vpns.isNotEmpty()
        if (up) {
            vpns.add(network)
        } else {
            vpns.remove(network)
        }
        val isOn = vpns.isNotEmpty()
        if (isOn != wasOn) {
            onChange(isOn)
        }
    }

    companion object {
        /** Whether a VPN is up right now, for the website's "This TV". */
        @Suppress("DEPRECATION") // allNetworks: the one call that lists every network at once.
        fun isOn(context: Context): Boolean {
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager? ?: return false
            for (network in connectivity.allNetworks) {
                val capabilities = connectivity.getNetworkCapabilities(network) ?: continue
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return true
                }
            }
            return false
        }

        /** VPN networks only; a plain request leaves VPNs out (NOT_VPN). */
        fun request(): NetworkRequest {
            return NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
        }
    }
}
