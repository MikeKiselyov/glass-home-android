package com.glasshome.tv

import org.junit.Assert.assertEquals
import org.junit.Test

/** What the website's log may say about a request. */
class RequestLogTest {

    /** Found in review: a failed shortcut request would have put the key in the TV's log. */
    @Test
    fun theShareKeyNeverReachesTheLog() {
        assertEquals("/send/…", RequestLog.path("/send/3f9c0a1b2c3d4e5f60718293a4b5c6d7"))
        assertEquals("/api/open", RequestLog.path("/api/open"))
    }
}
