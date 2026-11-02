package com.glasshome.tv

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The bear on the icon and banner: its eyes and nose are cut out of the head
 * (evenOdd), so they show the screen behind, not a solid light face.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34])
class AppIconTest {

    private fun render(resource: Int, width: Int, height: Int): Bitmap {
        val drawable = RuntimeEnvironment.getApplication().getDrawable(resource)!!
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    @Test
    fun theIconsBearHasEyesCutOut() {
        // 1 viewport unit = 4 px.
        val icon = render(R.drawable.icon, 432, 432)
        val face = icon.getPixel(54 * 4, 42 * 4)
        val leftEye = icon.getPixel((49.5 * 4).toInt(), (46.9 * 4).toInt())
        val rightEye = icon.getPixel((61 * 4).toInt(), (46.9 * 4).toInt())
        val nose = icon.getPixel(54 * 4, (51.6 * 4).toInt())
        val beside = icon.getPixel(30 * 4, 60 * 4)
        assertEquals(0xFFC4F4EF.toInt(), face)
        assertNotEquals(face, leftEye)
        assertNotEquals(face, rightEye)
        assertNotEquals(face, nose)
        // A hole shows the screen behind it: the same colour as the screen next to the bear, give or take the gradient.
        assertColorNear(beside, leftEye)
    }

    @Test
    fun theBannerHasTheSameBear() {
        // Banner: 320x180, the icon's picture at 1.5x moved by (79, 5.25); 1 unit = 2 px.
        val banner = render(R.drawable.banner, 640, 360)
        val faceX = (79 + 54 * 1.5) * 2
        val faceY = (5.25 + 42 * 1.5) * 2
        val eyeX = (79 + 49.5 * 1.5) * 2
        val eyeY = (5.25 + 46.9 * 1.5) * 2
        val face = banner.getPixel(faceX.toInt(), faceY.toInt())
        assertEquals(0xFFC4F4EF.toInt(), face)
        assertNotEquals(face, banner.getPixel(eyeX.toInt(), eyeY.toInt()))
    }

    private fun assertColorNear(expected: Int, actual: Int) {
        val red = Math.abs(((expected shr 16) and 0xFF) - ((actual shr 16) and 0xFF))
        val green = Math.abs(((expected shr 8) and 0xFF) - ((actual shr 8) and 0xFF))
        val blue = Math.abs((expected and 0xFF) - (actual and 0xFF))
        if (red > 16 || green > 16 || blue > 16) {
            throw AssertionError("Expected a colour near #%08X, got #%08X".format(expected, actual))
        }
    }
}
