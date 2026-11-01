package com.glasshome.tv

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class HomeSettingsTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun aNewTvShowsA24HourClockDigitDatesAndTheWeekPanel() {
        val settings = HomeSettings(context)
        assertEquals("24", settings.clockFormat)
        assertEquals("dmy", settings.dateFormat)
        assertEquals("week", settings.weatherStyle)
        assertEquals("clock", settings.theme)
    }

    @Test
    fun homeIsCaughtBeforeGoogleTvUnlessChanged() {
        val settings = HomeSettings(context)
        assertEquals("catch", settings.homeButton)
        settings.homeButton = "after"
        assertEquals("after", HomeSettings(context).homeButton)
        settings.homeButton = "nonsense"
        assertEquals("catch", HomeSettings(context).homeButton)
    }

    @Test
    fun aChoiceMadeOnTheWebsiteIsKept() {
        val settings = HomeSettings(context)
        settings.clockFormat = "12"
        settings.dateFormat = "words"
        settings.weatherStyle = "line"

        val again = HomeSettings(context)
        assertEquals("12", again.clockFormat)
        assertEquals("words", again.dateFormat)
        assertEquals("line", again.weatherStyle)
    }

    @Test
    fun unknownSavedValuesFallBackToTheDefaults() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("date_format", "nonsense")
            .putString("weather_style", "nonsense")
            .putString("theme", "nonsense")
            .commit()
        val settings = HomeSettings(context)
        assertEquals("dmy", settings.dateFormat)
        assertEquals("week", settings.weatherStyle)
        assertEquals("clock", settings.theme)
    }
}
