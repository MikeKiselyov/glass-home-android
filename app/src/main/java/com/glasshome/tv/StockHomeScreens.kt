package com.glasshome.tv

/**
 * Which windows of the TV's own launcher are its home screen, the ones that
 * [HomeOverrideService] replaces with Glass Home.
 *
 * Google TV's launcher opens more than its home screen: the settings panel
 * (the remote's Settings key, dashboard.DashboardActivity), account checks,
 * profiles. Those are announced under their own class names and stay on
 * screen. Its home screen is home.*, and coming back to the front (the Home
 * key) it is announced as a plain android.widget.FrameLayout: that counts too,
 * as any window did before. The older launchers were not checked on a TV, so
 * any of their windows counts.
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
        if (!className.startsWith("$GOOGLE_TV.")) {
            // A plain window (FrameLayout): the home screen coming back.
            return true
        }
        return className.startsWith(GOOGLE_TV_HOME_SCREENS)
    }
}
