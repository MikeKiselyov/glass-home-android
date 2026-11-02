package com.glasshome.tv

/** What the website's log may say about a request: never the share key in /send/<key>. */
object RequestLog {

    fun path(path: String): String {
        if (path.startsWith("/send/")) {
            return "/send/…"
        }
        return path
    }
}
