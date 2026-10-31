package com.glasshome.tv

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.util.concurrent.Executors

/** Shared state for the launcher process; the photo website runs as long as the process does. */
class GlassHomeApp : Application() {

    lateinit var wallpapers: WallpaperStore
        private set
    lateinit var icons: IconStore
        private set
    lateinit var catalog: AppCatalog
        private set
    lateinit var server: PhotoServer
        private set
    lateinit var weather: Weather
        private set
    lateinit var settings: HomeSettings
        private set

    /** Photos in the shared album after the last sync; -1 if the link failed, null before any sync. */
    @Volatile
    var albumCount: Int? = null
        private set

    private val albumExecutor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val albumTick = Runnable { syncAlbum() }

    override fun onCreate() {
        super.onCreate()
        wallpapers = WallpaperStore(this)
        icons = IconStore(this)
        catalog = AppCatalog(this, icons)
        weather = Weather(this)
        settings = HomeSettings(this)
        I18n.load(this, settings.language)
        server = PhotoServer(this, wallpapers, icons, weather, settings, catalog)
        try {
            server.start(SOCKET_TIMEOUT_MS, true)
        } catch (error: IOException) {
            Log.w("GlassHome", "Photo website could not start on port ${PhotoServer.PORT}", error)
        }
        LocalName(this).start()
        server.onAlbumChanged = { syncAlbum() }
        server.albumCount = { albumCount }
        syncAlbum()
    }

    /** Downloads new photos from the shared album now, then again every 6 hours. */
    fun syncAlbum() {
        handler.removeCallbacks(albumTick)
        val link = settings.albumLink
        albumExecutor.execute {
            albumCount = AlbumSync(wallpapers, cacheDir).sync(link)
            server.onPhotosChanged?.invoke()
        }
        if (link.isNotBlank()) {
            handler.postDelayed(albumTick, ALBUM_EVERY_MS)
        }
    }

    companion object {
        private const val SOCKET_TIMEOUT_MS = 30_000
        private const val ALBUM_EVERY_MS = 6 * 3600_000L
    }
}
