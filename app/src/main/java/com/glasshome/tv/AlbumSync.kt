package com.glasshome.tv

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Keeps the TV's photos in step with a shared album link:
 *   iCloud:        https://www.icloud.com/sharedalbum/#<token>   (public web API)
 *   Google Photos: https://photos.app.goo.gl/<id>                 (best effort: reads the share page)
 * New photos are downloaded as album-*.jpg next to uploaded ones; photos that
 * left the album are removed. Blocking: run off the main thread.
 *
 * Every address is checked by [AlbumLinks] before it is opened, redirects
 * included, and every reply has a size limit. Album links carry the album's
 * access token, so they never go into the log.
 */
class AlbumSync(private val store: WallpaperStore, private val cacheDir: File) {

    /** Returns the number of photos now in the album on the TV, or -1 if the link failed. */
    fun sync(link: String): Int {
        val trimmed = AlbumLinks.extractLink(link)
        if (trimmed.isEmpty()) {
            store.removeAlbumPhotosExcept(emptySet())
            return 0
        }
        val photoUrls = when (AlbumLinks.kind(trimmed)) {
            AlbumLinks.Kind.ICLOUD -> iCloudPhotos(trimmed)
            AlbumLinks.Kind.GOOGLE -> googlePhotos(trimmed)
            null -> null
        }
        if (photoUrls == null) {
            return -1
        }

        val keep = HashSet<String>()
        for (url in photoUrls.take(MAX_PHOTOS)) {
            val id = shortHash(url.substringBefore("?").substringBefore("="))
            keep.add(id)
            if (store.hasAlbumPhoto(id)) {
                continue
            }
            val download = File(cacheDir, "album-download.tmp")
            if (downloadTo(url, download)) {
                store.addAlbumPhoto(download, id)
            }
            download.delete()
        }
        store.removeAlbumPhotosExcept(keep)
        return keep.size
    }

    // ---------- iCloud ----------

    private fun iCloudPhotos(link: String): List<String>? {
        val token = link.substringAfter("#", "").substringBefore(";").trim()
        if (token.isEmpty()) {
            return null
        }
        var host = "p23-sharedstreams.icloud.com"
        var stream = postJson("https://$host/$token/sharedstreams/webstream", JSONObject().put("streamCtag", JSONObject.NULL))
        // A 330 reply names the server that holds this album.
        val redirectHost = stream?.optString("X-Apple-MMe-Host", "") ?: ""
        if (redirectHost.isNotEmpty()) {
            if (!AlbumLinks.isICloudStreamHost(redirectHost)) {
                return null
            }
            host = redirectHost
            stream = postJson("https://$host/$token/sharedstreams/webstream", JSONObject().put("streamCtag", JSONObject.NULL))
        }
        val photos = stream?.optJSONArray("photos") ?: return null

        val guids = JSONArray()
        val checksumByGuid = HashMap<String, String>()
        for (index in 0 until photos.length()) {
            val photo = photos.getJSONObject(index)
            val guid = photo.getString("photoGuid")
            val checksum = bestDerivative(photo.getJSONObject("derivatives")) ?: continue
            guids.put(guid)
            checksumByGuid[guid] = checksum
        }
        if (guids.length() == 0) {
            return emptyList()
        }

        val assets = postJson("https://$host/$token/sharedstreams/webasseturls", JSONObject().put("photoGuids", guids))
            ?: return null
        val items = assets.optJSONObject("items") ?: return null
        val urls = ArrayList<String>()
        for ((_, checksum) in checksumByGuid) {
            val item = items.optJSONObject(checksum) ?: continue
            urls.add("https://" + item.getString("url_location") + item.getString("url_path"))
        }
        return urls
    }

    /** The checksum of the largest version no wider than 2560 px. */
    private fun bestDerivative(derivatives: JSONObject): String? {
        var bestChecksum: String? = null
        var bestWidth = 0
        for (key in derivatives.keys()) {
            val derivative = derivatives.getJSONObject(key)
            val width = derivative.optString("width", "0").toIntOrNull() ?: 0
            if (width in (bestWidth + 1)..2560) {
                bestWidth = width
                bestChecksum = derivative.getString("checksum")
            }
        }
        return bestChecksum
    }

    // ---------- Google Photos ----------

    private fun googlePhotos(link: String): List<String>? {
        val page = getText(link) ?: return null
        val pattern = Regex("https://lh3\\.googleusercontent\\.com/pw/[A-Za-z0-9_\\-]+")
        val urls = LinkedHashSet<String>()
        for (match in pattern.findAll(page)) {
            urls.add(match.value + "=w2560-h1440")
        }
        return urls.toList()
    }

    // ---------- HTTP ----------

    private fun postJson(address: String, body: JSONObject): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(address).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "text/plain")
            connection.outputStream.use { output -> output.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            if (stream == null) {
                return null
            }
            JSONObject(readText(stream))
        } catch (error: Exception) {
            Log.w(TAG, "Album request failed: " + error.javaClass.simpleName)
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** A Google share page, following its redirects only between Google's pages. */
    private fun getText(link: String): String? {
        var address = link
        for (hop in 0..MAX_REDIRECTS) {
            if (!AlbumLinks.isGooglePage(address)) {
                return null
            }
            var connection: HttpURLConnection? = null
            try {
                connection = URL(address).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) GlassHome")
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return null
                    address = URL(URL(address), location).toString()
                    continue
                }
                if (code != 200) {
                    return null
                }
                return readText(connection.inputStream)
            } catch (error: Exception) {
                Log.w(TAG, "Album page failed: " + error.javaClass.simpleName)
                return null
            } finally {
                connection?.disconnect()
            }
        }
        return null
    }

    /** A photo file, following redirects only between the photo servers. */
    private fun downloadTo(link: String, target: File): Boolean {
        var address = link
        for (hop in 0..MAX_REDIRECTS) {
            if (!AlbumLinks.isPhotoDownload(address)) {
                return false
            }
            var connection: HttpURLConnection? = null
            try {
                connection = URL(address).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return false
                    address = URL(URL(address), location).toString()
                    continue
                }
                if (code != 200) {
                    return false
                }
                connection.inputStream.use { input ->
                    FileOutputStream(target).use { output -> AlbumLinks.copyLimited(input, output, MAX_PHOTO_BYTES) }
                }
                return true
            } catch (error: Exception) {
                Log.w(TAG, "Album photo download failed: " + error.javaClass.simpleName)
                return false
            } finally {
                connection?.disconnect()
            }
        }
        return false
    }

    private fun readText(input: java.io.InputStream): String {
        val bytes = java.io.ByteArrayOutputStream()
        input.use { stream -> AlbumLinks.copyLimited(stream, bytes, MAX_TEXT_BYTES) }
        return bytes.toString("UTF-8")
    }

    private fun shortHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        val builder = StringBuilder()
        for (index in 0 until 8) {
            builder.append(String.format("%02x", digest[index]))
        }
        return builder.toString()
    }

    companion object {
        private const val TAG = "GlassHome"
        private const val TIMEOUT_MS = 15_000
        private const val MAX_PHOTOS = 200
        private const val MAX_REDIRECTS = 5
        private const val MAX_TEXT_BYTES = 5L * 1024 * 1024
        private const val MAX_PHOTO_BYTES = 40L * 1024 * 1024
    }
}
