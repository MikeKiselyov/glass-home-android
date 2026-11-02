package com.glasshome.tv

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.text.format.DateFormat
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import java.util.concurrent.ExecutorService

/**
 * Glass Home's screensaver: the home screen's photos, one after another, and
 * the clock and date in the corner. Nothing from the network, unlike Google's.
 * Turned on with scripts/setup.sh --screensaver glasshome.
 *
 * Two photos in memory at most (about 8 MB each), loaded off the UI thread.
 * The clock moves a little every minute against burn-in.
 */
class GlassHomeDream : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private var loader: ExecutorService? = null
    private lateinit var settings: HomeSettings
    private lateinit var store: WallpaperStore
    private lateinit var shown: ImageView
    private lateinit var hidden: ImageView
    private lateinit var clock: LinearLayout
    private lateinit var timeView: TextView
    private lateinit var dateView: TextView
    private var photos: List<Wallpaper> = emptyList()
    private var photoIndex = -1
    private var shownBitmap: Bitmap? = null

    private val clockTick = Runnable { showClock() }
    private val photoTick = Runnable { showNextPhoto() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(R.layout.dream)
        shown = findViewById(R.id.dream_photo_a)
        hidden = findViewById(R.id.dream_photo_b)
        clock = findViewById(R.id.dream_clock)
        timeView = findViewById(R.id.dream_time)
        dateView = findViewById(R.id.dream_date)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        settings = HomeSettings(this)
        store = WallpaperStore(this)
        // Day and month names for the date.
        I18n.load(this, settings.language)
        timeView.typeface = Fonts.clock(this, settings.clockFont)
        dateView.typeface = Fonts.caption(this, settings.clockFont)
        loader = Background.executor("glasshome-dream")
        photos = store.playlist()
        showClock()
        showNextPhoto()
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(photoTick)
        loader?.shutdownNow()
        loader = null
        shown.setImageDrawable(null)
        hidden.setImageDrawable(null)
        shownBitmap?.recycle()
        shownBitmap = null
        super.onDreamingStopped()
    }

    private fun showClock() {
        val now = Calendar.getInstance()
        val use24Hours = when (settings.clockFormat) {
            "24" -> true
            "12" -> false
            else -> DateFormat.is24HourFormat(this)
        }
        val pattern = if (use24Hours) "H:mm" else "h:mm"
        timeView.text = DateFormat.format(pattern, now).toString()
        dateView.text = DateText.format(now, settings.dateFormat)
        val range = (CLOCK_DRIFT_DP * resources.displayMetrics.density).toInt()
        val offset = DreamPlan.clockOffset(now.get(Calendar.MINUTE), range)
        clock.translationX = offset.first.toFloat()
        clock.translationY = offset.second.toFloat()
        // Again at the start of the next minute.
        val wait = 60_000L - (System.currentTimeMillis() % 60_000L)
        handler.postDelayed(clockTick, wait)
    }

    private fun showNextPhoto() {
        val next = DreamPlan.nextPhoto(photoIndex, photos.size)
        if (next < 0) {
            return
        }
        photoIndex = next
        val wallpaper = photos[next]
        val running = loader ?: return
        running.execute {
            val bitmap = store.loadScreen(wallpaper)
            handler.post { crossfadeTo(bitmap) }
        }
    }

    private fun crossfadeTo(bitmap: Bitmap?) {
        if (loader == null) {
            // Stopped while the photo loaded.
            bitmap?.recycle()
            return
        }
        if (bitmap != null) {
            val old = shownBitmap
            hidden.setImageBitmap(bitmap)
            putOnTop(hidden)
            hidden.animate().alpha(1f).setDuration(FADE_MS).withEndAction {
                shown.setImageDrawable(null)
                shown.alpha = 0f
                old?.recycle()
                val front = hidden
                hidden = shown
                shown = front
            }.start()
            shownBitmap = bitmap
        }
        handler.postDelayed(photoTick, DreamPlan.photoMillis(settings.photoIntervalSeconds))
    }

    companion object {
        private const val FADE_MS = 1_500L

        /**
         * The incoming photo, see-through, above the other one before it fades
         * in. Found in review: after the views swapped, the new photo was under
         * the old one and every second change was a cut.
         */
        fun putOnTop(incoming: ImageView) {
            incoming.alpha = 0f
            incoming.bringToFront()
        }
        private const val CLOCK_DRIFT_DP = 24f
    }
}
