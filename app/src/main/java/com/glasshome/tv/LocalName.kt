package com.glasshome.tv

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException

/**
 * Answers mDNS questions for "glasshome.local", so phones can open
 * http://glasshome.local:8090 instead of the TV's IP address. Only A records
 * (IPv4) for that one name; everything else on the network is ignored.
 */
class LocalName(private val context: Context) {

    @Volatile
    private var running = false
    private var socket: MulticastSocket? = null
    private var lock: WifiManager.MulticastLock? = null

    fun start() {
        if (running) {
            return
        }
        running = true
        val thread = Background.factory("glasshome-mdns").newThread { serve() }
        thread.start()
    }

    /**
     * Answers on the home network until Glass Home stops. Found on the TV:
     * after the sleep timer put it into standby, a send with the Wi-Fi down
     * threw and glasshome.local was gone until Glass Home restarted. Now any
     * failure, or a change of network, starts over on the network there is.
     */
    private fun serve() {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val multicastLock = wifi.createMulticastLock("glasshome-mdns")
            multicastLock.setReferenceCounted(false)
            multicastLock.acquire()
            lock = multicastLock
            while (running) {
                // Right after boot or standby the Wi-Fi may not be up yet.
                val home = HomeNetwork.find()
                if (home != null) {
                    try {
                        serveOn(home)
                    } catch (error: Exception) {
                        Log.w(TAG, "glasshome.local stopped, starting over", error)
                    }
                    socket?.close()
                    socket = null
                }
                if (running) {
                    Thread.sleep(NETWORK_WAIT_MS)
                }
            }
        } catch (stopped: InterruptedException) {
            // Glass Home is going away.
        } finally {
            running = false
            lock?.release()
        }
    }

    /** Until a failure, or until the home network is another one (Wi-Fi to Ethernet, a new address). */
    private fun serveOn(home: HomeNetwork.Found) {
        val group = InetAddress.getByName(MDNS_GROUP)
        val multicast = MulticastSocket(MDNS_PORT)
        socket = multicast
        // On the home network by name: with a VPN on, the default route (and
        // with it the group membership and the replies) goes into the tunnel.
        multicast.networkInterface = home.networkInterface
        multicast.joinGroup(InetSocketAddress(group, MDNS_PORT), home.networkInterface)

        announce(multicast, group)
        var announcedAt = System.currentTimeMillis()
        // Wake up now and then without a question, to announce the name again.
        multicast.soTimeout = ANNOUNCE_EVERY_MS.toInt()
        val buffer = ByteArray(1500)
        while (running) {
            val now = System.currentTimeMillis()
            if (isAnnounceDue(announcedAt, now)) {
                val found = HomeNetwork.find()
                if (networkChanged(home.networkInterface.name, home.address.hostAddress, found?.networkInterface?.name, found?.address?.hostAddress)) {
                    return
                }
                announce(multicast, group)
                announcedAt = now
            }
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                multicast.receive(packet)
            } catch (quiet: SocketTimeoutException) {
                continue
            }
            if (asksForOurName(packet.data, packet.length)) {
                val reply = answer() ?: continue
                multicast.send(DatagramPacket(reply, reply.size, group, MDNS_PORT))
                // A one-shot question from a phone (not port 5353) also gets a direct reply.
                if (packet.port != MDNS_PORT) {
                    multicast.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                }
            }
        }
    }

    /**
     * Tells the network about the name: at start, as mDNS hosts do, and then
     * every [ANNOUNCE_EVERY_MS]. Found on a Mac: it held a wrong address for
     * glasshome.local for minutes. The answer carries the cache-flush bit, so
     * every device that hears it drops any other address it has for the name.
     */
    private fun announce(multicast: MulticastSocket, group: InetAddress) {
        val reply = answer() ?: return
        multicast.send(DatagramPacket(reply, reply.size, group, MDNS_PORT))
    }

    /** True when the packet is a query with a question for glasshome.local (A or ANY). */
    private fun asksForOurName(data: ByteArray, length: Int): Boolean {
        if (length < 12) {
            return false
        }
        val isResponse = (data[2].toInt() and 0x80) != 0
        if (isResponse) {
            return false
        }
        val questions = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        var offset = 12
        for (index in 0 until questions) {
            val labels = ArrayList<String>()
            while (offset < length) {
                val labelLength = data[offset].toInt() and 0xFF
                if (labelLength == 0) {
                    offset += 1
                    break
                }
                if (labelLength >= 0xC0) {
                    // Compressed names don't occur in single-question queries for our name.
                    return false
                }
                if (offset + 1 + labelLength > length) {
                    return false
                }
                labels.add(String(data, offset + 1, labelLength, Charsets.US_ASCII))
                offset += 1 + labelLength
            }
            if (offset + 4 > length) {
                return false
            }
            val type = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            offset += 4
            val name = labels.joinToString(".")
            // AAAA too: the reply then says "no IPv6 here", so phones don't wait for one.
            val wantsAddress = type == TYPE_A || type == TYPE_AAAA || type == TYPE_ANY
            if (wantsAddress && name.equals(HOST_NAME, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * A response with the A record (glasshome.local -> this TV's IPv4 address)
     * and an NSEC record saying the name has no other addresses (RFC 6762 6.1),
     * so resolvers don't wait seconds for an IPv6 answer that never comes.
     */
    private fun answer(): ByteArray? {
        val address = localAddress() ?: return null
        val name = encodedName()
        val out = ByteArrayOutputStream()
        // Header: id 0, flags "authoritative response", 0 questions, 1 answer, 0 authority, 1 additional.
        out.write(byteArrayOf(0, 0, 0x84.toByte(), 0, 0, 0, 0, 1, 0, 0, 0, 1))

        out.write(name)
        out.write(byteArrayOf(0, TYPE_A.toByte()))
        // Class IN with the cache-flush bit: this is the only address for the name.
        out.write(byteArrayOf(0x80.toByte(), 1))
        out.write(byteArrayOf(0, 0, 0, TTL_SECONDS.toByte()))
        out.write(byteArrayOf(0, 4))
        out.write(address.address)

        out.write(name)
        out.write(byteArrayOf(0, TYPE_NSEC.toByte()))
        out.write(byteArrayOf(0x80.toByte(), 1))
        out.write(byteArrayOf(0, 0, 0, TTL_SECONDS.toByte()))
        // Data: the name again, then a type bitmap (window 0, 1 byte) with only A set.
        val dataLength = name.size + 3
        out.write(byteArrayOf(0, dataLength.toByte()))
        out.write(name)
        out.write(byteArrayOf(0, 1, 0x40))
        return out.toByteArray()
    }

    private fun encodedName(): ByteArray {
        val out = ByteArrayOutputStream()
        for (label in HOST_NAME.split(".")) {
            out.write(label.length)
            out.write(label.toByteArray(Charsets.US_ASCII))
        }
        out.write(0)
        return out.toByteArray()
    }

    private fun localAddress(): Inet4Address? {
        return HomeNetwork.find()?.address
    }

    companion object {
        const val HOST_NAME = "glasshome.local"
        private const val TAG = "GlassHome"
        private const val MDNS_GROUP = "224.0.0.251"
        private const val MDNS_PORT = 5353
        private const val NETWORK_WAIT_MS = 10_000L
        private const val TYPE_A = 1
        private const val TYPE_AAAA = 28
        private const val TYPE_NSEC = 47
        private const val TYPE_ANY = 255
        private const val TTL_SECONDS = 120
        const val ANNOUNCE_EVERY_MS = 60_000L

        /** The network joined is not the home network any more (gone, another interface, a new address). */
        fun networkChanged(joinedName: String, joinedAddress: String?, foundName: String?, foundAddress: String?): Boolean {
            return joinedName != foundName || joinedAddress != foundAddress
        }

        fun isAnnounceDue(announcedAt: Long, now: Long): Boolean {
            return now - announcedAt >= ANNOUNCE_EVERY_MS || now < announcedAt
        }
    }
}
