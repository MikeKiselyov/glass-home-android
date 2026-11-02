package com.glasshome.tv

/**
 * The link a phone sends to open on the TV ("Open on the TV" on the website).
 *
 * Only http and https get through: other schemes open things on the TV that
 * no web page should reach (intent:, file:, content:, market:). The check is
 * loose on purpose: browsers open names with "_", non-Latin domains and "|"
 * in a query, which java.net.URI refuses.
 */
object SharedLinks {

    const val MAX_LENGTH = 4096

    /** The first web link in the text (apps share "Title https://..."), or null. */
    fun find(text: String): String? {
        val words = text.trim().split(Regex("\\s+"))
        for (word in words) {
            val lower = word.lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                continue
            }
            if (hasHost(word) && word.length <= MAX_LENGTH) {
                return word
            }
        }
        return null
    }

    /** "https://user@host:port/path" has a non-empty host. */
    private fun hasHost(link: String): Boolean {
        for (character in link) {
            if (character.code < 0x20 || character.code == 0x7F) {
                return false
            }
        }
        var authority = link.substringAfter("://")
        for (end in listOf('/', '?', '#')) {
            authority = authority.substringBefore(end)
        }
        var host = authority.substringAfterLast('@')
        if (host.startsWith("[")) {
            host = host.substringBefore("]") + "]"
            return host.length > 2
        }
        host = host.substringBefore(':')
        return host.isNotEmpty()
    }
}
