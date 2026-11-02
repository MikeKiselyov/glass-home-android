package com.glasshome.tv

import java.security.MessageDigest
import java.util.Random

/** Where the share key is kept between launches. */
interface ShareKeyStorage {
    fun loadKey(): String?
    fun saveKey(key: String)
}

/**
 * The key an iPhone shortcut sends links with ("Send To TV" in the Share
 * menu): POST /send/<key>, no browser and no sign-in. It only opens links.
 *
 * 128 random bits, so it can't be guessed the way the 6-digit code could be;
 * the website shows it to signed-in phones. "New code" on the TV replaces it.
 */
class ShareKey(private val storage: ShareKeyStorage, private val random: Random) {

    @Volatile
    var key: String = storage.loadKey() ?: created()
        private set

    /** Compares in constant time: how long it takes says nothing about the key. */
    fun matches(given: String?): Boolean {
        if (given.isNullOrEmpty()) {
            return false
        }
        return MessageDigest.isEqual(given.toByteArray(Charsets.US_ASCII), key.toByteArray(Charsets.US_ASCII))
    }

    fun reset() {
        key = created()
    }

    private fun created(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        val text = StringBuilder()
        for (byte in bytes) {
            text.append(String.format("%02x", byte))
        }
        val made = text.toString()
        storage.saveKey(made)
        return made
    }
}
