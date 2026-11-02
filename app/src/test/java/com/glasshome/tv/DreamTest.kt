package com.glasshome.tv

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.service.dreams.DreamService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Glass Home's screensaver: the same photos and clock, without the network. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DreamTest {

    /** Android only lists a screensaver that only it may bind to. */
    @Test
    fun theTvCanPickIt() {
        val context = RuntimeEnvironment.getApplication()
        val found = context.packageManager.queryIntentServices(Intent(DreamService.SERVICE_INTERFACE).setPackage(context.packageName), PackageManager.GET_META_DATA)
        assertEquals(1, found.size)
        val service = found[0].serviceInfo
        assertEquals(GlassHomeDream::class.java.name, service.name)
        assertEquals(android.Manifest.permission.BIND_DREAM_SERVICE, service.permission)
    }

    @Test
    fun photosGoRoundInOrder() {
        assertEquals(1, DreamPlan.nextPhoto(0, 3))
        assertEquals(0, DreamPlan.nextPhoto(2, 3))
        assertEquals(0, DreamPlan.nextPhoto(-1, 3))
        assertEquals(-1, DreamPlan.nextPhoto(0, 0))
    }

    /** The clock moves a little every minute, so it never burns into the screen. */
    @Test
    fun theClockMovesWithinItsCorner() {
        val seen = HashSet<Pair<Int, Int>>()
        for (minute in 0 until 60) {
            val offset = DreamPlan.clockOffset(minute, 40)
            assertTrue(offset.first in 0..40 && offset.second in -40..0)
            seen.add(offset)
        }
        assertTrue("only ${seen.size} places", seen.size >= 6)
        assertEquals(DreamPlan.clockOffset(3, 40), DreamPlan.clockOffset(3, 40))
    }

    @Test
    fun photosStayAtLeastFifteenSeconds() {
        assertEquals(15_000L, DreamPlan.photoMillis(5))
        assertEquals(60_000L, DreamPlan.photoMillis(60))
    }

    /** Every change is a crossfade: the incoming photo is on top, whichever view it is. */
    @Test
    fun theIncomingPhotoIsAlwaysOnTop() {
        val context = RuntimeEnvironment.getApplication()
        val photos = android.widget.FrameLayout(context)
        val first = android.widget.ImageView(context)
        val second = android.widget.ImageView(context)
        photos.addView(first)
        photos.addView(second)
        // First change: the view drawn last comes in.
        GlassHomeDream.putOnTop(second)
        assertTrue(photos.getChildAt(1) === second && second.alpha == 0f)
        // Second change: the other view, which lies underneath, comes in.
        GlassHomeDream.putOnTop(first)
        assertTrue(photos.getChildAt(1) === first && first.alpha == 0f)
    }
}
