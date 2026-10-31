package com.glasshome.tv

/** Picks the drawn weather icon for a WMO weather code, and its colour on the week panel. */
object WeatherIcons {

    /** Sun yellow, rain light blue, storms amber, the rest near white, as on the original Glass Home. */
    fun colorFor(code: Int, isDay: Boolean): Int {
        return when (code) {
            0, 1 -> if (isDay) 0xFFFFD43B.toInt() else 0xFFE9ECEF.toInt()
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> 0xFFA5D8FF.toInt()
            95, 96, 99 -> 0xFFFFE066.toInt()
            else -> 0xFFE9ECEF.toInt()
        }
    }

    fun forCode(code: Int, isDay: Boolean): Int {
        return when (code) {
            0, 1 -> if (isDay) R.drawable.weather_sun else R.drawable.weather_moon
            2 -> if (isDay) R.drawable.weather_partly else R.drawable.weather_cloud
            3 -> R.drawable.weather_cloud
            45, 48 -> R.drawable.weather_fog
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.weather_rain
            71, 73, 75, 77, 85, 86 -> R.drawable.weather_snow
            95, 96, 99 -> R.drawable.weather_storm
            else -> R.drawable.weather_cloud
        }
    }
}
