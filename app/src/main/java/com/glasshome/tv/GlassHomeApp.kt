package com.glasshome.tv

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException

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
    lateinit var updater: Updater
        private set

    /**
     * The dock as the home screen last showed it. The home screen can be closed
     * and built again on every Home press; this lets it show the dock at once.
     */
    @Volatile
    var lastDock: List<DockItem>? = null

    /** The photo on the screen now; the website's live preview shows it. */
    @Volatile
    var currentPhotoKey: String? = null

    /** Photos in the shared album after the last sync; -1 if the link failed, null before any sync. */
    @Volatile
    var albumCount: Int? = null
        private set

    private val albumExecutor = Background.executor("glasshome-album")
    private val handler = Handler(Looper.getMainLooper())
    private val albumTick = Runnable { syncAlbum() }
    private val updateTick = object : Runnable {
        override fun run() {
            updater.checkIfDue()
            handler.postDelayed(this, UPDATE_TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (isSettingsProcess(currentProcessName())) {
            // Settings on the TV: only a WebView. The website, the photos and the
            // rest belong to the home screen's process.
            return
        }
        wallpapers = WallpaperStore(this)
        icons = IconStore(this)
        catalog = AppCatalog(this, icons)
        weather = Weather(this)
        settings = HomeSettings(this)
        updater = Updater(this)
        updater.restore()
        I18n.load(this, settings.language)
        server = PhotoServer(this, wallpapers, icons, weather, settings, catalog)
        server.onAlbumChanged = { syncAlbum() }
        server.albumCount = { albumCount }
        server.currentPhoto = { currentPhotoKey }
        server.updater = updater
        val linkOpener = LinkOpener(this)
        server.openLink = { link -> linkOpener.open(link) }
        // Off the UI thread: NanoHTTPD's start waits for its socket, and nothing
        // on the first frame needs the website or the album.
        albumExecutor.execute {
            try {
                server.start(SOCKET_TIMEOUT_MS, true)
            } catch (error: IOException) {
                Log.w("GlassHome", "Photo website could not start on port ${PhotoServer.PORT}", error)
            }
            LocalName(this).start()
        }
        handler.postDelayed(albumTick, ALBUM_FIRST_MS)
        handler.postDelayed(updateTick, UPDATE_FIRST_MS)
    }

    /** Opens the settings on the TV, signed in with a session made here, where the website runs. */
    fun settingsIntent(context: Context): Intent {
        if (!::server.isInitialized) {
            // Not the home screen's process (or not started): settings ask for the code instead.
            return SettingsActivity.intent(context, null)
        }
        return SettingsActivity.intent(context, server.signInGuard.newLocalSession())
    }

    private fun currentProcessName(): String {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            return getProcessName()
        }
        return try {
            java.io.File("/proc/self/cmdline").readText().trim { character -> character <= ' ' }
        } catch (unreadable: IOException) {
            packageName
        }
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
        fun isSettingsProcess(processName: String): Boolean {
            return processName.endsWith(":settings")
        }

        private const val SOCKET_TIMEOUT_MS = 30_000
        private const val ALBUM_EVERY_MS = 6 * 3600_000L

        /** The first album check after the TV starts, once the home screen is up. */
        private const val ALBUM_FIRST_MS = 30_000L

        /** The first update check after the TV starts, then a look every 6 hours (GitHub is asked once a day). */
        private const val UPDATE_FIRST_MS = 120_000L
        private const val UPDATE_TICK_MS = 6 * 3600_000L
    }
}
