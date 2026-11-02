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

    /**
     * Found on the TV: the remote's Settings key opens Google TV's settings
     * panel, a window of its launcher, and Glass Home closed it at once.
     */
    @Test
    fun googleTvSettingsPanelIsNot() {
        assertFalse(StockHomeScreens.isHomeScreen(googleTv, "$googleTv.dashboard.DashboardActivity"))
    }

    /** Notifications, account checks and profiles come from the launcher too. */
    @Test
    fun otherGoogleTvWindowsAreNot() {
        assertFalse(StockHomeScreens.isHomeScreen(googleTv, "android.widget.FrameLayout"))
        assertFalse(StockHomeScreens.isHomeScreen(googleTv, "$googleTv.profile.core.AccountVerificationActivity"))
        assertFalse(StockHomeScreens.isHomeScreen(googleTv, null))
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
