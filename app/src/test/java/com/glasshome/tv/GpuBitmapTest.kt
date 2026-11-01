package com.glasshome.tv

import android.app.Application
import android.graphics.Bitmap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A shown photo keeps one copy, in graphics memory, and never goes missing. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class GpuBitmapTest {

    private fun photo(): Bitmap {
        return Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888)
    }

    @Test
    fun theOriginalIsFreedOnceTheCopyExists() {
        val original = photo()
        val copy = photo()
        val shown = GpuBitmap.move(original) { copy }
        assertSame(copy, shown)
        assertTrue(original.isRecycled)
    }

    /** Some TVs can't make one: the photo is shown from normal memory, as before. */
    @Test
    fun withoutACopyTheOriginalIsShown() {
        val original = photo()
        assertSame(original, GpuBitmap.move(original) { null })
        assertFalse(original.isRecycled)
        assertSame(original, GpuBitmap.move(original) { throw IllegalStateException("no hardware bitmaps") })
        assertFalse(original.isRecycled)
    }
}
