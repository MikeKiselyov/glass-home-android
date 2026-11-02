package com.glasshome.tv

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.tv.TvInputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * The home screen. Four themes share this logic and differ in layout and dock
 * style (res/layout/home_*.xml, DockTiles): clock, glass, postcard and type.
 * OK opens a tile, holding OK opens its menu.
 */
class MainActivity : Activity() {

    private lateinit var app: GlassHomeApp
    private lateinit var backdrop: Backdrop
    private lateinit var overlays: Overlays
    private lateinit var timeView: TextView
    private lateinit var dateView: TextView
    private lateinit var weatherView: TextView
    private lateinit var placeView: TextView
    private lateinit var dock: LinearLayout
    private lateinit var dockScroll: View
    private lateinit var tiles: DockTiles
    private var theme = "clock"
    private var language = "en"
    private var timeSizePx = 0f
    private var weatherCorner: View? = null
    private var weatherCornerTemperature: TextView? = null
    private var weatherCornerText: TextView? = null
    private var weatherCornerIcon: ImageView? = null
    private var weatherForecast: LinearLayout? = null
    private var weatherWeek: View? = null
    private var dockLabel: TextView? = null
    private lateinit var nightShade: View
    private lateinit var musicTint: View
    private lateinit var nowPlayingView: View
    private lateinit var nowPlayingArt: ImageView
    private lateinit var nowPlayingTitle: TextView
    private lateinit var nowPlayingArtist: TextView
    private lateinit var nowPlaying: NowPlaying
    private var tvInputManager: TvInputManager? = null

    private var items: List<DockItem> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private val weatherLoader = Background.executor("glasshome-weather")
    private val dockLoader = Background.executor("glasshome-dock")
    private var reportedFullyDrawn = false
    private val codeChanged: () -> Unit = { runOnUiThread { overlays.codeChanged() } }
    private var dockGeneration = 0

    /** What the weather views show now, so an unchanged forecast is not built again. */
    private data class ShownWeather(val now: WeatherNow, val style: String, val place: String, val language: String)
    private var shownWeather: ShownWeather? = null
    private val weatherTick = Runnable { refreshWeather() }
    private val photosChanged = Runnable { backdrop.reload(showNow = false) }
    private var focusedKey: String? = null

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            renderClock()
        }
    }

    /** HDMI devices switching on and off update the power dot on their tile. */
    private val inputCallback = object : TvInputManager.TvInputCallback() {
        override fun onInputStateChanged(inputId: String, state: Int) {
            rebuildDock()
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            rebuildDock()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as GlassHomeApp
        theme = app.settings.theme
        language = I18n.language
        setContentView(layoutFor(theme))
        val brand: TextView? = findViewById(R.id.brand)
        brand?.text = I18n.t("tv.brand")
        val typeHint: TextView? = findViewById(R.id.type_hint)
        typeHint?.text = I18n.t("tv.type_hint")

        timeView = findViewById(R.id.time)
        dateView = findViewById(R.id.date)
        weatherView = findViewById(R.id.weather)
        placeView = findViewById(R.id.place)
        dock = findViewById(R.id.dock)
        dockScroll = findViewById(R.id.dock_scroll)
        nightShade = findViewById(R.id.night_shade)
        musicTint = findViewById(R.id.music_tint)
        nowPlayingView = findViewById(R.id.now_playing)
        nowPlayingArt = findViewById(R.id.now_playing_art)
        nowPlayingArt.clipToOutline = true
        nowPlayingTitle = findViewById(R.id.now_playing_title)
        nowPlayingArtist = findViewById(R.id.now_playing_artist)
        weatherCorner = findViewById(R.id.weather_corner)
        weatherCornerTemperature = findViewById(R.id.weather_corner_temperature)
        weatherCornerText = findViewById(R.id.weather_corner_text)
        weatherCornerIcon = findViewById(R.id.weather_corner_icon)
        weatherForecast = findViewById(R.id.weather_forecast)
        weatherWeek = findViewById(R.id.weather_week)
        dockLabel = findViewById(R.id.dock_label)
        timeSizePx = timeView.textSize
        tiles = DockTiles(this, theme) { item ->
            focusedKey = item.key
            dockLabel?.text = item.label
        }
        nowPlaying = NowPlaying(this) { track -> runOnUiThread { showTrack(track) } }
        tvInputManager = getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager?

        val slideA = Slide(findViewById(R.id.slide_a), findViewById(R.id.blurred_a), findViewById(R.id.photo_a))
        val slideB = Slide(findViewById(R.id.slide_b), findViewById(R.id.blurred_b), findViewById(R.id.photo_b))
        Backdrop.prepareBlurredView(slideA.blurred)
        Backdrop.prepareBlurredView(slideB.blurred)
        backdrop = Backdrop(
            app.wallpapers,
            slideA,
            slideB,
            { app.settings.photoIntervalSeconds * 1000L },
            { app.settings.shufflePhotos },
            { app.settings.photoMotion },
        ) { wallpaper ->
            app.currentPhotoKey = wallpaper.key
            showCaption(wallpaper)
        }
        val ambient: ImageView? = findViewById(R.id.ambient)
        if (ambient != null) {
            Backdrop.prepareBlurredView(ambient)
            backdrop.ambient = ambient
        }
        applyFonts()

        overlays = Overlays(this, findViewById(R.id.overlay), app)
        overlays.onOpened = { dock.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS }
        overlays.onClosed = {
            dock.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            focusDock()
        }
        overlays.onWallpaperChanged = { backdrop.reload(showNow = true) }
        app.server.signInGuard.onCodeChanged = codeChanged
        overlays.onDockChanged = { rebuildDock() }

        // A batch of uploads arrives as many changes: refresh once, a moment after the last.
        app.server.onPhotosChanged = {
            runOnUiThread {
                handler.removeCallbacks(photosChanged)
                handler.postDelayed(photosChanged, PHOTOS_SETTLE_MS)
            }
        }
        app.server.onIconsChanged = {
            runOnUiThread { rebuildDock() }
        }
        app.server.onWeatherChanged = {
            runOnUiThread { refreshWeather() }
        }
        app.server.onPhotoModeChanged = {
            runOnUiThread { backdrop.reload(showNow = true) }
        }
        app.server.onSettingsChanged = {
            runOnUiThread {
                if (app.settings.theme != theme || I18n.language != language) {
                    // A new layout or language: build the screen again.
                    recreate()
                } else {
                    applyFonts()
                    renderClock()
                    rebuildDock()
                    refreshWeather()
                    backdrop.reload(showNow = false)
                }
            }
        }

        val packageFilter = IntentFilter()
        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED)
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED)
        packageFilter.addDataScheme("package")
        registerReceiver(packageReceiver, packageFilter)

        hideSystemBars()
    }

    override fun onStart() {
        super.onStart()
        val clockFilter = IntentFilter()
        clockFilter.addAction(Intent.ACTION_TIME_TICK)
        clockFilter.addAction(Intent.ACTION_TIME_CHANGED)
        clockFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        registerReceiver(clockReceiver, clockFilter)
        renderClock()
        // The dock and the weather from last time are still on screen: check
        // them without holding up the first frame, and redraw only what changed.
        refreshDockQuietly()
        backdrop.start()
        refreshWeather(redrawUnchanged = false)
        nowPlaying.start()
        tvInputManager?.registerCallback(inputCallback, handler)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        HomeScreenState.inFront = true
        app.updater.onNews = { askAboutUpdate() }
        handler.postDelayed(updateQuestion, UPDATE_QUESTION_DELAY_MS)
        // For scripts/measure/home-key.sh: the first frame after Glass Home came in front.
        Choreographer.getInstance().postFrameCallback {
            Log.i(HomeOverrideService.TIMING_TAG, "home shown " + SystemClock.uptimeMillis())
        }
    }

    override fun onPause() {
        HomeScreenState.inFront = false
        app.updater.onNews = null
        handler.removeCallbacks(updateQuestion)
        super.onPause()
    }

    private val updateQuestion = Runnable { askAboutUpdate() }

    /**
     * A downloaded update: ask on the home screen (never while a menu is open),
     * or show Android's own question for an install already said yes to.
     */
    private fun askAboutUpdate() {
        if (isFinishing || overlays.isOpen) {
            return
        }
        if (app.updater.showWaitingConfirmation(this)) {
            return
        }
        val version = app.updater.versionToOffer(System.currentTimeMillis()) ?: return
        val updater = app.updater
        overlays.openUpdateOffer(
            version,
            onInstall = { updater.install(reopenHome = true) },
            onLater = { updater.later(System.currentTimeMillis()) },
            onSkip = { updater.skip() },
        )
    }

    /** Another app is in front: drop the photos so the launcher holds almost no memory. */
    override fun onStop() {
        unregisterReceiver(clockReceiver)
        handler.removeCallbacks(weatherTick)
        nowPlaying.stop()
        tvInputManager?.unregisterCallback(inputCallback)
        backdrop.stop()
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        backdrop.shutdown()
        app.server.onPhotosChanged = null
        app.server.onIconsChanged = null
        app.server.onWeatherChanged = null
        app.server.onSettingsChanged = null
        // The sign-in guard lives as long as the app: don't let it keep this
        // screen. A newer home screen may already have set its own.
        if (app.server.signInGuard.onCodeChanged === codeChanged) {
            app.server.signInGuard.onCodeChanged = null
        }
        weatherLoader.shutdown()
        dockLoader.shutdown()
        super.onDestroy()
    }

    /**
     * Themes use different layouts where the same id can be a different view
     * class (the dock scrolls sideways in most themes, up and down in Postcard).
     * Restoring the old layout's state into the new one crashes, and a home
     * screen has nothing worth restoring, so view state is not kept.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.remove("android:viewHierarchyState")
    }

    /** Home pressed while already home: close any overlay and go back to the first tile. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        overlays.close()
        focusedKey = null
        dockScroll.scrollTo(0, 0)
        focusDock()
    }

    /**
     * Back closes an overlay; on the home screen itself it does nothing.
     * The red, green, yellow and blue buttons open the tile assigned to them.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val color = buttonColor(event.keyCode)
        if (color != null) {
            if (event.action == KeyEvent.ACTION_UP && !overlays.isOpen) {
                openButtonTarget(color)
            }
            return true
        }
        val isBack = event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE
        if (!isBack) {
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_UP && overlays.isOpen) {
            overlays.close()
        }
        return true
    }

    private fun buttonColor(keyCode: Int): String? {
        return when (keyCode) {
            KeyEvent.KEYCODE_PROG_RED -> "red"
            KeyEvent.KEYCODE_PROG_GREEN -> "green"
            KeyEvent.KEYCODE_PROG_YELLOW -> "yellow"
            KeyEvent.KEYCODE_PROG_BLUE -> "blue"
            else -> null
        }
    }

    private fun openButtonTarget(color: String) {
        val key = app.settings.buttonTarget(color)
        if (key == null) {
            val colorName = I18n.t("tv.color.$color")
            Toast.makeText(this, I18n.t("tv.button_empty", "color" to colorName), Toast.LENGTH_LONG).show()
            return
        }
        for (item in app.catalog.everyItem()) {
            if (item.key == key) {
                open(item)
                return
            }
        }
    }

    // ---------- theme and fonts ----------

    private fun layoutFor(theme: String): Int {
        return THEME_LAYOUTS[theme] ?: R.layout.home_clock
    }

    private fun applyFonts() {
        val font = app.settings.clockFont
        timeView.typeface = Fonts.clock(this, font)
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_PX, timeSizePx * Fonts.clockScale(font))
        val caption = Fonts.caption(this, font)
        dateView.typeface = caption
        weatherView.typeface = caption
        weatherCornerTemperature?.typeface = Fonts.clock(this, font)
        weatherCornerText?.typeface = caption
        if (theme == "postcard") {
            placeView.typeface = caption
        }
    }

    // ---------- clock and night mode ----------

    private fun renderClock() {
        val now = Calendar.getInstance()
        val use24Hours = when (app.settings.clockFormat) {
            "24" -> true
            "12" -> false
            else -> DateFormat.is24HourFormat(this)
        }
        val pattern = if (use24Hours) "H:mm" else "h:mm"
        setTextIfChanged(timeView, DateFormat.format(pattern, now).toString())

        setTextIfChanged(dateView, formatDate(now, app.settings.dateFormat).toString())

        applyNightMode(app.settings.isNight(now))
    }

    /** The interface is English everywhere, whatever the TV's language. */
    /** Day and month names come from the language file, so the date follows the interface language. */
    private fun formatDate(now: Calendar, format: String): String {
        val day = now.get(Calendar.DAY_OF_MONTH)
        val weekdayIndex = now.get(Calendar.DAY_OF_WEEK) - 1
        val monthIndex = now.get(Calendar.MONTH)
        when (format) {
            "words" -> return I18n.t(
                "date.words",
                "weekday" to I18n.list("date.weekdays")[weekdayIndex],
                "ordinal" to I18n.list("date.ordinals")[day - 1],
                "month" to I18n.list("date.months")[monthIndex],
            )
            "long" -> return I18n.t(
                "date.long",
                "weekday" to I18n.list("date.weekdays")[weekdayIndex],
                "day" to day,
                "month" to I18n.list("date.months")[monthIndex],
            )
            "short" -> return I18n.t(
                "date.short",
                "weekday" to I18n.list("date.weekdays_short")[weekdayIndex],
                "day" to day,
                "month" to I18n.list("date.months_short")[monthIndex],
            )
        }
        val pattern = when (format) {
            "dmy" -> "dd.MM.yyyy"
            "mdy" -> "MM/dd/yyyy"
            else -> "yyyy-MM-dd"
        }
        return SimpleDateFormat(pattern, Locale.ROOT).format(now.time)
    }

    /** Evenings: a warm veil over the photos and a dimmer clock. */
    private fun applyNightMode(isNight: Boolean) {
        val shadeAlpha = if (isNight) NIGHT_SHADE_ALPHA else 0f
        val textAlpha = if (isNight) NIGHT_TEXT_ALPHA else 1f
        if (nightShade.alpha != shadeAlpha) {
            nightShade.animate().alpha(shadeAlpha).setDuration(2_000).start()
        }
        timeView.alpha = textAlpha
        dateView.alpha = textAlpha
        weatherView.alpha = textAlpha
        weatherCorner?.alpha = textAlpha
        weatherWeek?.alpha = textAlpha
    }

    // ---------- now playing ----------

    private fun showTrack(track: Track?) {
        if (track == null) {
            nowPlayingView.visibility = View.GONE
            nowPlayingArt.setImageDrawable(null)
            musicTint.animate().alpha(0f).setDuration(1_200).start()
            return
        }
        nowPlayingTitle.text = track.title
        nowPlayingArtist.text = track.artist
        val art = track.art
        if (art != null) {
            nowPlayingArt.setImageBitmap(art)
            nowPlayingArt.visibility = View.VISIBLE
            musicTint.setBackgroundColor(averageColor(art))
            musicTint.animate().alpha(MUSIC_TINT_ALPHA).setDuration(1_200).start()
        } else {
            nowPlayingArt.visibility = View.GONE
            musicTint.animate().alpha(0f).setDuration(1_200).start()
        }
        nowPlayingView.visibility = View.VISIBLE
    }

    /** The album art's average colour, darkened so white text stays readable. */
    private fun averageColor(art: Bitmap): Int {
        val onePixel = Bitmap.createScaledBitmap(art, 1, 1, true)
        val color = onePixel.getPixel(0, 0)
        if (onePixel !== art) {
            onePixel.recycle()
        }
        val red = (color shr 16 and 0xFF) / 2
        val green = (color shr 8 and 0xFF) / 2
        val blue = (color and 0xFF) / 2
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }

    // ---------- weather ----------

    /**
     * Shows the cached weather at once, then fetches fresh weather in the
     * background; repeats every 30 minutes. With [redrawUnchanged] false,
     * weather already on screen is left alone.
     */
    private fun refreshWeather(redrawUnchanged: Boolean = true) {
        // A change from the phone can arrive just after this screen was closed.
        if (isDestroyed) {
            return
        }
        if (redrawUnchanged) {
            shownWeather = null
        }
        handler.removeCallbacks(weatherTick)
        val weather = app.weather
        if (!weather.enabled || weather.place() == null) {
            shownWeather = null
            weatherView.visibility = View.GONE
            weatherCorner?.visibility = View.GONE
            weatherWeek?.visibility = View.GONE
            showCaptionWithCredit()
            return
        }
        val cached = weather.cached()
        if (cached != null) {
            showWeather(cached)
        }
        // Back on the home screen (redrawUnchanged false) with a recent reading:
        // no network, the regular tick fetches the next one.
        if (!redrawUnchanged && cached != null && weather.isFresh()) {
            val wait = Weather.nextFetchIn(weather.cachedAt(), System.currentTimeMillis(), WEATHER_EVERY_MS)
            handler.postDelayed(weatherTick, wait)
            return
        }
        weatherLoader.execute {
            val fresh = weather.fetch()
            runOnUiThread {
                if (fresh != null && weather.enabled) {
                    showWeather(fresh)
                }
            }
        }
        handler.postDelayed(weatherTick, WEATHER_EVERY_MS)
    }

    /**
     * Weather styles: next to the date as text ("line"), icon and temperature
     * ("compact") or temperature only ("minimal"); large in the top right corner
     * ("corner"), with three days ahead ("forecast"). Themes without room in the
     * corner (Glass, Postcard) show the corner styles as "line".
     */
    private fun showWeather(now: WeatherNow) {
        val place = app.weather.place() ?: return
        val shown = ShownWeather(now, app.settings.weatherStyle, place.name, I18n.language)
        if (shown == shownWeather) {
            return
        }
        shownWeather = shown
        // "Porto, Portugal" -> "Porto"
        val city = place.name.substringBefore(",")
        val spoken = I18n.t("weather.line", "temperature" to now.temperature, "description" to now.description, "city" to city) +
            " " + I18n.t("weather.spoken_credit")
        val iconResource = WeatherIcons.forCode(now.code, now.isDay)

        var style = app.settings.weatherStyle
        val week = weatherWeek
        if (style == "week" && week != null) {
            showWeek(now, place.name, iconResource)
            week.contentDescription = spoken
            week.visibility = View.VISIBLE
            weatherCorner?.visibility = View.GONE
            weatherView.visibility = View.GONE
            showCaptionWithCredit()
            return
        }
        week?.visibility = View.GONE
        if (style == "week") {
            style = "line"
        }

        val corner = weatherCorner
        val wantsCorner = style == "corner" || style == "forecast"
        if (wantsCorner && corner == null) {
            style = "line"
        }

        if (corner != null && (style == "corner" || style == "forecast")) {
            weatherCornerIcon?.setImageResource(iconResource)
            weatherCornerTemperature?.text = "${now.temperature}°"
            weatherCornerText?.text = I18n.t("weather.corner", "description" to now.description, "city" to city)
            if (style == "forecast") {
                showForecast(now.days)
            } else {
                weatherForecast?.visibility = View.GONE
            }
            corner.contentDescription = spoken
            corner.visibility = View.VISIBLE
            weatherView.visibility = View.GONE
            showCaptionWithCredit()
            return
        }

        corner?.visibility = View.GONE
        when (style) {
            "compact" -> {
                weatherView.text = "${now.temperature}°"
                setLeadingIcon(weatherView, iconResource)
            }
            "minimal" -> {
                weatherView.text = "${now.temperature}°"
                setLeadingIcon(weatherView, null)
            }
            else -> {
                weatherView.text = I18n.t("weather.line", "temperature" to now.temperature, "description" to now.description, "city" to city)
                setLeadingIcon(weatherView, null)
            }
        }
        weatherView.contentDescription = spoken
        weatherView.visibility = View.VISIBLE
        showCaptionWithCredit()
    }

    /** The "week" style: today's summary, then seven columns on the frosted panel. */
    private fun showWeek(now: WeatherNow, placeName: String, iconResource: Int) {
        val icon: ImageView = findViewById(R.id.weather_week_icon)
        icon.setImageResource(iconResource)
        icon.setColorFilter(WeatherIcons.colorFor(now.code, now.isDay))
        findViewById<TextView>(R.id.weather_week_temperature).text = "${now.temperature}°"
        findViewById<TextView>(R.id.weather_week_summary).text = I18n.t(
            "weather.summary",
            "description" to now.description.replaceFirstChar { letter -> letter.uppercase() },
            "high" to now.todayHigh,
            "low" to now.todayLow,
        )
        findViewById<TextView>(R.id.weather_week_place).text = placeName.substringBefore(",")
        findViewById<TextView>(R.id.weather_week_credit).text = I18n.t("weather.week_credit")

        val weekdays = I18n.list("date.weekdays_short")
        val days = ArrayList<WeekDay>()
        for (day in now.days.take(WEEK_DAYS)) {
            var rain = " "
            if (day.rainChance > 0) {
                rain = I18n.t("weather.rain_chance", "percent" to day.rainChance)
            }
            days.add(WeekDay(
                weekday = weekdays.getOrElse(day.weekday) { "" },
                iconResource = WeatherIcons.forCode(day.code, true),
                iconColor = WeatherIcons.colorFor(day.code, true),
                high = "${day.high}°",
                low = "${day.low}°",
                rain = rain,
            ))
        }
        findViewById<WeekForecastView>(R.id.weather_week_days).setDays(days)
    }

    /** An icon before the text, as tall as the text and in its colour. */
    private fun setLeadingIcon(view: TextView, resource: Int?) {
        if (resource == null) {
            view.setCompoundDrawablesRelative(null, null, null, null)
            return
        }
        val icon = getDrawable(resource)?.mutate() ?: return
        val size = (view.textSize * 1.05f).toInt()
        icon.setBounds(0, 0, size, size)
        icon.setTint(view.currentTextColor)
        view.setCompoundDrawablesRelative(icon, null, null, null)
        view.compoundDrawablePadding = dp(6)
    }

    /** Rows like "Sun  (icon)  21°  14°" for the next three days. */
    private fun showForecast(days: List<DayForecast>) {
        val list = weatherForecast ?: return
        list.removeAllViews()
        val weekdays = I18n.list("date.weekdays_short")
        val captionFont = Fonts.caption(this, app.settings.clockFont)
        for (day in days.take(FORECAST_DAYS)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL

            val name = forecastText(weekdays.getOrElse(day.weekday) { "" }, captionFont, 0xCCFFFFFF.toInt())
            row.addView(name, LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT))

            val icon = ImageView(this)
            icon.setImageResource(WeatherIcons.forCode(day.code, true))
            icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            val iconParams = LinearLayout.LayoutParams(dp(18), dp(18))
            iconParams.marginEnd = dp(10)
            row.addView(icon, iconParams)

            row.addView(forecastText("${day.high}°", captionFont, 0xFFFFFFFF.toInt()), LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT))
            row.addView(forecastText("${day.low}°", captionFont, 0x99FFFFFF.toInt()), LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.WRAP_CONTENT))
            list.addView(row)
        }
        list.visibility = if (list.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun forecastText(text: String, typeface: android.graphics.Typeface, color: Int): TextView {
        val view = TextView(this)
        view.text = text
        view.typeface = typeface
        view.setTextColor(color)
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        view.setShadowLayer(8f, 0f, 1f, 0x99000000.toInt())
        return view
    }

    private var caption = ""

    private fun showCaption(wallpaper: Wallpaper) {
        // "The Twelve Apostles, Great Ocean Road, Victoria" -> two lines.
        caption = wallpaper.caption.replaceFirst(", ", ",\n")
        showCaptionWithCredit()
    }

    /** The photo's place, plus the Open-Meteo credit (CC BY 4.0) while weather shows. */
    private fun showCaptionWithCredit() {
        // The week panel credits Open-Meteo itself.
        val weatherShown = weatherView.visibility == View.VISIBLE || weatherCorner?.visibility == View.VISIBLE
        if (!weatherShown) {
            placeView.text = caption
            return
        }
        if (caption.isEmpty()) {
            placeView.text = I18n.t("weather.credit")
        } else {
            placeView.text = caption + "\n" + I18n.t("weather.credit")
        }
    }

    // ---------- dock ----------

    private fun rebuildDock() {
        dockGeneration += 1
        showDock(app.catalog.dockItems())
    }

    /**
     * Reads the dock in the background and rebuilds the tiles only when an
     * app or an HDMI device came, went, was renamed or switched on or off.
     * Changes made through Glass Home itself rebuild straight away instead.
     */
    private fun refreshDockQuietly() {
        if (dock.childCount == 0) {
            // A new screen (the TV may close the home screen in the background):
            // show the dock from last time at once, then check it like below.
            // The very first start has no dock yet: the clock, the date and the
            // photo come first and the tiles a moment later, rather than the
            // whole screen waiting for every app's name and icon.
            val last = app.lastDock
            if (last != null) {
                showDock(last)
            }
        }
        dockGeneration += 1
        val generation = dockGeneration
        dockLoader.execute {
            val fresh = app.catalog.dockItems()
            runOnUiThread {
                if (generation == dockGeneration && !isDestroyed && !DockItems.sameTiles(fresh, items)) {
                    showDock(fresh)
                }
                if (!reportedFullyDrawn && !isDestroyed) {
                    // "Fully drawn" in the log: the start until the tiles are there too.
                    reportedFullyDrawn = true
                    reportFullyDrawn()
                }
            }
        }
    }

    private fun showDock(fresh: List<DockItem>) {
        items = fresh
        app.lastDock = fresh
        dock.removeAllViews()
        val wordFont = Fonts.clock(this, app.settings.clockFont)
        for (item in items) {
            val tile = tiles.make(item, wordFont)
            tile.setOnClickListener { open(item) }
            tile.setOnLongClickListener {
                overlays.openTileMenu(item, items.indexOf(item), items.size)
                true
            }
            dock.addView(tile)
        }
        focusDock()
    }

    private fun focusDock() {
        if (overlays.isOpen || dock.childCount == 0) {
            return
        }
        var target: View = dock.getChildAt(0)
        for (index in 0 until dock.childCount) {
            val child = dock.getChildAt(index)
            if (child.tag == focusedKey) {
                target = child
            }
        }
        target.requestFocus()
    }

    private fun open(item: DockItem) {
        val opened = app.catalog.open(item)
        if (!opened) {
            Toast.makeText(this, I18n.t("tv.could_not_open", "app" to item.label), Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- helpers ----------

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        val flags = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        // Setting the same flags again still lays the whole screen out again.
        if (window.decorView.systemUiVisibility != flags) {
            window.decorView.systemUiVisibility = flags
        }
    }

    /** A new text, even an equal one, makes a large serif TextView measure itself again. */
    private fun setTextIfChanged(view: TextView, text: String) {
        if (view.text.toString() != text) {
            view.text = text
        }
    }

    companion object {
        /** The update question waits for the home screen to settle after it comes in front. */
        private const val UPDATE_QUESTION_DELAY_MS = 1500L

        /** The layout of each theme in HomeSettings.THEMES. */
        val THEME_LAYOUTS = mapOf(
            "clock" to R.layout.home_clock,
            "glass" to R.layout.home_glass,
            "postcard" to R.layout.home_postcard,
            "type" to R.layout.home_type,
        )

        private const val WEATHER_EVERY_MS = 30 * 60_000L
        private const val PHOTOS_SETTLE_MS = 3_000L
        private const val FORECAST_DAYS = 3
        private const val WEEK_DAYS = 7
        private const val NIGHT_SHADE_ALPHA = 0.45f
        private const val NIGHT_TEXT_ALPHA = 0.7f
        private const val MUSIC_TINT_ALPHA = 0.45f

    }
}
