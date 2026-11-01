package com.glasshome.tv

import android.app.Activity
import android.app.Application
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class WeekForecastViewTest {

    private fun week(count: Int): List<WeekDay> {
        val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val days = ArrayList<WeekDay>()
        for (index in 0 until count) {
            days.add(WeekDay(names[index], R.drawable.weather_sun, 0xFFFFD43B.toInt(), "23°", "12°", if (index == 5) "56%" else " "))
        }
        return days
    }

    private fun measured(days: List<WeekDay>): WeekForecastView {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = WeekForecastView(activity)
        view.setDays(days)
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(unspecified, unspecified)
        return view
    }

    /** Seven columns of 46 dp at density 2, as the TextView columns were. */
    @Test
    fun sevenDaysAreSevenColumnsWide() {
        assertEquals(7 * 46 * 2, measured(week(7)).measuredWidth)
    }

    @Test
    fun hasRoomForFiveLines() {
        val view = measured(week(7))
        // Weekday, icon (22 dp + 3 dp above and below), high, low and rain:
        // about 96 dp, the height the TextView columns had on the TV.
        assertTrue("height " + view.measuredHeight, view.measuredHeight in 170..220)
    }

    @Test
    fun noDaysTakeNoWidth() {
        assertEquals(0, measured(emptyList()).measuredWidth)
    }

    @Test
    fun readsTheWeekToTalkBack() {
        val view = measured(week(2))
        assertEquals("Sun 23° 12°, Mon 23° 12°", view.contentDescription)
    }
}
