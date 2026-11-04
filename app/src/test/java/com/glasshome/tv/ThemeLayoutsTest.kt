package com.glasshome.tv

import android.app.Activity
import android.app.Application
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every theme's layout has the views the home screen looks up without a
 * fallback; a missing one would crash the home screen in that theme only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ThemeLayoutsTest {

    private val layouts = MainActivity.THEME_LAYOUTS

    /** Postcard has no room for it: its weather is a line under the date. */
    private val themesWithWeekPanel = setOf("clock", "glass", "type")

    private val required = mapOf(
        "time" to R.id.time,
        "date" to R.id.date,
        "weather" to R.id.weather,
        "place" to R.id.place,
        "dock" to R.id.dock,
        "dock_scroll" to R.id.dock_scroll,
        "night_shade" to R.id.night_shade,
        "music_tint" to R.id.music_tint,
        "now_playing" to R.id.now_playing,
        "now_playing_art" to R.id.now_playing_art,
        "now_playing_title" to R.id.now_playing_title,
        "now_playing_artist" to R.id.now_playing_artist,
        "overlay" to R.id.overlay,
        "vpn_badge" to R.id.vpn_badge,
        "slide_a" to R.id.slide_a,
        "slide_b" to R.id.slide_b,
        "blurred_a" to R.id.blurred_a,
        "blurred_b" to R.id.blurred_b,
        "photo_a" to R.id.photo_a,
        "photo_b" to R.id.photo_b,
    )

    /** When a theme has the week panel, it needs all of its parts. */
    private val weekParts = mapOf(
        "weather_week_icon" to R.id.weather_week_icon,
        "weather_week_temperature" to R.id.weather_week_temperature,
        "weather_week_summary" to R.id.weather_week_summary,
        "weather_week_place" to R.id.weather_week_place,
        "weather_week_credit" to R.id.weather_week_credit,
        "weather_week_days" to R.id.weather_week_days,
    )

    private fun inflate(layout: Int): View {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        return activity.layoutInflater.inflate(layout, null)
    }

    @Test
    fun everyThemeHasTheViewsTheHomeScreenNeeds() {
        for ((theme, layout) in layouts) {
            val root = inflate(layout)
            for ((name, id) in required) {
                assertNotNull("$theme is missing $name", root.findViewById<View>(id))
            }
        }
    }

    @Test
    fun slidesZoomWhileDrawing() {
        for ((theme, layout) in layouts) {
            val root = inflate(layout)
            assertTrue("$theme slide_a", root.findViewById<View>(R.id.slide_a) is ZoomFrame)
            assertTrue("$theme slide_b", root.findViewById<View>(R.id.slide_b) is ZoomFrame)
        }
    }

    @Test
    fun everyThemeHasALayout() {
        for (theme in HomeSettings.THEMES) {
            assertNotNull("no layout for $theme", layouts[theme])
        }
    }

    @Test
    fun weekPanelsAreComplete() {
        for ((theme, layout) in layouts) {
            val root = inflate(layout)
            val hasWeekPanel = root.findViewById<View>(R.id.weather_week) != null
            assertEquals(theme, themesWithWeekPanel.contains(theme), hasWeekPanel)
            if (!hasWeekPanel) {
                continue
            }
            for ((name, id) in weekParts) {
                assertNotNull("$theme week panel is missing $name", root.findViewById<View>(id))
            }
            assertTrue("$theme days view", root.findViewById<View>(R.id.weather_week_days) is WeekForecastView)
        }
    }

    /** The photos are decoration: hidden from TalkBack in every theme. */
    @Test
    fun photosAreHiddenFromAccessibility() {
        for ((theme, layout) in layouts) {
            val photos = inflate(layout).findViewById<View>(R.id.photos)
            assertNotNull("$theme photos", photos)
            assertTrue(theme, photos.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
        }
    }
}
