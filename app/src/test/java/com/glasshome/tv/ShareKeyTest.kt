package com.glasshome.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** The key an iPhone shortcut sends links to the TV with. */
class ShareKeyTest {

    private class MemoryStorage : ShareKeyStorage {
        var saved: String? = null

        override fun loadKey(): String? {
            return saved
        }

        override fun saveKey(key: String) {
            saved = key
        }
    }

    @Test
    fun theKeyIsLongRandomAndKept() {
        val storage = MemoryStorage()
        val key = ShareKey(storage, Random(1)).key
        assertEquals(32, key.length)
        assertTrue(key.all { character -> character in '0'..'9' || character in 'a'..'f' })
        assertEquals(key, ShareKey(storage, Random(2)).key)
    }

    @Test
    fun onlyTheKeyMatches() {
        val shareKey = ShareKey(MemoryStorage(), Random(1))
        assertTrue(shareKey.matches(shareKey.key))
        assertFalse(shareKey.matches(null))
        assertFalse(shareKey.matches(""))
        assertFalse(shareKey.matches(shareKey.key.dropLast(1)))
        assertFalse(shareKey.matches(shareKey.key.uppercase()))
    }

    /** "New code" on the TV replaces the key too: the old shortcut stops working. */
    @Test
    fun aNewKeyRetiresTheOldOne() {
        val storage = MemoryStorage()
        val shareKey = ShareKey(storage, Random(1))
        val old = shareKey.key
        shareKey.reset()
        assertNotEquals(old, shareKey.key)
        assertFalse(shareKey.matches(old))
        assertEquals(shareKey.key, storage.saved)
    }
}
