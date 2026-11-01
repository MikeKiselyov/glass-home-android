package com.glasshome.tv

/**
 * The slow zoom on a photo, as steps instead of a smooth animation.
 *
 * The photo grows by 3% over its whole turn (about 45 seconds). Drawn every
 * frame, that kept the TV redrawing the full screen 40-60 times a second
 * (about 20% CPU on a 4-core TV). Moving 0.3 pixel twice a second looks the
 * same and costs almost nothing.
 *
 * The step is as long as it can be while the edge still moves at most half a
 * pixel: a photo shown for 45 seconds steps every 0.8 s, one shown for ten
 * minutes every 2 s. The viewer can also turn the zoom off (still photos).
 */
object SlowZoom {

    /** The shortest and longest step. */
    const val STEP_MS = 500L
    const val LONGEST_STEP_MS = 2_000L

    /** Half a pixel per step at most: steps smaller than that can't be seen either way. */
    private const val MOST_PIXELS_PER_STEP = 0.5f

    /** How long to wait between steps for a zoom over [durationMs] on a screen [widthPx] wide. */
    fun stepMs(durationMs: Long, zoom: Float, widthPx: Int): Long {
        val growth = (zoom - 1f) * widthPx / 2f
        if (growth <= 0f || durationMs <= 0) {
            return LONGEST_STEP_MS
        }
        val step = (durationMs * MOST_PIXELS_PER_STEP / growth).toLong()
        if (step < STEP_MS) {
            return STEP_MS
        }
        if (step > LONGEST_STEP_MS) {
            return LONGEST_STEP_MS
        }
        return step
    }

    /** The scale [elapsedMs] into a zoom from 1 to [zoom] over [durationMs]. */
    fun scaleAt(elapsedMs: Long, durationMs: Long, zoom: Float): Float {
        if (durationMs <= 0 || elapsedMs >= durationMs) {
            return zoom
        }
        if (elapsedMs <= 0) {
            return 1f
        }
        val progress = elapsedMs.toFloat() / durationMs.toFloat()
        return 1f + (zoom - 1f) * progress
    }

    /** How far the photo's edge moves in one step, in pixels, on a screen [widthPx] wide. */
    fun pixelsPerStep(durationMs: Long, zoom: Float, widthPx: Int): Float {
        val growth = (zoom - 1f) * widthPx / 2f
        return growth * stepMs(durationMs, zoom, widthPx) / durationMs
    }
}
