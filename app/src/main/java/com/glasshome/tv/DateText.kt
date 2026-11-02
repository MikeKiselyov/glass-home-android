package com.glasshome.tv

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * The date as the home screen and the screensaver show it, in one of
 * [HomeSettings.DATE_FORMATS]. Day and month names come from the language
 * file, so the date follows the interface language.
 */
object DateText {

    fun format(now: Calendar, format: String): String {
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
}
