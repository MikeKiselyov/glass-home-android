package com.glasshome.tv

import android.content.Context
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** With a city picked, every theme shows the weather in every style. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = GlassHomeApp::class, sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HomeWeatherTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun weatherCached() {
        val days = JSONArray()
        for (index in 0 until 8) {
            days.put(JSONObject().put("weekday", (index % 7) + 1).put("code", 3).put("high", 12).put("low", 4).put("rain", 10))
        }
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", true)
            .putString("name", "Grenoble, France")
            .putFloat("latitude", 45.19f)
            .putFloat("longitude", 5.72f)
            .putLong("cached_at", System.currentTimeMillis())
            .putInt("cached_temperature", 9)
            .putInt("cached_code", 3)
            .putBoolean("cached_is_day", true)
            .putInt("cached_today_high", 12)
            .putInt("cached_today_low", 4)
            .putString("cached_days", days.toString())
            .commit()
    }

    /** Visible, not see-through, with a size, and inside the screen. */
    private fun shown(view: View?): Boolean {
        if (view == null || !view.isShown || view.alpha < 0.1f) {
            return false
        }
        if (view.width == 0 || view.height == 0) {
            return false
        }
        val onScreen = android.graphics.Rect()
        return view.getGlobalVisibleRect(onScreen) && onScreen.width() > 0 && onScreen.height() > 0
    }

    @Test
    fun everyThemeShowsTheWeatherInEveryStyle() {
        weatherCached()
        val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val missing = ArrayList<String>()
        for (theme in HomeSettings.THEMES) {
            for (style in HomeSettings.WEATHER_STYLES) {
                settings.edit().putString("theme", theme).putString("weather_style", style).commit()
                val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
                shadowOf(context.mainLooper).idle()
                val activity = controller.get()
                val any = shown(activity.findViewById(R.id.weather)) ||
                    shown(activity.findViewById(R.id.weather_corner)) ||
                    shown(activity.findViewById(R.id.weather_week))
                if (!any) {
                    for (name in listOf("weather", "weather_corner", "weather_week")) {
                        val id = activity.resources.getIdentifier(name, "id", activity.packageName)
                        val view = activity.findViewById<View>(id) ?: continue
                        val box = android.graphics.Rect()
                        view.getGlobalVisibleRect(box)
                        println("$theme/$style $name visibility=${view.visibility} shown=${view.isShown} alpha=${view.alpha} size=${view.width}x${view.height} box=$box")
                    }
                    missing.add("$theme/$style")
                }
                controller.pause().stop().destroy()
            }
        }
        assertTrue("No weather on screen in: $missing", missing.isEmpty())
    }
}
