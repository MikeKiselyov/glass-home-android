package com.glasshome.tv

/**
 * Where to crop each photo (found by looking for faces), kept by photo so the
 * search runs once per photo instead of on every turn. The newest [capacity]
 * photos are kept; the framing objects are a few bytes each.
 */
class FramingCache(private val capacity: Int = 500) {

    private val framings = LinkedHashMap<String, PhotoFraming>(16, 0.75f, true)

    @Synchronized
    fun get(key: String): PhotoFraming? {
        return framings[key]
    }

    @Synchronized
    fun put(key: String, framing: PhotoFraming) {
        framings[key] = framing
        while (framings.size > capacity) {
            val oldest = framings.keys.iterator().next()
            framings.remove(oldest)
        }
    }

    @Synchronized
    fun size(): Int {
        return framings.size
    }
}
