package com.glasshome.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Faces are searched once per photo, not every time the photo comes round. */
class FramingCacheTest {

    @Test
    fun aPhotoSeenBeforeKeepsItsFraming() {
        val cache = FramingCache()
        val framing = PhotoFraming(false, 0.3f)
        cache.put("own:beach.jpg", framing)
        assertSame(framing, cache.get("own:beach.jpg"))
        assertNull(cache.get("own:other.jpg"))
    }

    @Test
    fun theOldestIsDroppedWhenFull() {
        val cache = FramingCache(capacity = 2)
        cache.put("a", PhotoFraming(false, 0.5f))
        cache.put("b", PhotoFraming(false, 0.5f))
        cache.get("a")
        cache.put("c", PhotoFraming(true, 0.5f))
        assertEquals(2, cache.size())
        assertNull(cache.get("b"))
    }
}
