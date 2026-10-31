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
 */
class AlbumSync(private val store: WallpaperStore, private val cacheDir: File) {

    /** Returns the number of photos now in the album on the TV, or -1 if the link failed. */
    fun sync(link: String): Int {
        val trimmed = link.trim()
        if (trimmed.isEmpty()) {
            store.removeAlbumPhotosExcept(emptySet())
            return 0
        }
        val photoUrls = when {
            trimmed.contains("icloud.com/sharedalbum") -> iCloudPhotos(trimmed)
            trimmed.contains("photos.app.goo.gl") || trimmed.contains("photos.google.com") -> googlePhotos(trimmed)
            else -> null
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
            val text = stream.bufferedReader().use { reader -> reader.readText() }
            JSONObject(text)
        } catch (error: Exception) {
            Log.w(TAG, "Album request failed: $address", error)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun getText(address: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(address).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) GlassHome")
            if (connection.responseCode != 200) {
                return null
            }
            connection.inputStream.bufferedReader().use { reader -> reader.readText() }
        } catch (error: Exception) {
            Log.w(TAG, "Album page failed: $address", error)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun downloadTo(address: String, target: File): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(address).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode != 200) {
                return false
            }
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            true
        } catch (error: Exception) {
            Log.w(TAG, "Album photo download failed", error)
            false
        } finally {
            connection?.disconnect()
        }
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
    }
}
