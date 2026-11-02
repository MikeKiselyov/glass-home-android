package com.glasshome.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which windows of the TV's own launcher are its home screen. */
class StockHomeScreensTest {

    private val googleTv = "com.google.android.apps.tv.launcherx"

    @Test
    fun googleTvHomeScreenIsOne() {
        assertTrue(StockHomeScreens.isHomeScreen(googleTv, "$googleTv.home.HomeActivity"))
        assertTrue(StockHomeScreens.isHomeScreen(googleTv, "$googleTv.home.VanillaModeHomeActivity"))
    }

    /** Found in review: another Google TV build may name its home screen differently. */
    @Test
    fun anotherHomeScreenOfGoogleTvIsOneToo() {
        assertTrue(StockHomeScreens.isHomeScreen(googleTv, "$googleTv.home.KidsHomeActivity"))
    }

    /**
     * Found on the TV: the remote's Settings key opens Google TV's settings
     * panel, a window of its launcher, and Glass Home closed it at once.
     */
    @Test
    fun googleTvSettingsPanelIsNot() {
        assertFalse(StockHomeScreens.isHomeScreen(googleTv, "$googleTv.dashboard.DashboardActivity"))
    }

    /** Account checks and profiles are screens of the launcher too. */
    @Test
    fun otherGoogleTvScreensAreNot() {
        assertFalse(StockHomeScreens.isHomeScreen(googleTv, "$googleTv.profile.core.AccountVerificationActivity"))
        assertFalse(StockHomeScreens.isHomeScreen(googleTv, "$googleTv.entity.EntityActivity"))
    }

    /**
     * Found on the TV: when Google TV brings its home screen back (the Home
     * key), the window is announced as a plain FrameLayout, not HomeActivity,
     * and Glass Home stayed behind it.
     */
    @Test
    fun googleTvHomeComingBackAsAPlainWindowIsOne() {
        assertTrue(StockHomeScreens.isHomeScreen(googleTv, "android.widget.FrameLayout"))
        assertTrue(StockHomeScreens.isHomeScreen(googleTv, null))
    }

    /** Not checked on a TV with these launchers: any of their windows still counts. */
    @Test
    fun olderLaunchersCountAsBefore() {
        assertTrue(StockHomeScreens.isHomeScreen("com.google.android.tvlauncher", "android.widget.FrameLayout"))
        assertTrue(StockHomeScreens.isHomeScreen("com.google.android.leanbacklauncher", null))
    }

    @Test
    fun otherAppsAreNot() {
        assertFalse(StockHomeScreens.isHomeScreen("com.google.android.youtube.tv", "$googleTv.home.HomeActivity"))
    }
}
