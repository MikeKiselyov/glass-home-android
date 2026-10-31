package com.glasshome.tv

/**
 * The slow zoom on a photo, as steps instead of a smooth animation.
 *
 * The photo grows by 3% over its whole turn (about 45 seconds). Drawn every
 * frame, that kept the TV redrawing the full screen 40-60 times a second
 * (about 20% CPU on a 4-core TV). Moving 0.3 pixel twice a second looks the
 * same and costs almost nothing.
 */
object SlowZoom {

    /** How often the zoom moves on. */
    const val STEP_MS = 500L

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
        return growth * STEP_MS / durationMs
    }
}
