package com.glasshome.tv

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log

/**
 * Opens a link from the phone on the TV, in the app that handles it: YouTube
 * links in YouTube, other pages in a browser. Google TV has no browser of its
 * own, so without one the phone is told to install it (TV Bro, for example).
 *
 * The link has passed [SharedLinks] first. Glass Home may start the app while
 * another one is on screen only while its accessibility service is on
 * ([canStartApps]: that, or the home screen is in front).
 */
class LinkOpener(private val context: Context, private val canStartApps: () -> Boolean) {

    enum class Result { OPENED, NO_APP, SERVICE_OFF }

    fun open(link: String): Result {
        // "HTTPS://" matches no intent filter: schemes are compared as written.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link).normalizeScheme())
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val apps = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (apps.isEmpty()) {
            return Result.NO_APP
        }
        // With another app in front Android drops the start without a word,
        // unless the accessibility service is on.
        if (!canStartApps()) {
            return Result.SERVICE_OFF
        }
        try {
            context.startActivity(intent)
        } catch (gone: ActivityNotFoundException) {
            Log.w(TAG, "No app took the link", gone)
            return Result.NO_APP
        }
        return Result.OPENED
    }

    companion object {
        private const val TAG = "GlassHome"

        /** Glass Home's accessibility service is on in the TV's settings. */
        fun serviceOn(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val ours = ComponentName(context, HomeOverrideService::class.java)
            for (entry in enabled.split(':')) {
                if (ComponentName.unflattenFromString(entry) == ours) {
                    return true
                }
            }
            return false
        }
    }
}
