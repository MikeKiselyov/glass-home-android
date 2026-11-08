package com.glasshome.tv

import android.content.Context
import android.provider.Settings

/**
 * Keeps Glass Home's clock setting (12 or 24 hours) and the TV's the same, so
 * Google's screensaver and other apps show the clock Glass Home shows.
 *
 * - A choice on the website goes to the TV ([chosen]).
 * - A change in the TV's own settings comes to Glass Home ([sync]): the TV
 *   then differs from what Glass Home last put there.
 * - "Auto" is the TV's own setting, which Glass Home follows and leaves alone.
 *
 * Writing needs "Modify system settings", given by scripts/install/allow-clock.sh;
 * without it only Glass Home's clock changes.
 */
object SystemClockFormat {

    /** The clock was chosen on the website: the TV gets it. */
    fun chosen(context: Context, settings: HomeSettings) {
        val clockFormat = settings.clockFormat
        if (isHours(clockFormat)) {
            putOnTv(context, settings, clockFormat)
        }
    }

    /** Home screen shown or settings read: takes a change made on the TV, or puts Glass Home's there. */
    fun sync(context: Context, settings: HomeSettings) {
        val clockFormat = settings.clockFormat
        if (!isHours(clockFormat)) {
            return
        }
        val onTv = Settings.System.getString(context.contentResolver, Settings.System.TIME_12_24)
        val putThere = settings.clockOnTv
        val changedOnTv = putThere != null && isHours(onTv) && onTv != putThere
        if (changedOnTv) {
            settings.clockFormat = onTv!!
            settings.clockOnTv = onTv
            return
        }
        if (onTv != clockFormat) {
            putOnTv(context, settings, clockFormat)
            return
        }
        if (putThere != clockFormat) {
            // Already the same: remembered, so a later change on the TV is noticed.
            settings.clockOnTv = clockFormat
        }
    }

    private fun putOnTv(context: Context, settings: HomeSettings, clockFormat: String) {
        if (!Settings.System.canWrite(context)) {
            return
        }
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, clockFormat)
        settings.clockOnTv = clockFormat
    }

    private fun isHours(value: String?): Boolean {
        return value == "12" || value == "24"
    }
}
