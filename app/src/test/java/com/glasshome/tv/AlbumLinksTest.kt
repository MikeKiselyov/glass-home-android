package com.glasshome.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class AlbumLinksTest {

    // ---------- album links ----------

    @Test
    fun recognisesICloudSharedAlbums() {
        assertEquals(AlbumLinks.Kind.ICLOUD, AlbumLinks.kind("https://www.icloud.com/sharedalbum/#B0aGWZuqDGGl9Qg"))
        assertEquals(AlbumLinks.Kind.ICLOUD, AlbumLinks.kind("  https://www.icloud.com/sharedalbum/#B0aGWZuqDGGl9Qg  "))
    }

    @Test
    fun recognisesGooglePhotosLinks() {
        assertEquals(AlbumLinks.Kind.GOOGLE, AlbumLinks.kind("https://photos.app.goo.gl/AbCdEf123"))
        assertEquals(AlbumLinks.Kind.GOOGLE, AlbumLinks.kind("https://photos.google.com/share/AF1Qip?key=abc"))
    }

    /** The old check only looked for the host name anywhere in the text. */
    @Test
    fun refusesOtherHostsThatMentionAnAlbumHost() {
        assertNull(AlbumLinks.kind("https://evil.example/?photos.google.com"))
        assertNull(AlbumLinks.kind("https://evil.example/photos.app.goo.gl/x"))
        assertNull(AlbumLinks.kind("https://photos.google.com.evil.example/share/x"))
        assertNull(AlbumLinks.kind("https://evil.example/icloud.com/sharedalbum/#x"))
        assertNull(AlbumLinks.kind("https://www.icloud.com.evil.example/sharedalbum/#x"))
    }

    @Test
    fun refusesLinksToInternalAddresses() {
        assertNull(AlbumLinks.kind("https://192.168.1.1/sharedalbum/#x"))
        assertNull(AlbumLinks.kind("https://localhost/photos.google.com"))
    }

    @Test
    fun refusesPlainHttpLoginsAndOddPorts() {
        assertNull(AlbumLinks.kind("http://photos.app.goo.gl/AbCdEf123"))
        assertNull(AlbumLinks.kind("https://user@photos.app.goo.gl/AbCdEf123"))
        assertNull(AlbumLinks.kind("https://photos.app.goo.gl:8443/AbCdEf123"))
        assertNull(AlbumLinks.kind("file:///etc/hosts"))
        assertNull(AlbumLinks.kind("not a link"))
    }

    @Test
    fun iCloudLinkNeedsTheSharedAlbumPath() {
        assertNull(AlbumLinks.kind("https://www.icloud.com/photos/"))
    }

    // ---------- iCloud stream host ----------

    @Test
    fun acceptsICloudStreamHosts() {
        assertTrue(AlbumLinks.isICloudStreamHost("p23-sharedstreams.icloud.com"))
        assertTrue(AlbumLinks.isICloudStreamHost("P42-SharedStreams.iCloud.com"))
    }

    @Test
    fun refusesOtherStreamHosts() {
        assertFalse(AlbumLinks.isICloudStreamHost("evil.example"))
        assertFalse(AlbumLinks.isICloudStreamHost("icloud.com.evil.example"))
        assertFalse(AlbumLinks.isICloudStreamHost("192.168.1.1"))
        assertFalse(AlbumLinks.isICloudStreamHost("evil.example/x.icloud.com"))
        assertFalse(AlbumLinks.isICloudStreamHost("evil.example#.icloud.com"))
        assertFalse(AlbumLinks.isICloudStreamHost(""))
    }

    // ---------- redirects and downloads ----------

    @Test
    fun googleRedirectsStayOnGooglePages() {
        assertTrue(AlbumLinks.isGooglePage("https://photos.google.com/share/AF1Qip?key=abc"))
        assertFalse(AlbumLinks.isGooglePage("https://evil.example/share"))
        assertFalse(AlbumLinks.isGooglePage("http://photos.google.com/share"))
        assertFalse(AlbumLinks.isGooglePage("https://192.168.1.1/"))
    }

    @Test
    fun photosComeFromPhotoServersOnly() {
        assertTrue(AlbumLinks.isPhotoDownload("https://cvws.icloud-content.com/S/abc/IMG_1.JPG?o=x"))
        assertTrue(AlbumLinks.isPhotoDownload("https://lh3.googleusercontent.com/pw/AbC=w2560-h1440"))
        assertFalse(AlbumLinks.isPhotoDownload("https://evil.example/photo.jpg"))
        assertFalse(AlbumLinks.isPhotoDownload("https://192.168.1.1/photo.jpg"))
        assertFalse(AlbumLinks.isPhotoDownload("http://lh3.googleusercontent.com/pw/AbC"))
        assertFalse(AlbumLinks.isPhotoDownload("https://lh3.googleusercontent.com.evil.example/pw/AbC"))
    }

    // ---------- size limit ----------

    @Test
    fun copiesUpToTheLimit() {
        val output = ByteArrayOutputStream()
        AlbumLinks.copyLimited(ByteArrayInputStream(ByteArray(1000)), output, 1000)
        assertEquals(1000, output.size())
    }

    @Test(expected = IOException::class)
    fun stopsPastTheLimit() {
        AlbumLinks.copyLimited(ByteArrayInputStream(ByteArray(1001)), ByteArrayOutputStream(), 1000)
    }

    // ---------- pasted text ----------

    @Test
    fun findsTheLinkInPastedText() {
        assertEquals(
            "https://www.icloud.com/sharedalbum/#B0aGWZuqDGGl9Qg",
            AlbumLinks.extractLink("Our album: https://www.icloud.com/sharedalbum/#B0aGWZuqDGGl9Qg enjoy"),
        )
        assertEquals("https://photos.app.goo.gl/AbC", AlbumLinks.extractLink("  https://photos.app.goo.gl/AbC\n"))
    }

    @Test
    fun textWithoutALinkIsKeptForTheCheckToRefuse() {
        assertEquals("not a link", AlbumLinks.extractLink("  not a link "))
        assertNull(AlbumLinks.kind(AlbumLinks.extractLink("http://photos.app.goo.gl/AbC")))
    }
}
