package com.glasshome.tv

import java.net.URI
import java.net.URISyntaxException

/**
 * The link a phone sends to open on the TV ("Open on the TV" on the website).
 *
 * Only http and https get through: other schemes open things on the TV that
 * no web page should reach (intent:, file:, content:, market:).
 */
object SharedLinks {

    const val MAX_LENGTH = 4096

    /** The first web link in the text (apps share "Title https://..."), or null. */
    fun find(text: String): String? {
        val words = text.trim().split(Regex("\\s+"))
        for (word in words) {
            val lower = word.lowercase()
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                return checked(word)
            }
        }
        return null
    }

    private fun checked(link: String): String? {
        if (link.length > MAX_LENGTH) {
            return null
        }
        val uri = try {
            URI(link)
        } catch (notALink: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return null
        }
        if (uri.host.isNullOrEmpty()) {
            return null
        }
        return link
    }
}
