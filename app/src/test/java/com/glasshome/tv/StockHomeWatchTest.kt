package com.glasshome.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which windows of the TV's launcher Glass Home covers, in the order the TV announces them. */
class StockHomeWatchTest {

    private val googleTv = "com.google.android.apps.tv.launcherx"
    private val gateway = "com.google.apps.tiktok.nav.gateway.GatewayActivity"
    private val home = "$googleTv.home.HomeActivity"

    @Test
    fun homeScreenIsCovered() {
        val watch = StockHomeWatch()
        assertTrue(watch.shouldCover(googleTv, home, 1_000))
    }

    /**
     * Found on the TV: a voice search opens its results through the gateway,
     * then in HomeActivity 390 ms later, and Glass Home covered them.
     */
    @Test
    fun voiceSearchResultsAreLeftOnScreen() {
        val watch = StockHomeWatch()
        assertFalse(watch.shouldCover(googleTv, gateway, 10_000))
        assertFalse(watch.shouldCover(googleTv, home, 10_390))
    }

    /** Found on the TV: the results came in HomeActivity once more, 950 ms after the gateway. */
    @Test
    fun resultsInMoreThanOneWindowAreLeftOnScreen() {
        val watch = StockHomeWatch()
        watch.shouldCover(googleTv, gateway, 10_000)
        assertFalse(watch.shouldCover(googleTv, home, 10_390))
        assertFalse(watch.shouldCover(googleTv, home, 10_950))
    }

    /** Only the results: Home pressed later from them still brings Glass Home back. */
    @Test
    fun homeScreenAfterTheResultsIsCovered() {
        val watch = StockHomeWatch()
        watch.shouldCover(googleTv, gateway, 10_000)
        watch.shouldCover(googleTv, home, 10_390)
        assertTrue(watch.shouldCover(googleTv, home, 20_000))
    }

    /**
     * Home ("After Google TV" mode) or Back right after the results: the
     * launcher's home screen that follows is covered, not taken for the results.
     */
    @Test
    fun homeScreenRightAfterLeavingTheResultsIsCovered() {
        val watch = StockHomeWatch()
        watch.shouldCover(googleTv, gateway, 10_000)
        watch.shouldCover(googleTv, home, 10_390)
        watch.resultsLeft()
        assertTrue(watch.shouldCover(googleTv, home, 11_500))
    }

    @Test
    fun homeScreenLongAfterADeepLinkIsCovered() {
        val watch = StockHomeWatch()
        watch.shouldCover(googleTv, gateway, 10_000)
        assertTrue(watch.shouldCover(googleTv, home, 10_000 + StockHomeWatch.DEEP_LINK_MS))
    }

    @Test
    fun otherAppsAreNotCovered() {
        val watch = StockHomeWatch()
        assertFalse(watch.shouldCover("com.google.android.youtube.tv", home, 1_000))
    }
}
