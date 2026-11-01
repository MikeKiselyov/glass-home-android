package com.glasshome.tv

import android.graphics.Bitmap

/**
 * Moves a decoded photo to graphics memory before it is shown. A normal bitmap
 * lives in the app's memory and is copied to the graphics side again to be
 * drawn: a full-screen photo is 8 MB twice, and two slides are on screen
 * during a fade. A HARDWARE bitmap is the drawn copy only.
 *
 * Everything that reads pixels (the face search, the blurred copies) must run
 * before this: a HARDWARE bitmap can be drawn, not read.
 */
object GpuBitmap {

    /** The graphics-memory copy, or [bitmap] itself when the device can't make one. */
    fun move(bitmap: Bitmap): Bitmap {
        return move(bitmap) { original -> original.copy(Bitmap.Config.HARDWARE, false) }
    }

    fun move(bitmap: Bitmap, copy: (Bitmap) -> Bitmap?): Bitmap {
        val moved = try {
            copy(bitmap)
        } catch (notSupported: RuntimeException) {
            null
        }
        if (moved == null || moved === bitmap) {
            return bitmap
        }
        bitmap.recycle()
        return moved
    }
}
