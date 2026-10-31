package com.glasshome.tv

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Links that are not shared albums are refused before anything is fetched.
 * The rules are in AlbumLinksTest; this makes sure sync() uses them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AlbumSyncTest {

    private fun sync(link: String): Int {
        val context = RuntimeEnvironment.getApplication()
        return AlbumSync(WallpaperStore(context), context.cacheDir).sync(link)
    }

    @Test
    fun refusesAHostThatOnlyMentionsAnAlbumHost() {
        assertEquals(-1, sync("https://evil.example/?photos.google.com"))
        assertEquals(-1, sync("https://192.168.1.1/icloud.com/sharedalbum/#x"))
    }

    @Test
    fun refusesPlainHttp() {
        assertEquals(-1, sync("http://photos.app.goo.gl/AbCdEf"))
    }

    @Test
    fun emptyLinkClearsTheAlbum() {
        assertEquals(0, sync("   "))
    }
}
