package com.glasshome.tv

/** Checks on the size an uploaded picture says it has, before decoding it. */
object ImageLimits {

    /** 100 megapixels: far beyond any phone photo, small enough to refuse bombs. */
    const val MAX_PIXELS = 100_000_000L

    fun isAcceptable(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) {
            return false
        }
        return width.toLong() * height.toLong() <= MAX_PIXELS
    }

    /** At most this many pixels are ever decoded for an icon (16 MB as ARGB). */
    const val MAX_DECODED_PIXELS = 4_000_000L

    /**
     * The power of two to decode with: as large as keeps the shorter side at
     * least [targetSide], and at least large enough that the decoded picture
     * stays under [MAX_DECODED_PIXELS] (a 100000 x 1000 strip, say).
     */
    fun sampleSize(width: Int, height: Int, targetSide: Int): Int {
        val shorterSide = minOf(width, height)
        var sample = 1
        while (shorterSide / (sample * 2) >= targetSide) {
            sample *= 2
        }
        while (decodedPixels(width, height, sample) > MAX_DECODED_PIXELS) {
            sample *= 2
        }
        return sample
    }

    fun decodedPixels(width: Int, height: Int, sample: Int): Long {
        val decodedWidth = (width / sample).toLong()
        val decodedHeight = (height / sample).toLong()
        return decodedWidth * decodedHeight
    }
}
