package com.glasshome.tv

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * A slide that can be drawn slightly larger, for the slow zoom on photos.
 *
 * The zoom is applied while drawing, not with scaleX / scaleY: changing those
 * tells accessibility services the screen changed, twice a second for the
 * whole time a photo is shown. TalkBack users got a stream of updates and
 * tools that wait for a quiet screen (uiautomator) never got one.
 */
class ZoomFrame @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
) : FrameLayout(context, attributes) {

    var zoom = 1f
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidate()
        }

    /**
     * The photos fade in by alpha every interval. With overlapping children the
     * whole full-screen slide is first drawn into an off-screen layer each
     * frame of the fade; with only the photo visible (most photos, which fill
     * the screen) it is simply drawn see-through, which looks the same.
     */
    override fun hasOverlappingRendering(): Boolean {
        var visible = 0
        for (index in 0 until childCount) {
            if (getChildAt(index).visibility == VISIBLE) {
                visible += 1
            }
        }
        return visible > 1
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (zoom == 1f) {
            super.dispatchDraw(canvas)
            return
        }
        val saved = canvas.save()
        canvas.scale(zoom, zoom, width / 2f, height / 2f)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saved)
    }
}
