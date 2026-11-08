package com.glasshome.tv

/**
 * Which windows of the TV's own launcher are its home screen, the ones that
 * [HomeOverrideService] replaces with Glass Home.
 *
 * Google TV's launcher opens more than its home screen: the settings panel
 * (the remote's Settings key, dashboard.DashboardActivity), account checks,
 * profiles. Those are announced under their own class names and stay on
 * screen. Its home screen is home.*, and coming back to the front (the Home
 * key) it is announced as a plain android.widget.FrameLayout: that counts too.
 * Voice search results come through a deep link announced as
 * com.google.apps.tiktok.nav.gateway.GatewayActivity: not a home screen.
 * The older launchers were not checked on a TV, so any of their windows counts.
 */
object StockHomeScreens {

    private const val GOOGLE_TV = "com.google.android.apps.tv.launcherx"

    /** Seen on the TV (`uiautomator events`): home.HomeActivity, home.VanillaModeHomeActivity. */
    private const val GOOGLE_TV_HOME_SCREENS = "$GOOGLE_TV.home."

    /** Also listed in res/xml/home_override.xml: the service only hears from these. */
    val LAUNCHERS = setOf(
        GOOGLE_TV,
        "com.google.android.tvlauncher",
        "com.google.android.leanbacklauncher",
    )

    fun isHomeScreen(packageName: String, className: String?): Boolean {
        if (!LAUNCHERS.contains(packageName)) {
            return false
        }
        if (packageName != GOOGLE_TV || className == null) {
            return true
        }
        if (className.startsWith("android.")) {
            // A plain window (FrameLayout): the home screen coming back.
            return true
        }
        return className.startsWith(GOOGLE_TV_HOME_SCREENS)
    }
}

/**
 * Decides, window by window, whether [HomeOverrideService] covers the TV's
 * launcher with Glass Home.
 *
 * Found on the TV: a voice search opens its results through a deep link to the
 * launcher (its gateway window), then shows them in home.HomeActivity, the
 * same window as the home screen, twice. So for a moment after a deep link
 * the launcher's windows are the results and stay.
 */
class StockHomeWatch {

    /** When the last deep link window opened (uptime ms), or null. */
    private var deepLinkAt: Long? = null

    fun shouldCover(packageName: String, className: String?, now: Long): Boolean {
        if (packageName == GOOGLE_TV && className == GATEWAY) {
            deepLinkAt = now
            return false
        }
        if (!StockHomeScreens.isHomeScreen(packageName, className)) {
            return false
        }
        val openedAt = deepLinkAt ?: return true
        // The results come in more than one window: HomeActivity twice.
        return now - openedAt >= DEEP_LINK_MS
    }

    /** Home or Back pressed: whatever the launcher opens next is not the results. */
    fun resultsLeft() {
        deepLinkAt = null
    }

    companion object {
        /** Seen on the TV: HomeActivity 470 and 950 ms after the gateway. */
        const val DEEP_LINK_MS = 3_000L

        private const val GOOGLE_TV = "com.google.android.apps.tv.launcherx"
        private const val GATEWAY = "com.google.apps.tiktok.nav.gateway.GatewayActivity"
    }
}
