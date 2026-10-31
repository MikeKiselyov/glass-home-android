package com.glasshome.tv

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.FaceDetector

/**
 * How a photo fills the 16:9 screen without cutting off what matters.
 *
 * - Landscape photos are cropped to fill the screen. When the photo has faces
 *   the crop keeps them in view (heads in the upper part of the screen);
 *   otherwise it sits slightly above centre, where subjects usually are.
 * - Portrait and square photos would lose too much, so they are shown whole
 *   over a blurred copy of themselves.
 */
class PhotoFraming(val fitWhole: Boolean, private val focusY: Float) {

    /** Matrix for an ImageView of [viewWidth] x [viewHeight] showing [bitmap] cropped. */
    fun cropMatrix(bitmap: Bitmap, viewWidth: Int, viewHeight: Int): Matrix {
        val scale = maxOf(viewWidth.toFloat() / bitmap.width, viewHeight.toFloat() / bitmap.height)
        val scaledWidth = bitmap.width * scale
        val scaledHeight = bitmap.height * scale

        val dx = (viewWidth - scaledWidth) / 2f
        // Put the focus point at FOCUS_ON_SCREEN of the screen height, but never
        // past the photo's own edges.
        var dy = viewHeight * FOCUS_ON_SCREEN - focusY * scaledHeight
        val lowest = viewHeight - scaledHeight
        if (dy < lowest) {
            dy = lowest
        }
        if (dy > 0f) {
            dy = 0f
        }

        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
        return matrix
    }

    companion object {
        /** Photos narrower than this (width / height) are shown whole. 4:3 is 1.33. */
        private const val FIT_BELOW_ASPECT = 1.25f
        /** Where the faces (or the default focus) end up, from the top of the screen. */
        private const val FOCUS_ON_SCREEN = 0.38f
        /** Without faces: a little above the middle of the photo. */
        private const val DEFAULT_FOCUS = 0.42f
        private const val DETECT_WIDTH = 480
        private const val MAX_FACES = 8

        fun of(bitmap: Bitmap): PhotoFraming {
            val aspect = bitmap.width.toFloat() / bitmap.height
            if (aspect < FIT_BELOW_ASPECT) {
                return PhotoFraming(true, 0.5f)
            }
            val faceY = facesCentreY(bitmap) ?: DEFAULT_FOCUS
            return PhotoFraming(false, faceY)
        }

        /**
         * Vertical centre of the faces as a share of the photo height, or null
         * when none are found. Runs Android's built-in detector on a small copy.
         */
        private fun facesCentreY(bitmap: Bitmap): Float? {
            // The detector needs RGB_565 and an even width.
            val width = DETECT_WIDTH
            val height = (bitmap.height.toFloat() * width / bitmap.width).toInt()
            if (height < 2) {
                return null
            }
            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
            val small = if (scaled.config == Bitmap.Config.RGB_565) scaled else scaled.copy(Bitmap.Config.RGB_565, false)
            if (small !== scaled) {
                scaled.recycle()
            }

            val faces = arrayOfNulls<FaceDetector.Face>(MAX_FACES)
            val found = try {
                FaceDetector(width, height, MAX_FACES).findFaces(small, faces)
            } catch (error: Exception) {
                0
            }
            small.recycle()
            if (found == 0) {
                return null
            }

            // Centre between the highest and the lowest face, so a group stays together.
            var top = Float.MAX_VALUE
            var bottom = 0f
            val midpoint = android.graphics.PointF()
            for (index in 0 until found) {
                val face = faces[index] ?: continue
                face.getMidPoint(midpoint)
                // The midpoint is between the eyes; the head spans about 1.5 eye distances up and 2 down.
                val headTop = midpoint.y - face.eyesDistance() * 1.5f
                val headBottom = midpoint.y + face.eyesDistance() * 2f
                if (headTop < top) {
                    top = headTop
                }
                if (headBottom > bottom) {
                    bottom = headBottom
                }
            }
            return ((top + bottom) / 2f) / height
        }
    }
}
