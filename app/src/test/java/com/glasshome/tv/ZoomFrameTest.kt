package com.glasshome.tv

import android.app.Application
import android.view.View
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A fading photo is drawn without an off-screen layer when nothing lies under it. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ZoomFrameTest {

    @Test
    fun aPhotoAloneNeedsNoLayer() {
        val context = RuntimeEnvironment.getApplication()
        val frame = ZoomFrame(context)
        val blurred = View(context)
        val photo = View(context)
        frame.addView(blurred)
        frame.addView(photo)
        blurred.visibility = View.GONE
        assertFalse(frame.hasOverlappingRendering())
    }

    /** A portrait photo over its blurred copy: the copy must not show through while fading. */
    @Test
    fun aPhotoOverItsBlurredCopyKeepsTheLayer() {
        val context = RuntimeEnvironment.getApplication()
        val frame = ZoomFrame(context)
        frame.addView(View(context))
        frame.addView(View(context))
        assertTrue(frame.hasOverlappingRendering())
    }
}
