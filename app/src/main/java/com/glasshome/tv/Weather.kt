package com.glasshome.tv

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

class WeatherPlace(val name: String, val latitude: Double, val longitude: Double)

/** One day of the forecast: [weekday] is 0 for Sunday, as in date.weekdays; [rainChance] in percent. */
class DayForecast(val weekday: Int, val code: Int, val high: Int, val low: Int, val rainChance: Int)

class WeatherNow(
    val temperature: Int,
    val code: Int,
    val isDay: Boolean,
    /** Today's forecast high and low. */
    val todayHigh: Int,
    val todayLow: Int,
    /** The next seven days, tomorrow first. */
    val days: List<DayForecast>,
) {
    val description: String
        get() = Weather.describe(code)
}

/**
 * Current weather from Open-Meteo (free, no key). The place is kept in
 * SharedPreferences and chosen on the phone website; nothing is shown until then.
 * Network calls block: run them off the main thread.
 */
class Weather(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    /** The chosen place, or null before one is picked on the phone website. */
    fun place(): WeatherPlace? {
        val name = prefs.getString("name", null) ?: return null
        val latitude = prefs.getFloat("latitude", 0f).toDouble()
        val longitude = prefs.getFloat("longitude", 0f).toDouble()
        return WeatherPlace(name, latitude, longitude)
    }

    fun setPlace(place: WeatherPlace) {
        prefs.edit()
            .putString("name", place.name)
            .putFloat("latitude", place.latitude.toFloat())
            .putFloat("longitude", place.longitude.toFloat())
            .remove("cached_at")
            .apply()
    }

    /** The last reading if it is less than [MAX_CACHE_AGE_MS] old. */
    fun cached(): WeatherNow? {
        val cachedAt = prefs.getLong("cached_at", 0L)
        if (System.currentTimeMillis() - cachedAt > MAX_CACHE_AGE_MS) {
            return null
        }
        if (!prefs.contains("cached_code")) {
            return null
        }
        val days = parseDays(prefs.getString("cached_days", "[]") ?: "[]")
        return WeatherNow(
            prefs.getInt("cached_temperature", 0),
            prefs.getInt("cached_code", -1),
            prefs.getBoolean("cached_is_day", true),
            prefs.getInt("cached_today_high", 0),
            prefs.getInt("cached_today_low", 0),
            days,
        )
    }

    fun fetch(): WeatherNow? {
        val place = place() ?: return null
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=" + place.latitude +
            "&longitude=" + place.longitude +
            "&current=temperature_2m,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&forecast_days=8&timezone=auto"
        val body = get(url) ?: return null
        return try {
            val reply = JSONObject(body)
            val current = reply.getJSONObject("current")
            val temperature = current.getDouble("temperature_2m").roundToInt()
            val code = current.getInt("weather_code")
            val isDay = current.optInt("is_day", 1) == 1
            val daily = reply.optJSONObject("daily")
            val daysJson = daysFrom(daily)
            var todayHigh = temperature
            var todayLow = temperature
            if (daily != null) {
                todayHigh = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt()
                todayLow = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt()
            }
            prefs.edit()
                .putInt("cached_today_high", todayHigh)
                .putInt("cached_today_low", todayLow)
                .putLong("cached_at", System.currentTimeMillis())
                .putInt("cached_temperature", temperature)
                .putInt("cached_code", code)
                .putBoolean("cached_is_day", isDay)
                .putString("cached_days", daysJson)
                .apply()
            WeatherNow(temperature, code, isDay, todayHigh, todayLow, parseDays(daysJson))
        } catch (error: Exception) {
            Log.w(TAG, "Unexpected weather reply", error)
            null
        }
    }

    /** Open-Meteo's daily block -> [{weekday, code, high, low}] for the days after today. */
    private fun daysFrom(daily: JSONObject?): String {
        val result = org.json.JSONArray()
        if (daily == null) {
            return result.toString()
        }
        val dates = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val highs = daily.getJSONArray("temperature_2m_max")
        val lows = daily.getJSONArray("temperature_2m_min")
        val rainChances = daily.optJSONArray("precipitation_probability_max")
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
        val calendar = java.util.Calendar.getInstance()
        // Index 0 is today; the forecast shows the days after it.
        for (index in 1 until dates.length()) {
            val date = parser.parse(dates.getString(index)) ?: continue
            calendar.time = date
            result.put(JSONObject()
                .put("weekday", calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1)
                .put("code", codes.getInt(index))
                .put("high", highs.getDouble(index).roundToInt())
                .put("low", lows.getDouble(index).roundToInt())
                .put("rain", rainChances?.optInt(index, 0) ?: 0))
        }
        return result.toString()
    }

    private fun parseDays(json: String): List<DayForecast> {
        val days = ArrayList<DayForecast>()
        try {
            val array = org.json.JSONArray(json)
            for (index in 0 until array.length()) {
                val day = array.getJSONObject(index)
                days.add(DayForecast(day.getInt("weekday"), day.getInt("code"), day.getInt("high"), day.getInt("low"), day.optInt("rain", 0)))
            }
        } catch (broken: Exception) {
            // An old or damaged cache: no forecast until the next fetch.
        }
        return days
    }

    /** Places matching a name, e.g. "Porto" -> "Porto, Portugal". */
    fun search(query: String): List<WeatherPlace> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=6&language=en&format=json&name=" +
            URLEncoder.encode(query, "UTF-8")
        val body = get(url) ?: return emptyList()
        val result = ArrayList<WeatherPlace>()
        try {
            val matches = JSONObject(body).optJSONArray("results") ?: return result
            for (index in 0 until matches.length()) {
                val match = matches.getJSONObject(index)
                val parts = ArrayList<String>()
                parts.add(match.getString("name"))
                val region = match.optString("admin1", "")
                if (region.isNotEmpty() && region != match.getString("name")) {
                    parts.add(region)
                }
                val country = match.optString("country", "")
                if (country.isNotEmpty()) {
                    parts.add(country)
                }
                result.add(WeatherPlace(parts.joinToString(", "), match.getDouble("latitude"), match.getDouble("longitude")))
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unexpected geocoding reply", error)
        }
        return result
    }

    private fun get(address: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(address).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode != 200) {
                Log.w(TAG, "HTTP ${connection.responseCode} from $address")
                return null
            }
            connection.inputStream.bufferedReader().use { reader -> reader.readText() }
        } catch (error: Exception) {
            Log.w(TAG, "Request failed: $address", error)
            null
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        /** WMO weather codes as Open-Meteo reports them, in the interface language. */
        fun describe(code: Int): String {
            val key = when (code) {
                0, 1, 2, 3, 61, 63, 65, 71, 73, 75, 77, 80, 81, 82, 95 -> "weather.$code"
                45, 48 -> "weather.fog"
                51, 53, 55 -> "weather.drizzle"
                56, 57 -> "weather.freezing_drizzle"
                66, 67 -> "weather.freezing_rain"
                85, 86 -> "weather.snow_showers"
                96, 99 -> "weather.hail"
                else -> "weather.unknown"
            }
            return I18n.t(key)
        }

        private const val TAG = "GlassHome"
        private const val TIMEOUT_MS = 8_000
        private const val MAX_CACHE_AGE_MS = 3 * 3600_000L
    }
}
