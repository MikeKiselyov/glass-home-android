package com.glasshome.tv

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

/**
 * Opens a link from the phone on the TV, in the app that handles it: YouTube
 * links in YouTube, other pages in a browser. Google TV has no browser of its
 * own, so without one the phone is told to install it (TV Bro, for example).
 *
 * The link has passed [SharedLinks] first. Glass Home may start the app while
 * another one is on screen: Android allows that for an app whose accessibility
 * service is on.
 */
class LinkOpener(private val context: Context) {

    enum class Result { OPENED, NO_APP }

    fun open(link: String): Result {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val apps = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (apps.isEmpty()) {
            return Result.NO_APP
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
    }
}
