package com.glasshome.tv

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView

/** One full-screen photo: a blurred backdrop (for portrait photos) and the photo itself. */
class Slide(val frame: ZoomFrame, val blurred: ImageView, val photo: ImageView) {
    var photoBitmap: Bitmap? = null
    var blurredBitmap: Bitmap? = null

    fun clear() {
        photo.setImageDrawable(null)
        blurred.setImageDrawable(null)
        photoBitmap?.recycle()
        blurredBitmap?.recycle()
        photoBitmap = null
        blurredBitmap = null
    }
}

/**
 * Full-screen photos that crossfade every [intervalMs] with a slow zoom.
 * Two slides take turns being in front. Each photo is framed by [PhotoFraming]:
 * landscape photos are cropped around faces, portrait ones shown whole over a
 * blurred copy. Bitmaps are dropped in [stop] so the launcher holds almost no
 * image memory while another app is open.
 */
class Backdrop(
    private val store: WallpaperStore,
    private val first: Slide,
    private val second: Slide,
    private val intervalMs: () -> Long,
    private val shuffle: () -> Boolean,
    private val motion: () -> Boolean,
    private val onShow: (Wallpaper) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val loader = Background.executor("glasshome-photos")
    private var front: Slide = first
    private var playlist: List<Wallpaper> = emptyList()
    private var position = -1
    private var running = false
    private var generation = 0
    private var fadeAnimator: Animator? = null
    private var zoomFrame: ZoomFrame? = null
    private var zoomStartedAt = 0L
    private var zoomDurationMs = 0L
    private var zoomStepMs = SlowZoom.STEP_MS

    /** Where the faces are in each photo: found once, not again every time it comes round. */
    private val framings = FramingCache()

    private val zoomStep = object : Runnable {
        override fun run() {
            val frame = zoomFrame ?: return
            val elapsed = SystemClock.uptimeMillis() - zoomStartedAt
            val scale = SlowZoom.scaleAt(elapsed, zoomDurationMs, ZOOM)
            frame.zoom = scale
            if (elapsed < zoomDurationMs) {
                handler.postDelayed(this, zoomStepMs)
            }
        }
    }
    private var knownKeys: Set<String> = emptySet()

    /** Postcard theme: a full-screen view that shows a blurred copy of each photo. */
    var ambient: ImageView? = null
    private var ambientBitmap: Bitmap? = null

    private val advance = Runnable { showNext() }

    fun start() {
        running = true
        reload(showNow = true)
    }

    /**
     * Re-reads the photos. New ones (uploaded since the last read) come first.
     * With [showNow] the next photo appears at once; otherwise the current one
     * finishes its turn, so a batch of uploads doesn't restart the show each time.
     */
    fun reload(showNow: Boolean) {
        val all = store.playlist()
        val fresh = ArrayList<Wallpaper>()
        val rest = ArrayList<Wallpaper>()
        for (wallpaper in all) {
            if (knownKeys.isNotEmpty() && !knownKeys.contains(wallpaper.key)) {
                fresh.add(wallpaper)
            } else {
                rest.add(wallpaper)
            }
        }
        val keys = HashSet<String>()
        for (wallpaper in all) {
            keys.add(wallpaper.key)
        }
        knownKeys = keys
        if (shuffle()) {
            rest.shuffle()
        }
        playlist = fresh + rest
        position = -1

        if (!running) {
            return
        }
        if (showNow || front.photoBitmap == null) {
            handler.removeCallbacks(advance)
            showNext()
        }
    }

    fun stop() {
        running = false
        generation += 1
        handler.removeCallbacks(advance)
        cancelAnimations()
        first.clear()
        second.clear()
        ambient?.setImageDrawable(null)
        ambientBitmap?.recycle()
        ambientBitmap = null
    }

    fun shutdown() {
        stop()
        loader.shutdown()
    }

    private fun showNext() {
        if (playlist.isEmpty()) {
            return
        }
        position = (position + 1) % playlist.size
        val wallpaper = playlist[position]
        val requestGeneration = generation
        loader.execute {
            val bitmap = store.loadScreen(wallpaper)
            var framing: PhotoFraming? = null
            var blurred: Bitmap? = null
            var forAmbient: Bitmap? = null
            if (bitmap != null) {
                framing = framings.get(wallpaper.key)
                if (framing == null) {
                    framing = PhotoFraming.of(bitmap)
                    framings.put(wallpaper.key, framing)
                }
                if (framing.fitWhole) {
                    blurred = blurredCopy(bitmap)
                }
                if (ambient != null) {
                    forAmbient = blurredCopy(bitmap)
                }
            }
            // Last: from here on the photo can be drawn but not read.
            val shown = if (bitmap != null) GpuBitmap.move(bitmap) else null
            handler.post {
                if (requestGeneration != generation || !running) {
                    shown?.recycle()
                    blurred?.recycle()
                    forAmbient?.recycle()
                    return@post
                }
                if (shown == null || framing == null) {
                    // An unreadable file: skip it rather than stop the show.
                    handler.removeCallbacks(advance)
                    handler.postDelayed(advance, SKIP_MS)
                    return@post
                }
                crossfadeTo(shown, framing, blurred)
                showAmbient(forAmbient)
                onShow(wallpaper)
                handler.removeCallbacks(advance)
                if (playlist.size > 1) {
                    handler.postDelayed(advance, intervalMs())
                }
            }
        }
    }

    private fun crossfadeTo(bitmap: Bitmap, framing: PhotoFraming, blurred: Bitmap?) {
        val back = if (front === first) second else first
        val oldFront = front

        back.clear()
        back.photoBitmap = bitmap
        back.blurredBitmap = blurred
        if (framing.fitWhole) {
            back.photo.scaleType = ImageView.ScaleType.FIT_CENTER
            back.blurred.setImageBitmap(blurred)
            back.blurred.visibility = View.VISIBLE
        } else {
            back.photo.scaleType = ImageView.ScaleType.MATRIX
            applyCrop(back.photo, bitmap, framing)
            back.blurred.visibility = View.GONE
        }
        back.photo.setImageBitmap(bitmap)

        back.frame.alpha = 0f
        back.frame.zoom = 1f
        back.frame.bringToFront()

        cancelAnimations()

        val fade = ObjectAnimator.ofFloat(back.frame, View.ALPHA, 0f, 1f)
        fade.duration = FADE_MS
        fade.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // Two quick crossfades can make the old slide the front again: keep its photo then.
                if (oldFront === front) {
                    return
                }
                oldFront.clear()
            }
        })

        fadeAnimator = fade
        fade.start()

        // The slow zoom runs for the whole time the photo is on screen, in steps (see SlowZoom).
        if (motion()) {
            zoomFrame = back.frame
            zoomStartedAt = SystemClock.uptimeMillis()
            zoomDurationMs = intervalMs() + FADE_MS
            zoomStepMs = SlowZoom.stepMs(zoomDurationMs, ZOOM, back.frame.resources.displayMetrics.widthPixels)
            handler.post(zoomStep)
        } else {
            zoomFrame = null
        }

        front = back
    }

    /** The crop needs the view's size; before the first layout, wait for it. */
    private fun applyCrop(view: ImageView, bitmap: Bitmap, framing: PhotoFraming) {
        if (view.width > 0 && view.height > 0) {
            view.imageMatrix = framing.cropMatrix(bitmap, view.width, view.height)
            return
        }
        view.post {
            if (!bitmap.isRecycled && view.width > 0) {
                view.imageMatrix = framing.cropMatrix(bitmap, view.width, view.height)
            }
        }
    }

    private fun showAmbient(bitmap: Bitmap?) {
        val view = ambient ?: return
        val old = ambientBitmap
        view.setImageBitmap(bitmap)
        ambientBitmap = bitmap
        old?.recycle()
    }

    /**
     * A soft copy for the sides of portrait photos. Android 12+ blurs it with
     * RenderEffect when drawn; older versions get a tiny copy that the screen
     * stretches, which looks blurred too.
     */
    private fun blurredCopy(bitmap: Bitmap): Bitmap {
        val width = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 320 else 32
        val height = maxOf(1, bitmap.height * width / bitmap.width)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun cancelAnimations() {
        fadeAnimator?.cancel()
        fadeAnimator = null
        handler.removeCallbacks(zoomStep)
        zoomFrame = null
    }

    companion object {
        private const val FADE_MS = 1_600L
        private const val SKIP_MS = 1_000L

        /** A gentle zoom: a stronger one softens the photo on a 1080p screen. */
        private const val ZOOM = 1.03f

        /** Sets up the blur and dimming on a slide's backdrop view once. */
        fun prepareBlurredView(view: ImageView) {
            view.scaleType = ImageView.ScaleType.CENTER_CROP
            view.setColorFilter(0x66000000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                view.setRenderEffect(RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP))
            }
        }
    }
}
