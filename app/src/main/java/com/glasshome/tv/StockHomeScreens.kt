package com.glasshome.tv

/**
 * Which windows of the TV's own launcher are its home screen, the ones that
 * [HomeOverrideService] replaces with Glass Home.
 *
 * Google TV's launcher opens more than its home screen: the settings panel
 * (the remote's Settings key), notifications, account checks, profiles.
 * Those stay on screen. The older launchers were not checked on a TV, so any
 * of their windows counts, as it did before.
 */
object StockHomeScreens {

    private const val GOOGLE_TV = "com.google.android.apps.tv.launcherx"

    /**
     * Seen on the TV (`uiautomator events`): home.HomeActivity and
     * home.VanillaModeHomeActivity. Other Google TV builds may add more home
     * screens there; the settings panel is dashboard.*, profiles profile.*.
     */
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
        if (packageName != GOOGLE_TV) {
            return true
        }
        return className != null && className.startsWith(GOOGLE_TV_HOME_SCREENS)
    }
}
