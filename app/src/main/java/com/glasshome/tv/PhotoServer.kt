package com.glasshome.tv

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.SecureRandom

/**
 * The small website phones use to add photos: http://<tv>:8090.
 *
 * Only answers home-network addresses, and only under the TV's own name or IP
 * (no DNS rebinding). A 6-digit code shown on the TV signs a phone in; the
 * limits on guessing it live in [SignInGuard]. POSTs need the X-Glass-Home
 * header, which other sites can't send (CSRF).
 */
class PhotoServer(
    private val context: Context,
    private val store: WallpaperStore,
    private val icons: IconStore,
    private val weather: Weather,
    private val settings: HomeSettings,
    private val catalog: AppCatalog,
) : NanoHTTPD(PORT) {

    /** Key of the photo on the TV right now (null before the first one); set by the app. */
    var currentPhoto: () -> String? = { null }

    /** Photos in the shared album after the last sync (-1: the link failed); set by the app. */
    var albumCount: () -> Int? = { null }

    var onPhotosChanged: (() -> Unit)? = null
    var onIconsChanged: (() -> Unit)? = null
    var onWeatherChanged: (() -> Unit)? = null
    var onSettingsChanged: (() -> Unit)? = null
    var onAlbumChanged: (() -> Unit)? = null
    var onPhotoModeChanged: (() -> Unit)? = null

    private val prefs: SharedPreferences = context.getSharedPreferences("server", Context.MODE_PRIVATE)
    val signInGuard = SignInGuard(PrefsSignInStorage(prefs), SecureRandom()) { System.currentTimeMillis() }

    val code: String
        get() = signInGuard.code

    /** For the iPhone shortcut: POST /send/<key> opens a link without signing in. */
    val shareKey = ShareKey(PrefsShareKeyStorage(prefs), SecureRandom())

    private val recentLinks = RecentLinks(loadRecentLinks())

    /** "New code" on the TV: every phone signs in again, and the shortcut needs the new key. */
    fun newCode() {
        signInGuard.resetCode()
        shareKey.reset()
    }

    /** "http://glasshome.local:8090": answered by LocalName over mDNS. */
    fun friendlyAddress(): String {
        return "http://" + LocalName.HOST_NAME + ":" + PORT
    }

    /** A link that also signs the phone in: what the QR code on the TV holds. */
    fun signInLink(): String? {
        val base = address() ?: return null
        return "$base/?code=$code"
    }

    /** "http://192.168.1.20:8090", or null without a home-network address. */
    fun address(): String? {
        val home = HomeNetwork.find() ?: return null
        return "http://" + home.address.hostAddress + ":" + PORT
    }

    override fun serve(session: IHTTPSession): Response {
        val remote = session.remoteIpAddress ?: ""
        if (!RequestChecks.isHomeNetwork(remote)) {
            return text(Response.Status.FORBIDDEN, I18n.t("server.home_only"))
        }
        if (!RequestChecks.isAllowedHost(session.headers["host"])) {
            return text(Response.Status.FORBIDDEN, I18n.t("server.home_only"))
        }
        val path = session.uri ?: "/"
        val method = session.method

        try {
            if (method == Method.GET && (path == "/" || path == "/index.html")) {
                return page()
            }
            if (method == Method.GET && path == "/i18n.json") {
                return json(Response.Status.OK, org.json.JSONObject(I18n.merged()))
            }
            if (method == Method.GET && path.startsWith("/fonts/")) {
                return font(path.removePrefix("/fonts/"))
            }
            // Before the website's own checks: the shortcut has the key instead of a
            // session, and can't be a page in a browser (the key is in no page).
            if (method == Method.POST && path.startsWith("/send/")) {
                return sendWithKey(session, path.removePrefix("/send/"))
            }
            if (method == Method.POST && !hasCsrfHeader(session)) {
                return text(Response.Status.FORBIDDEN, I18n.t("server.missing_header"))
            }
            if (method == Method.POST && path == "/api/login") {
                return login(session, remote)
            }
            if (!isSignedIn(session)) {
                return json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "sign-in"))
            }
            if (method == Method.GET && path == "/api/update") {
                return updateStatus()
            }
            if (method == Method.POST && path == "/api/update") {
                return changeUpdate(session)
            }
            if (method == Method.GET && path == "/api/screen") {
                return screen()
            }
            if (method == Method.GET && path == "/api/photos") {
                return listPhotos()
            }
            if (method == Method.GET && path.startsWith("/api/thumb/")) {
                return thumb(path.removePrefix("/api/thumb/"))
            }
            if (method == Method.GET && path.startsWith("/api/photo/")) {
                return photo(path.removePrefix("/api/photo/"))
            }
            if (method == Method.POST && path == "/api/upload") {
                return upload(session)
            }
            if (method == Method.POST && path == "/api/delete") {
                return delete(session)
            }
            if (method == Method.POST && path == "/api/photo-order") {
                return photoOrder(session)
            }
            if (method == Method.GET && path == "/api/settings") {
                return homeSettings()
            }
            if (method == Method.POST && path == "/api/settings") {
                return saveSettings(session)
            }
            if (method == Method.GET && path == "/api/dock") {
                return dock()
            }
            if (method == Method.POST && path == "/api/dock") {
                return changeDock(session)
            }
            if (method == Method.GET && path == "/api/weather") {
                return weatherSettings()
            }
            if (method == Method.GET && path == "/api/weather-search") {
                return weatherSearch(session)
            }
            if (method == Method.POST && path == "/api/weather") {
                return saveWeather(session)
            }
            if (method == Method.GET && path == "/api/icons") {
                return listIcons()
            }
            if (method == Method.GET && path.startsWith("/api/icon/")) {
                return icon(path.removePrefix("/api/icon/"))
            }
            if (method == Method.POST && path == "/api/icon-upload") {
                return uploadIcons(session)
            }
            if (method == Method.POST && path == "/api/icon-delete") {
                return deleteIcon(session)
            }
            if (method == Method.POST && path == "/api/open") {
                return openSharedLink(session)
            }
            if (method == Method.GET && path == "/api/sleep") {
                return sleepStatus()
            }
            if (method == Method.POST && path == "/api/sleep") {
                return changeSleep(session)
            }
            if (method == Method.GET && path == "/api/recent") {
                return recent()
            }
            if (method == Method.POST && path == "/api/recent") {
                return changeRecent(session)
            }
            if (method == Method.GET && path == "/api/share-key") {
                return shareKeyInfo()
            }
            if (method == Method.POST && path == "/api/share-key") {
                return changeShareKey(session)
            }
            return text(Response.Status.NOT_FOUND, I18n.t("server.not_found"))
        } catch (error: Exception) {
            Log.w(TAG, "Request failed: " + RequestLog.path(path), error)
            return text(Response.Status.INTERNAL_ERROR, I18n.t("server.error"))
        }
    }

    // ---------- routes ----------

    private fun page(): Response {
        val html = context.assets.open("upload.html").use { input -> input.bufferedReader().readText() }
        val response = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
        response.addHeader("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'; img-src 'self' blob: data:; font-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'")
        // Check for a newer page every time: after an app update the phone must not keep the old one.
        response.addHeader("Cache-Control", "no-cache")
        return response
    }

    private fun login(session: IHTTPSession, remote: String): Response {
        session.parseBody(HashMap())
        val entered = session.parameters["code"]?.firstOrNull() ?: ""
        val result = signInGuard.signIn(remote, entered)
        if (result is SignInResult.Locked) {
            return json(Response.Status.FORBIDDEN, JSONObject().put("error", "locked").put("minutes", result.minutes))
        }
        if (result !is SignInResult.SignedIn) {
            return json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "wrong-code"))
        }
        val response = json(Response.Status.OK, JSONObject().put("ok", true))
        response.addHeader("Set-Cookie", "gh=${result.token}; Max-Age=${SignInGuard.SESSION_SECONDS}; Path=/; HttpOnly; SameSite=Strict")
        return response
    }

    private fun listPhotos(): Response {
        val photos = JSONArray()
        for (wallpaper in store.own()) {
            val file = wallpaper.file ?: continue
            photos.put(JSONObject().put("name", file.name))
        }
        return json(Response.Status.OK, JSONObject().put("photos", photos))
    }

    private fun photo(name: String): Response {
        if (name.contains("/") || !name.endsWith(".jpg")) {
            return text(Response.Status.NOT_FOUND, I18n.t("server.not_found"))
        }
        val file = File(File(context.filesDir, "photos"), name)
        if (!file.isFile) {
            return text(Response.Status.NOT_FOUND, I18n.t("server.not_found"))
        }
        return newFixedLengthResponse(Response.Status.OK, "image/jpeg", FileInputStream(file), file.length())
    }

    /** Small JPEG previews for the website, made once and kept in the cache folder. */
    private fun thumb(name: String): Response {
        if (name.contains("/") || !name.endsWith(".jpg")) {
            return text(Response.Status.NOT_FOUND, I18n.t("server.not_found"))
        }
        val thumbs = File(context.cacheDir, "thumbs")
        val cached = File(thumbs, name)
        if (!cached.isFile) {
            val wallpaper = store.find("own:$name") ?: return text(Response.Status.NOT_FOUND, I18n.t("server.not_found"))
            val bitmap = store.loadThumb(wallpaper) ?: return text(Response.Status.NOT_FOUND, I18n.t("server.not_found"))
            thumbs.mkdirs()
            java.io.FileOutputStream(cached).use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, output)
            }
            bitmap.recycle()
        }
        val response = newFixedLengthResponse(Response.Status.OK, "image/jpeg", FileInputStream(cached), cached.length())
        response.addHeader("Cache-Control", "private, max-age=86400")
        return response
    }

    /**
     * A small JPEG of the photo on the TV right now, for the live preview at
     * the top of the website. Never cached: it changes with every photo.
     */
    private fun screen(): Response {
        var wallpaper: Wallpaper? = null
        val key = currentPhoto()
        if (key != null) {
            wallpaper = store.find(key)
        }
        if (wallpaper == null) {
            wallpaper = store.playlist().firstOrNull()
        }
        if (wallpaper == null) {
            return text(Response.Status.NOT_FOUND, I18n.t("server.not_found"))
        }
        val bitmap = store.loadThumb(wallpaper) ?: return text(Response.Status.NOT_FOUND, I18n.t("server.not_found"))
        val bytes = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, bytes)
        bitmap.recycle()
        val body = bytes.toByteArray()
        val response = newFixedLengthResponse(Response.Status.OK, "image/jpeg", java.io.ByteArrayInputStream(body), body.size.toLong())
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    /** The clock and website fonts, so the website can use them. Public: they are OFL fonts. */
    private fun font(name: String): Response {
        val resource = when (name) {
            "instrument.ttf" -> R.font.instrument_serif_regular
            "instrument-italic.ttf" -> R.font.instrument_serif_italic
            "dmserif.ttf" -> R.font.dm_serif_display_regular
            "bebas.ttf" -> R.font.bebas_neue_regular
            "spacemono.ttf" -> R.font.space_mono_regular
            "bricolage.ttf" -> R.font.bricolage_grotesque
            "atkinson.ttf" -> R.font.atkinson_hyperlegible_regular
            "atkinson-bold.ttf" -> R.font.atkinson_hyperlegible_bold
            else -> return text(Response.Status.NOT_FOUND, I18n.t("server.not_found"))
        }
        val bytes = context.resources.openRawResource(resource).use { input -> input.readBytes() }
        val response = newFixedLengthResponse(Response.Status.OK, "font/ttf", java.io.ByteArrayInputStream(bytes), bytes.size.toLong())
        response.addHeader("Cache-Control", "public, max-age=604800")
        return response
    }

    private fun upload(session: IHTTPSession): Response {
        val length = session.headers["content-length"]?.toLongOrNull() ?: 0L
        if (length > MAX_UPLOAD_BYTES) {
            return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "too-big"))
        }
        if (store.own().size >= MAX_PHOTOS) {
            return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "full"))
        }

        val files = HashMap<String, String>()
        session.parseBody(files)
        var added = 0
        var rejected = 0
        for (temporaryPath in files.values) {
            val saved = store.addOwn(File(temporaryPath))
            if (saved) {
                added += 1
            } else {
                rejected += 1
            }
        }
        if (added > 0) {
            onPhotosChanged?.invoke()
        }
        return json(Response.Status.OK, JSONObject().put("added", added).put("rejected", rejected))
    }

    /** Body: names=a.jpg,b.jpg,... in the order to show them. */
    private fun photoOrder(session: IHTTPSession): Response {
        session.parseBody(HashMap())
        val names = (session.parameters["names"]?.firstOrNull() ?: "").split(",")
        store.setOrder(names)
        onPhotosChanged?.invoke()
        return listPhotos()
    }

    private fun delete(session: IHTTPSession): Response {
        session.parseBody(HashMap())
        val name = session.parameters["name"]?.firstOrNull() ?: ""
        val deleted = store.deleteOwn(name)
        if (deleted) {
            onPhotosChanged?.invoke()
        }
        return json(Response.Status.OK, JSONObject().put("deleted", deleted))
    }

    private fun listIcons(): Response {
        val list = JSONArray()
        for (name in icons.userIcons()) {
            list.put(JSONObject().put("name", name))
        }
        return json(Response.Status.OK, JSONObject().put("icons", list))
    }

    private fun icon(name: String): Response {
        val file = icons.userIconFile(name) ?: return text(Response.Status.NOT_FOUND, I18n.t("server.not_found"))
        return newFixedLengthResponse(Response.Status.OK, "image/png", FileInputStream(file), file.length())
    }

    private fun uploadIcons(session: IHTTPSession): Response {
        val length = session.headers["content-length"]?.toLongOrNull() ?: 0L
        if (length > MAX_UPLOAD_BYTES) {
            return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "too-big"))
        }
        if (icons.userIcons().size >= MAX_ICONS) {
            return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "full"))
        }
        val files = HashMap<String, String>()
        session.parseBody(files)
        var added = 0
        var rejected = 0
        for (temporaryPath in files.values) {
            if (icons.addUser(File(temporaryPath))) {
                added += 1
            } else {
                rejected += 1
            }
        }
        if (added > 0) {
            onIconsChanged?.invoke()
        }
        return json(Response.Status.OK, JSONObject().put("added", added).put("rejected", rejected))
    }

    private fun deleteIcon(session: IHTTPSession): Response {
        session.parseBody(HashMap())
        val name = session.parameters["name"]?.firstOrNull() ?: ""
        val deleted = icons.deleteUser(name)
        if (deleted) {
            onIconsChanged?.invoke()
        }
        return json(Response.Status.OK, JSONObject().put("deleted", deleted))
    }

    private fun homeSettings(): Response {
        SystemClockFormat.sync(context, settings)
        val buttons = JSONObject()
        for (color in HomeSettings.BUTTON_COLORS) {
            val key = settings.buttonTarget(color) ?: continue
            for (item in catalog.everyItem()) {
                if (item.key == key) {
                    buttons.put(color, item.label)
                }
            }
        }
        val body = JSONObject()
            .put("photoInterval", settings.photoIntervalSeconds)
            .put("clockFormat", settings.clockFormat)
            .put("homeButton", settings.homeButton)
            // What the TV shows now; with "auto" that is the TV's own setting.
            .put("clock24", uses24Hours())
            .put("nightEnabled", settings.nightEnabled)
            .put("nightFrom", settings.nightFrom)
            .put("nightTo", settings.nightTo)
            .put("theme", settings.theme)
            .put("clockFont", settings.clockFont)
            .put("weatherStyle", settings.weatherStyle)
            .put("dateFormat", settings.dateFormat)
            .put("language", I18n.language)
            .put("languages", JSONArray(availableLanguages()))
            .put("languageNames", languageNames())
            // "This TV" on the website: the VPN, and the address that works without glasshome.local.
            .put("vpn", VpnWatch.isOn(context))
            .put("address", address() ?: "")
            .put("shufflePhotos", settings.shufflePhotos)
            .put("photoMotion", settings.photoMotion)
            .put("photoMode", store.mode)
            .put("pinnedPhoto", store.pinnedKey ?: "")
            .put("albumLink", settings.albumLink)
            .put("albumCount", albumCount() ?: JSONObject.NULL)
            .put("buttons", buttons)
        return json(Response.Status.OK, body)
    }

    /** Every field is optional; values outside the allowed ones are ignored. */
    private fun saveSettings(session: IHTTPSession): Response {
        session.parseBody(HashMap())
        val parameters = session.parameters

        val interval = parameters["photoInterval"]?.firstOrNull()?.toIntOrNull()
        if (interval != null && ALLOWED_INTERVALS.contains(interval)) {
            settings.photoIntervalSeconds = interval
        }
        val clockFormat = parameters["clockFormat"]?.firstOrNull()
        if (clockFormat == "auto" || clockFormat == "12" || clockFormat == "24") {
            settings.clockFormat = clockFormat
            SystemClockFormat.chosen(context, settings)
        }
        val homeButton = parameters["homeButton"]?.firstOrNull()
        if (homeButton != null && HomeButton.MODES.contains(homeButton)) {
            settings.homeButton = homeButton
        }
        val nightEnabled = parameters["nightEnabled"]?.firstOrNull()
        if (nightEnabled != null) {
            settings.nightEnabled = nightEnabled == "1"
        }
        val nightFrom = parameters["nightFrom"]?.firstOrNull()?.toIntOrNull()
        if (nightFrom != null && nightFrom in 0..23) {
            settings.nightFrom = nightFrom
        }
        val nightTo = parameters["nightTo"]?.firstOrNull()?.toIntOrNull()
        if (nightTo != null && nightTo in 0..23) {
            settings.nightTo = nightTo
        }

        val theme = parameters["theme"]?.firstOrNull()
        if (theme != null && HomeSettings.THEMES.contains(theme)) {
            settings.theme = theme
        }
        val clockFont = parameters["clockFont"]?.firstOrNull()
        if (clockFont != null && HomeSettings.FONTS.contains(clockFont)) {
            settings.clockFont = clockFont
        }
        val weatherStyle = parameters["weatherStyle"]?.firstOrNull()
        if (weatherStyle != null && HomeSettings.WEATHER_STYLES.contains(weatherStyle)) {
            settings.weatherStyle = weatherStyle
        }
        val language = parameters["language"]?.firstOrNull()
        if (language != null && availableLanguages().contains(language)) {
            settings.language = language
            I18n.load(context, language)
        }
        val dateFormat = parameters["dateFormat"]?.firstOrNull()
        if (dateFormat != null && HomeSettings.DATE_FORMATS.contains(dateFormat)) {
            settings.dateFormat = dateFormat
        }
        // Which photos play: "all", "own" (uploaded only) or "pinned" (one photo stays).
        val photoMode = parameters["photoMode"]?.firstOrNull()
        val pinnedPhoto = parameters["pinnedPhoto"]?.firstOrNull()
        var photoModeChanged = false
        if (pinnedPhoto != null && store.find(pinnedPhoto) != null) {
            store.pinnedKey = pinnedPhoto
            photoModeChanged = true
        }
        if (photoMode == WallpaperStore.MODE_ALL || photoMode == WallpaperStore.MODE_OWN || photoMode == WallpaperStore.MODE_PINNED) {
            store.mode = photoMode
            photoModeChanged = true
        }
        if (photoModeChanged) {
            onPhotoModeChanged?.invoke()
        }
        val motion = parameters["photoMotion"]?.firstOrNull()
        if (motion != null) {
            settings.photoMotion = motion == "1"
        }
        val shuffle = parameters["shufflePhotos"]?.firstOrNull()
        if (shuffle != null) {
            settings.shufflePhotos = shuffle == "1"
        }

        val pastedLink = parameters["albumLink"]?.firstOrNull()
        var albumLink: String? = null
        if (pastedLink != null) {
            albumLink = AlbumLinks.extractLink(pastedLink)
        }
        if (albumLink != null) {
            val acceptable = albumLink.isEmpty() || (albumLink.startsWith("https://") && albumLink.length < 500)
            if (!acceptable) {
                return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "bad-link"))
            }
            if (albumLink != settings.albumLink) {
                settings.albumLink = albumLink
                onAlbumChanged?.invoke()
            }
        }
        onSettingsChanged?.invoke()
        return homeSettings()
    }

    private fun uses24Hours(): Boolean {
        if (settings.clockFormat == "24") {
            return true
        }
        if (settings.clockFormat == "12") {
            return false
        }
        return android.text.format.DateFormat.is24HourFormat(context)
    }

    /** Language codes with a file in assets/i18n, e.g. ["en"]. */
    private fun languageNames(): JSONObject {
        val names = JSONObject()
        for (code in availableLanguages()) {
            names.put(code, I18n.nameOf(context, code))
        }
        return names
    }

    private fun availableLanguages(): List<String> {
        val files = context.assets.list("i18n") ?: emptyArray()
        val codes = ArrayList<String>()
        for (file in files) {
            if (file.endsWith(".json")) {
                codes.add(file.removeSuffix(".json"))
            }
        }
        codes.sort()
        return codes
    }

    private fun dock(): Response {
        val list = JSONArray()
        for (item in catalog.everyItem()) {
            list.put(JSONObject()
                .put("key", item.key)
                .put("label", item.label)
                .put("hidden", catalog.isHidden(item.key)))
        }
        return json(Response.Status.OK, JSONObject().put("items", list))
    }

    /** Body: key + move=-1|1, or key + hidden=0|1. */
    private fun changeDock(session: IHTTPSession): Response {
        session.parseBody(HashMap())
        val parameters = session.parameters
        val key = parameters["key"]?.firstOrNull() ?: return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "no-key"))
        val move = parameters["move"]?.firstOrNull()?.toIntOrNull()
        if (move == -1 || move == 1) {
            catalog.move(key, move)
        }
        val hidden = parameters["hidden"]?.firstOrNull()
        if (hidden == "1") {
            catalog.hide(key)
        }
        if (hidden == "0") {
            catalog.unhide(key)
        }
        onSettingsChanged?.invoke()
        return dock()
    }

    private fun weatherSettings(): Response {
        val place = weather.place()
        val body = JSONObject()
            .put("enabled", weather.enabled)
            .put("place", place?.name ?: "")
        // The reading the TV shows now, for the website's preview.
        val now = weather.cached()
        if (now != null) {
            body.put("temperature", now.temperature)
                .put("description", now.description)
        }
        return json(Response.Status.OK, body)
    }

    private fun weatherSearch(session: IHTTPSession): Response {
        val query = session.parameters["q"]?.firstOrNull()?.trim() ?: ""
        val places = JSONArray()
        if (query.length >= 2) {
            for (place in weather.search(query)) {
                places.put(JSONObject()
                    .put("name", place.name)
                    .put("latitude", place.latitude)
                    .put("longitude", place.longitude))
            }
        }
        return json(Response.Status.OK, JSONObject().put("places", places))
    }

    /** Body: enabled=0|1, or name + latitude + longitude to change the place. */
    private fun saveWeather(session: IHTTPSession): Response {
        session.parseBody(HashMap())
        val parameters = session.parameters
        val enabled = parameters["enabled"]?.firstOrNull()
        if (enabled != null) {
            weather.enabled = enabled == "1"
        }
        val name = parameters["name"]?.firstOrNull()
        val latitude = parameters["latitude"]?.firstOrNull()?.toDoubleOrNull()
        val longitude = parameters["longitude"]?.firstOrNull()?.toDoubleOrNull()
        if (name != null && latitude != null && longitude != null) {
            val validLatitude = latitude >= -90 && latitude <= 90
            val validLongitude = longitude >= -180 && longitude <= 180
            // City names have no markup; refusing < and > keeps a name from ever becoming HTML on a page.
            val hasMarkup = name.contains('<') || name.contains('>')
            if (!validLatitude || !validLongitude || name.length > 120 || hasMarkup) {
                return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "bad-place"))
            }
            weather.setPlace(WeatherPlace(name, latitude, longitude))
        }
        onWeatherChanged?.invoke()
        return weatherSettings()
    }

    // ---------- updates ----------

    /** Set by GlassHomeApp: the updater that checks GitHub and installs after "Install". */
    var updater: Updater? = null

    private fun updateStatus(): Response {
        val current = updater ?: return json(Response.Status.NOT_FOUND, JSONObject().put("error", "no-updater"))
        val reply = JSONObject()
            .put("installed", current.installedVersion)
            .put("state", current.state)
            .put("checks", current.checksEnabled)
            .put("development", current.isDevelopmentBuild)
            .put("lastCheck", current.lastCheckMillis)
            .put("error", current.lastError)
        val latest = current.latest
        if (latest != null) {
            reply.put("latest", latest.version)
        }
        return json(Response.Status.OK, reply)
    }

    /** Body: action=check|install|skip, or checks=0|1. Nothing installs without action=install. */
    private fun changeUpdate(session: IHTTPSession): Response {
        val current = updater ?: return json(Response.Status.NOT_FOUND, JSONObject().put("error", "no-updater"))
        session.parseBody(HashMap())
        val parameters = session.parameters
        val checks = parameters["checks"]?.firstOrNull()
        if (checks != null) {
            current.checksEnabled = checks == "1"
        }
        val action = parameters["action"]?.firstOrNull()
        if (action == "check") {
            current.check()
        } else if (action == "install") {
            // Glass Home comes back after the update when it was on the TV's screen,
            // or the request came from the settings on the TV (127.0.0.1).
            val fromTv = session.remoteIpAddress == "127.0.0.1"
            current.install(reopenHome = fromTv || HomeScreenState.inFront)
        } else if (action == "skip") {
            current.skip()
        } else if (action != null) {
            return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "bad-action"))
        }
        return updateStatus()
    }

    // ---------- links ----------

    /** Set by GlassHomeApp: opens a link on the TV ([LinkOpener]). */
    var openLink: ((String) -> LinkOpener.Result)? = null

    /** The last request=<id> and what it did: the website sends again after 8 s. */
    private var lastOpenRequest: String? = null
    private var lastOpenResult = LinkOpener.Result.OPENED

    /** Body: link=<a web link, or text with one in it>, request=<an id the website makes>. */
    @Synchronized
    private fun openSharedLink(session: IHTTPSession): Response {
        val opener = openLink ?: return json(Response.Status.NOT_FOUND, JSONObject().put("error", "no-opener"))
        session.parseBody(HashMap())
        val parameters = session.parameters
        val text = parameters["link"]?.firstOrNull() ?: ""
        val link = SharedLinks.find(text) ?: return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "bad-link"))
        val request = parameters["request"]?.firstOrNull()
        val result: LinkOpener.Result
        if (request != null && request == lastOpenRequest) {
            // The same request again: the first one got here, only its answer didn't.
            result = lastOpenResult
        } else {
            result = openAndRemember(opener, link)
            lastOpenRequest = request
            lastOpenResult = result
        }
        val reply = when (result) {
            LinkOpener.Result.NO_APP -> "no-app"
            LinkOpener.Result.SERVICE_OFF -> "service-off"
            LinkOpener.Result.OPENED -> "opened"
        }
        return json(Response.Status.OK, JSONObject().put("result", reply))
    }

    @Synchronized
    private fun openAndRemember(opener: (String) -> LinkOpener.Result, link: String): LinkOpener.Result {
        // When it arrived, not what: the link is the user's watch history.
        Log.i(TAG, "Link from a phone to open")
        val result = opener(link)
        if (result == LinkOpener.Result.OPENED) {
            recentLinks.add(link)
            prefs.edit().putString("recent_links", JSONArray(recentLinks.links).toString()).apply()
        }
        return result
    }

    /** POST /send/<key>, body link=<link>: the answer is a sentence the shortcut can show. */
    private fun sendWithKey(session: IHTTPSession, given: String): Response {
        if (!shareKey.matches(given)) {
            return text(Response.Status.FORBIDDEN, I18n.t("web.send.wrong_key"))
        }
        val opener = openLink ?: return text(Response.Status.NOT_FOUND, I18n.t("server.not_found"))
        session.parseBody(HashMap())
        val text = session.parameters["link"]?.firstOrNull() ?: ""
        val link = SharedLinks.find(text) ?: return text(Response.Status.BAD_REQUEST, I18n.t("web.open.bad_link"))
        val result = openAndRemember(opener, link)
        val sentence = when (result) {
            LinkOpener.Result.NO_APP -> I18n.t("web.open.no_app")
            LinkOpener.Result.SERVICE_OFF -> I18n.t("web.open.service_off")
            LinkOpener.Result.OPENED -> I18n.t("web.open.opened")
        }
        return text(Response.Status.OK, sentence)
    }

    @Synchronized
    private fun recent(): Response {
        return json(Response.Status.OK, JSONObject().put("links", JSONArray(recentLinks.links)))
    }

    /** Body: action=clear. */
    @Synchronized
    private fun changeRecent(session: IHTTPSession): Response {
        session.parseBody(HashMap())
        if (session.parameters["action"]?.firstOrNull() != "clear") {
            return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "bad-action"))
        }
        recentLinks.clear()
        prefs.edit().remove("recent_links").apply()
        return recent()
    }

    private fun loadRecentLinks(): List<String> {
        val saved = prefs.getString("recent_links", null) ?: return emptyList()
        val links = ArrayList<String>()
        try {
            val array = JSONArray(saved)
            for (index in 0 until array.length()) {
                links.add(array.getString(index))
            }
        } catch (broken: Exception) {
            // A damaged list: start a new one.
        }
        return links
    }

    private fun shareKeyInfo(): Response {
        val body = JSONObject()
            .put("key", shareKey.key)
            .put("address", friendlyAddress() + "/send/" + shareKey.key)
        return json(Response.Status.OK, body)
    }

    /** Body: action=reset. The shortcut on every phone needs the new address. */
    private fun changeShareKey(session: IHTTPSession): Response {
        session.parseBody(HashMap())
        if (session.parameters["action"]?.firstOrNull() != "reset") {
            return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "bad-action"))
        }
        shareKey.reset()
        return shareKeyInfo()
    }

    // ---------- sleep timer ----------

    /** Set by GlassHomeApp, which turns the TV off when it runs out. */
    var sleepTimer: SleepTimer? = null
    var onSleepChanged: (() -> Unit)? = null

    private fun sleepStatus(): Response {
        val timer = sleepTimer ?: return json(Response.Status.NOT_FOUND, JSONObject().put("error", "no-timer"))
        val now = System.currentTimeMillis()
        val body = JSONObject()
            .put("minutesLeft", timer.minutesLeft(now))
            .put("sleepAt", timer.sleepAt() ?: 0L)
            // Only the accessibility service can turn the screen off.
            .put("serviceOn", LinkOpener.serviceOn(context))
        return json(Response.Status.OK, body)
    }

    /** Body: minutes=0 (off) or 1 to SleepTimer.MAX_MINUTES. */
    private fun changeSleep(session: IHTTPSession): Response {
        val timer = sleepTimer ?: return json(Response.Status.NOT_FOUND, JSONObject().put("error", "no-timer"))
        session.parseBody(HashMap())
        val minutes = session.parameters["minutes"]?.firstOrNull()?.toIntOrNull()
        if (minutes == null || !timer.set(minutes, System.currentTimeMillis())) {
            return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "bad-minutes"))
        }
        onSleepChanged?.invoke()
        return sleepStatus()
    }

    // ---------- checks ----------

    private fun hasCsrfHeader(session: IHTTPSession): Boolean {
        return session.headers["x-glass-home"] == "1"
    }

    private fun isSignedIn(session: IHTTPSession): Boolean {
        return signInGuard.isSignedIn(session.cookies.read("gh"))
    }

    // ---------- helpers ----------

    private fun json(status: Response.Status, body: JSONObject): Response {
        return newFixedLengthResponse(status, "application/json", body.toString())
    }

    private fun text(status: Response.Status, body: String): Response {
        return newFixedLengthResponse(status, "text/plain; charset=utf-8", body)
    }

    companion object {
        const val PORT = 8090
        private const val TAG = "GlassHome"
        private const val MAX_UPLOAD_BYTES = 60L * 1024 * 1024
        private const val MAX_PHOTOS = 300
        private const val MAX_ICONS = 100
        private val ALLOWED_INTERVALS = setOf(15, 30, 45, 60, 120, 300, 600)
    }
}

/** The share key in the server's SharedPreferences. */
private class PrefsShareKeyStorage(private val prefs: SharedPreferences) : ShareKeyStorage {
    override fun loadKey(): String? {
        return prefs.getString("share_key", null)
    }

    override fun saveKey(key: String) {
        prefs.edit().putString("share_key", key).apply()
    }
}

/** The code and sessions in the server's SharedPreferences. */
private class PrefsSignInStorage(private val prefs: SharedPreferences) : SignInStorage {
    override fun loadCode(): String? {
        return prefs.getString("code", null)
    }

    override fun saveCode(code: String) {
        prefs.edit().putString("code", code).apply()
    }

    override fun loadTokens(): Set<String> {
        return prefs.getStringSet("tokens", emptySet()) ?: emptySet()
    }

    override fun saveTokens(tokens: Set<String>) {
        prefs.edit().putStringSet("tokens", HashSet(tokens)).apply()
    }
}
