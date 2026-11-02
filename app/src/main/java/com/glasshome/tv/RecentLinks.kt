package com.glasshome.tv

/**
 * The last links sent to the TV, newest first, for opening a series again
 * with one tap on the website. Kept on the TV (a shortcut sends without the
 * website); "Clear" on the website empties it.
 */
class RecentLinks(saved: List<String>) {

    private val kept = ArrayList<String>()

    init {
        for (link in saved) {
            if (SharedLinks.find(link) == link && kept.size < MAX) {
                kept.add(link)
            }
        }
    }

    val links: List<String>
        get() = ArrayList(kept)

    fun add(link: String) {
        kept.remove(link)
        kept.add(0, link)
        while (kept.size > MAX) {
            kept.removeAt(kept.size - 1)
        }
    }

    fun clear() {
        kept.clear()
    }

    companion object {
        const val MAX = 10
    }
}
