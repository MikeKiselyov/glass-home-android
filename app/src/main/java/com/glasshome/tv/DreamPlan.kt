package com.glasshome.tv

/** What the screensaver shows when: the photo order and where the clock sits. */
object DreamPlan {

    /** The photo after [current] in a list of [count]; -1 with no photos. */
    fun nextPhoto(current: Int, count: Int): Int {
        if (count <= 0) {
            return -1
        }
        if (current < 0) {
            return 0
        }
        return (current + 1) % count
    }

    /**
     * The clock's shift for this minute, right and up from its corner, within
     * [range] pixels: eight places in turn, so no pixel stays lit for hours.
     */
    fun clockOffset(minute: Int, range: Int): Pair<Int, Int> {
        val place = minute % PLACES
        val column = place % 4
        val row = place / 4
        return Pair(column * range / 3, -row * range)
    }

    /** How long a photo stays: the home screen's setting, at least 15 seconds. */
    fun photoMillis(intervalSeconds: Int): Long {
        val seconds = maxOf(intervalSeconds, 15)
        return seconds * 1000L
    }

    private const val PLACES = 8
}
