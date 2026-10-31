package com.glasshome.tv

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * Exists only so Android lets Glass Home read media sessions (what Spotify or
 * YouTube Music is playing). Enable it once:
 *   adb shell cmd notification allow_listener com.glasshome.tv/com.glasshome.tv.MediaListener
 * Notifications themselves are ignored.
 */
class MediaListener : NotificationListenerService()

class Track(val title: String, val artist: String, val art: Bitmap?)

/**
 * Watches the active media sessions and reports the playing track, or null
 * when nothing plays. Call [start] and [stop] with the activity's lifecycle.
 */
class NowPlaying(private val context: Context, private val onChange: (Track?) -> Unit) {

    private val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val listenerComponent = ComponentName(context, MediaListener::class.java)
    private val watched = ArrayList<MediaController>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        watch(controllers ?: emptyList())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            report()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            report()
        }
    }

    /** False until the listener permission is granted (see [MediaListener]). */
    fun start(): Boolean {
        return try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
            watch(sessionManager.getActiveSessions(listenerComponent))
            true
        } catch (notAllowed: SecurityException) {
            Log.i(TAG, "Now playing needs the notification listener permission")
            onChange(null)
            false
        }
    }

    fun stop() {
        try {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (notAllowed: SecurityException) {
            // Was never added.
        }
        unwatchAll()
    }

    private fun watch(controllers: List<MediaController>) {
        unwatchAll()
        for (controller in controllers) {
            controller.registerCallback(controllerCallback)
            watched.add(controller)
        }
        report()
    }

    private fun unwatchAll() {
        for (controller in watched) {
            controller.unregisterCallback(controllerCallback)
        }
        watched.clear()
    }

    private fun report() {
        for (controller in watched) {
            val state = controller.playbackState?.state
            if (state != PlaybackState.STATE_PLAYING) {
                continue
            }
            val metadata = controller.metadata ?: continue
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: continue
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: ""
            val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            onChange(Track(title, artist, art))
            return
        }
        onChange(null)
    }

    companion object {
        private const val TAG = "GlassHome"
    }
}
