package com.glasshome.tv

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/**
 * Home screen settings changed from the phone website: photo interval, clock
 * format, night mode, colour buttons and the shared album link.
 */
class HomeSettings(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var photoIntervalSeconds: Int
        get() = prefs.getInt("photo_interval", 45)
        set(value) = prefs.edit().putInt("photo_interval", value).apply()

    /** "auto" (the TV's setting), "12" or "24"; 24 hours unless changed. */
    var clockFormat: String
        get() = prefs.getString("clock_format", DEFAULT_CLOCK_FORMAT) ?: DEFAULT_CLOCK_FORMAT
        set(value) = prefs.edit().putString("clock_format", value).apply()

    var nightEnabled: Boolean
        get() = prefs.getBoolean("night_enabled", true)
        set(value) = prefs.edit().putBoolean("night_enabled", value).apply()

    /** Hour the night mode starts, 0-23. */
    var nightFrom: Int
        get() = prefs.getInt("night_from", 22)
        set(value) = prefs.edit().putInt("night_from", value).apply()

    /** Hour the night mode ends, 0-23. */
    var nightTo: Int
        get() = prefs.getInt("night_to", 7)
        set(value) = prefs.edit().putInt("night_to", value).apply()

    /** false: photos follow the order set on the phone website. */
    var shufflePhotos: Boolean
        get() = prefs.getBoolean("shuffle_photos", true)
        set(value) = prefs.edit().putBoolean("shuffle_photos", value).apply()

    /** Home screen layout: one of [THEMES]. */
    var theme: String
        get() = valid(prefs.getString("theme", "clock"), THEMES, "clock")
        set(value) = prefs.edit().putString("theme", value).apply()

    /** Typeface for the clock and date: one of [FONTS]. */
    var clockFont: String
        get() = valid(prefs.getString("clock_font", "instrument"), FONTS, "instrument")
        set(value) = prefs.edit().putString("clock_font", value).apply()

    /**
     * How the weather is shown, one of [WEATHER_STYLES]: next to the date as
     * text ("line"), icon and temperature ("compact") or temperature only
     * ("minimal"); or large top right ("corner"), with three days ahead
     * ("forecast"), or a frosted panel with the next seven days ("week").
     */
    var weatherStyle: String
        get() {
            val saved = prefs.getString("weather_style", null)
            if (saved != null) {
                return valid(saved, WEATHER_STYLES, DEFAULT_WEATHER_STYLE)
            }
            // Settings from before weather styles: "corner" or "date".
            if (prefs.getString("weather_position", null) == "corner") {
                return "corner"
            }
            return DEFAULT_WEATHER_STYLE
        }
        set(value) = prefs.edit().putString("weather_style", value).apply()

    /** How the date is written: one of [DATE_FORMATS]; digits (31.10.2026) unless changed. */
    var dateFormat: String
        get() = valid(prefs.getString("date_format", DEFAULT_DATE_FORMAT), DATE_FORMATS, DEFAULT_DATE_FORMAT)
        set(value) = prefs.edit().putString("date_format", value).apply()

    /** Interface language: a file in assets/i18n (only "en" so far). */
    var language: String
        get() = prefs.getString("language", "en") ?: "en"
        set(value) = prefs.edit().putString("language", value).apply()

    /** What the Home button does on Google TV: one of [HomeButton.MODES]. */
    var homeButton: String
        get() = valid(prefs.getString("home_button", HomeButton.CATCH), HomeButton.MODES, HomeButton.CATCH)
        set(value) = prefs.edit().putString("home_button", value).apply()

    var albumLink: String
        get() = prefs.getString("album_link", "") ?: ""
        set(value) = prefs.edit().putString("album_link", value).apply()

    fun isNight(now: Calendar): Boolean {
        if (!nightEnabled) {
            return false
        }
        val hour = now.get(Calendar.HOUR_OF_DAY)
        if (nightFrom == nightTo) {
            return false
        }
        if (nightFrom < nightTo) {
            return hour >= nightFrom && hour < nightTo
        }
        // Wraps past midnight, e.g. 22:00 to 07:00.
        return hour >= nightFrom || hour < nightTo
    }

    /** The dock tile a colour button opens: "red", "green", "yellow" or "blue". */
    fun buttonTarget(color: String): String? {
        return prefs.getString("button_$color", null)
    }

    fun setButtonTarget(color: String, key: String?) {
        val editor = prefs.edit()
        if (key == null) {
            editor.remove("button_$color")
        } else {
            editor.putString("button_$color", key)
        }
        editor.apply()
    }

    private fun valid(value: String?, allowed: List<String>, fallback: String): String {
        if (value != null && allowed.contains(value)) {
            return value
        }
        return fallback
    }

    companion object {
        val BUTTON_COLORS = listOf("red", "green", "yellow", "blue")
        val THEMES = listOf("clock", "glass", "postcard", "type")
        val FONTS = listOf("instrument", "dmserif", "bebas", "spacemono", "modern")
        val WEATHER_STYLES = listOf("week", "line", "compact", "minimal", "corner", "forecast")
        val DATE_FORMATS = listOf("words", "long", "short", "dmy", "mdy", "iso")
        const val DEFAULT_CLOCK_FORMAT = "24"
        const val DEFAULT_DATE_FORMAT = "dmy"
        const val DEFAULT_WEATHER_STYLE = "week"
    }
}
