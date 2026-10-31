package com.glasshome.tv

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.SecureRandom

/**
 * The small website phones use to add photos: http://<tv>:8090.
 *
 * Only answers home-network addresses. A 6-digit code shown on the TV signs a
 * phone in (cookie, 30 days); 5 wrong codes lock that address out for 5 minutes.
 * POSTs need the X-Glass-Home header, which other sites can't send (CSRF).
 */
class PhotoServer(
    private val context: Context,
    private val store: WallpaperStore,
    private val icons: IconStore,
    private val weather: Weather,
    private val settings: HomeSettings,
    private val catalog: AppCatalog,
) : NanoHTTPD(PORT) {

    /** Photos in the shared album after the last sync (-1: the link failed); set by the app. */
    var albumCount: () -> Int? = { null }

    var onPhotosChanged: (() -> Unit)? = null
    var onIconsChanged: (() -> Unit)? = null
    var onWeatherChanged: (() -> Unit)? = null
    var onSettingsChanged: (() -> Unit)? = null
    var onAlbumChanged: (() -> Unit)? = null
    var onPhotoModeChanged: (() -> Unit)? = null

    private val prefs: SharedPreferences = context.getSharedPreferences("server", Context.MODE_PRIVATE)
    private val random = SecureRandom()
    private val failuresByAddress = HashMap<String, Int>()
    private val lockedUntilByAddress = HashMap<String, Long>()

    val code: String = loadOrCreateCode()

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
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (networkInterface in interfaces) {
            if (!networkInterface.isUp || networkInterface.isLoopback) {
                continue
            }
            for (inetAddress in networkInterface.inetAddresses) {
                if (inetAddress is Inet4Address && inetAddress.isSiteLocalAddress) {
                    return "http://" + inetAddress.hostAddress + ":" + PORT
                }
            }
        }
        return null
    }

    override fun serve(session: IHTTPSession): Response {
        val remote = session.remoteIpAddress ?: ""
        if (!isHomeNetwork(remote)) {
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
            if (method == Method.POST && !hasCsrfHeader(session)) {
                return text(Response.Status.FORBIDDEN, I18n.t("server.missing_header"))
            }
            if (method == Method.POST && path == "/api/login") {
                return login(session, remote)
            }
            if (!isSignedIn(session)) {
                return json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "sign-in"))
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
            return text(Response.Status.NOT_FOUND, I18n.t("server.not_found"))
        } catch (error: Exception) {
            Log.w(TAG, "Request failed: $path", error)
            return text(Response.Status.INTERNAL_ERROR, I18n.t("server.error"))
        }
    }

    // ---------- routes ----------

    private fun page(): Response {
        val html = context.assets.open("upload.html").use { input -> input.bufferedReader().readText() }
        val response = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
        response.addHeader("Content-Security-Policy", "default-src 'self'; img-src 'self' blob: data:; font-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'")
        return response
    }

    private fun login(session: IHTTPSession, remote: String): Response {
        val now = System.currentTimeMillis()
        val lockedUntil = synchronized(this) { lockedUntilByAddress[remote] ?: 0L }
        if (now < lockedUntil) {
            val minutes = (lockedUntil - now) / 60_000 + 1
            return json(Response.Status.FORBIDDEN, JSONObject().put("error", "locked").put("minutes", minutes))
        }

        session.parseBody(HashMap())
        val entered = session.parameters["code"]?.firstOrNull()?.trim() ?: ""
        if (entered != code) {
            synchronized(this) {
                val failures = (failuresByAddress[remote] ?: 0) + 1
                failuresByAddress[remote] = failures
                if (failures >= MAX_FAILURES) {
                    failuresByAddress.remove(remote)
                    lockedUntilByAddress[remote] = now + LOCKOUT_MS
                }
            }
            return json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "wrong-code"))
        }

        synchronized(this) { failuresByAddress.remove(remote) }
        val token = newToken()
        val tokens = HashSet(prefs.getStringSet("tokens", emptySet()) ?: emptySet())
        tokens.add(token)
        prefs.edit().putStringSet("tokens", tokens).apply()

        val response = json(Response.Status.OK, JSONObject().put("ok", true))
        response.addHeader("Set-Cookie", "gh=$token; Max-Age=$SESSION_SECONDS; Path=/; HttpOnly; SameSite=Strict")
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

    /** The clock fonts, so the website can preview them. Public: they are OFL fonts. */
    private fun font(name: String): Response {
        val resource = when (name) {
            "instrument.ttf" -> R.font.instrument_serif_regular
            "instrument-italic.ttf" -> R.font.instrument_serif_italic
            "dmserif.ttf" -> R.font.dm_serif_display_regular
            "bebas.ttf" -> R.font.bebas_neue_regular
            "spacemono.ttf" -> R.font.space_mono_regular
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
            .put("nightEnabled", settings.nightEnabled)
            .put("nightFrom", settings.nightFrom)
            .put("nightTo", settings.nightTo)
            .put("theme", settings.theme)
            .put("clockFont", settings.clockFont)
            .put("weatherStyle", settings.weatherStyle)
            .put("dateFormat", settings.dateFormat)
            .put("language", I18n.language)
            .put("languages", JSONArray(availableLanguages()))
            .put("shufflePhotos", settings.shufflePhotos)
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
        val shuffle = parameters["shufflePhotos"]?.firstOrNull()
        if (shuffle != null) {
            settings.shufflePhotos = shuffle == "1"
        }

        val albumLink = parameters["albumLink"]?.firstOrNull()?.trim()
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

    /** Language codes with a file in assets/i18n, e.g. ["en"]. */
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
            if (!validLatitude || !validLongitude || name.length > 120) {
                return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "bad-place"))
            }
            weather.setPlace(WeatherPlace(name, latitude, longitude))
        }
        onWeatherChanged?.invoke()
        return weatherSettings()
    }

    // ---------- checks ----------

    private fun isHomeNetwork(remote: String): Boolean {
        return try {
            val address = InetAddress.getByName(remote)
            address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress
        } catch (error: Exception) {
            false
        }
    }

    private fun hasCsrfHeader(session: IHTTPSession): Boolean {
        return session.headers["x-glass-home"] == "1"
    }

    private fun isSignedIn(session: IHTTPSession): Boolean {
        val token = session.cookies.read("gh") ?: return false
        val tokens = prefs.getStringSet("tokens", emptySet()) ?: emptySet()
        return tokens.contains(token)
    }

    // ---------- helpers ----------

    private fun loadOrCreateCode(): String {
        val saved = prefs.getString("code", null)
        if (saved != null) {
            return saved
        }
        val newCode = String.format("%06d", random.nextInt(1_000_000))
        prefs.edit().putString("code", newCode).apply()
        return newCode
    }

    private fun newToken(): String {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        val builder = StringBuilder()
        for (byte in bytes) {
            builder.append(String.format("%02x", byte))
        }
        return builder.toString()
    }

    private fun json(status: Response.Status, body: JSONObject): Response {
        return newFixedLengthResponse(status, "application/json", body.toString())
    }

    private fun text(status: Response.Status, body: String): Response {
        return newFixedLengthResponse(status, "text/plain; charset=utf-8", body)
    }

    companion object {
        const val PORT = 8090
        private const val TAG = "GlassHome"
        private const val MAX_FAILURES = 5
        private const val LOCKOUT_MS = 5 * 60_000L
        private const val SESSION_SECONDS = 30 * 24 * 3600
        private const val MAX_UPLOAD_BYTES = 60L * 1024 * 1024
        private const val MAX_PHOTOS = 300
        private const val MAX_ICONS = 100
        private val ALLOWED_INTERVALS = setOf(15, 30, 45, 60, 120, 300, 600)
    }
}
