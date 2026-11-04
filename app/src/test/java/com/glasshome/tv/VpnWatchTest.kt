package com.glasshome.tv

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork

/** The home screen shows "VPN" while a VPN is on. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class VpnWatchTest {

    private val context = RuntimeEnvironment.getApplication()
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Test
    fun onWhileAVpnIsUpOffWhenTheLastOneGoes() {
        val seen = ArrayList<Boolean>()
        val watch = VpnWatch(context) { on -> seen.add(on) }
        watch.start()
        val callback = shadowOf(connectivity).networkCallbacks.single()
        val first = ShadowNetwork.newInstance(101)
        val second = ShadowNetwork.newInstance(102)

        callback.onAvailable(first)
        callback.onAvailable(second)
        callback.onLost(first)
        assertEquals(listOf(true), seen)
        callback.onLost(second)
        assertEquals(listOf(true, false), seen)

        watch.stop()
        assertEquals(0, shadowOf(connectivity).networkCallbacks.size)
    }

    /** Only VPNs: the Wi-Fi itself never counts. */
    @Test
    fun theRequestAsksForVpnsOnly() {
        val request = VpnWatch.request()
        assertEquals(true, request.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN))
        assertEquals(false, request.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
    }

    /** The website's "This TV" asks once: is any VPN up right now? */
    @Test
    fun isOnReadsTheNetworksThereAreNow() {
        assertEquals(false, VpnWatch.isOn(context))
        val vpn = ShadowNetwork.newInstance(201)
        val capabilities = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addTransportType(android.net.NetworkCapabilities.TRANSPORT_VPN)
        shadowOf(connectivity).addNetwork(vpn, org.robolectric.shadows.ShadowNetworkInfo.newInstance(
            android.net.NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_VPN, 0, true, android.net.NetworkInfo.State.CONNECTED))
        shadowOf(connectivity).setNetworkCapabilities(vpn, capabilities)
        assertEquals(true, VpnWatch.isOn(context))
    }
}
