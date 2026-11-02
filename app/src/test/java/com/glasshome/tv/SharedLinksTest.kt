package com.glasshome.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The link a phone sends to open on the TV. */
class SharedLinksTest {

    @Test
    fun aPlainLinkIsKept() {
        assertEquals("https://www.youtube.com/watch?v=abc", SharedLinks.find("https://www.youtube.com/watch?v=abc"))
        assertEquals("http://example.com/film", SharedLinks.find("  http://example.com/film \n"))
    }

    /** Apps share "Title https://..." as text: the link inside is the one. */
    @Test
    fun theLinkInsideSharedTextIsFound() {
        assertEquals("https://example.com/a?b=1", SharedLinks.find("Watch this https://example.com/a?b=1 tonight"))
    }

    @Test
    fun onlyWebLinksOpen() {
        assertNull(SharedLinks.find("javascript:alert(1)"))
        assertNull(SharedLinks.find("intent://scan/#Intent;scheme=zxing;end"))
        assertNull(SharedLinks.find("file:///data/data/com.glasshome.tv/shared_prefs/server.xml"))
        assertNull(SharedLinks.find("content://settings/secure"))
        assertNull(SharedLinks.find("market://details?id=com.example"))
    }

    @Test
    fun notALinkIsRefused() {
        assertNull(SharedLinks.find(""))
        assertNull(SharedLinks.find("just some words"))
        assertNull(SharedLinks.find("https://"))
        assertNull(SharedLinks.find("https:///path-without-host"))
    }

    @Test
    fun aVeryLongLinkIsRefused() {
        val long = "https://example.com/" + "a".repeat(SharedLinks.MAX_LENGTH)
        assertNull(SharedLinks.find(long))
    }

    /** Found in review: java.net.URI refused links every browser opens. */
    @Test
    fun linksBrowsersOpenAreKept() {
        assertEquals("https://bücher.example/film", SharedLinks.find("https://bücher.example/film"))
        assertEquals("https://my_host.example.com/", SharedLinks.find("https://my_host.example.com/"))
        assertEquals("https://example.com/a?x={1}|^", SharedLinks.find("https://example.com/a?x={1}|^"))
        assertEquals("http://192.168.1.20:8080/", SharedLinks.find("http://192.168.1.20:8080/"))
    }

    @Test
    fun aBrokenLinkFirstDoesNotHideAGoodOneLater() {
        assertEquals("https://example.com/", SharedLinks.find("https:// and then https://example.com/"))
    }

    @Test
    fun aLinkWithoutAHostIsRefused() {
        assertNull(SharedLinks.find("https://user@/path"))
        assertNull(SharedLinks.find("https://:8080/"))
        assertNull(SharedLinks.find("https://?q=1"))
    }

    @Test
    fun schemeCaseDoesNotMatter() {
        assertEquals("HTTPS://Example.com/", SharedLinks.find("HTTPS://Example.com/"))
    }
}
