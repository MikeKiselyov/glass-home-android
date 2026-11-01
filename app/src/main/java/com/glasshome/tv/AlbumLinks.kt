package com.glasshome.tv

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

/**
 * Which addresses the album sync may talk to. The album link comes from the
 * phone website, so it is checked by its exact host, never by "contains":
 * https://evil.example/?photos.google.com must not be fetched.
 */
object AlbumLinks {

    enum class Kind { ICLOUD, GOOGLE }

    private val GOOGLE_PAGE_HOSTS = setOf("photos.app.goo.gl", "photos.google.com")

    /**
     * The first https address in what was pasted ("Look: https://www.icloud.com/...
     * enjoy"), or the text itself when there is none.
     */
    fun extractLink(pasted: String): String {
        val start = pasted.indexOf("https://")
        if (start < 0) {
            return pasted.trim()
        }
        var end = start
        while (end < pasted.length && !pasted[end].isWhitespace()) {
            end += 1
        }
        return pasted.substring(start, end)
    }

    /** The kind of a shared album link, or null when it is neither. */
    fun kind(link: String): Kind? {
        val parsed = parse(link) ?: return null
        val host = parsed.host
        if ((host == "www.icloud.com" || host == "icloud.com") && parsed.path.startsWith("/sharedalbum")) {
            return Kind.ICLOUD
        }
        if (GOOGLE_PAGE_HOSTS.contains(host)) {
            return Kind.GOOGLE
        }
        return null
    }

    /** iCloud names the server for an album ("p42-sharedstreams.icloud.com"); only its own hosts are accepted. */
    fun isICloudStreamHost(host: String): Boolean {
        val lower = host.lowercase()
        if (!lower.endsWith(".icloud.com")) {
            return false
        }
        for (character in lower) {
            val allowed = character in 'a'..'z' || character in '0'..'9' || character == '-' || character == '.'
            if (!allowed) {
                return false
            }
        }
        return true
    }

    /** A Google share link redirects between Google's own pages only. */
    fun isGooglePage(address: String): Boolean {
        val parsed = parse(address) ?: return false
        return GOOGLE_PAGE_HOSTS.contains(parsed.host)
    }

    /** Photo files come from iCloud's or Google's photo servers only. */
    fun isPhotoDownload(address: String): Boolean {
        val parsed = parse(address) ?: return false
        val host = parsed.host
        if (host.endsWith(".icloud-content.com") || host.endsWith(".icloud.com")) {
            return true
        }
        return host == "lh3.googleusercontent.com"
    }

    /** Copies at most [limit] bytes; throws when the stream has more. */
    fun copyLimited(input: InputStream, output: OutputStream, limit: Long) {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                return
            }
            total += read
            if (total > limit) {
                throw IOException("More than $limit bytes")
            }
            output.write(buffer, 0, read)
        }
    }

    private class Parsed(val host: String, val path: String)

    /** https only, with a host and no login or odd port; null for anything else. */
    private fun parse(address: String): Parsed? {
        val uri = try {
            URI(address.trim())
        } catch (error: Exception) {
            return null
        }
        if (uri.scheme?.lowercase() != "https") {
            return null
        }
        if (uri.host.isNullOrEmpty() || uri.rawUserInfo != null) {
            return null
        }
        if (uri.port != -1 && uri.port != 443) {
            return null
        }
        return Parsed(uri.host.lowercase(), uri.rawPath ?: "")
    }
}
