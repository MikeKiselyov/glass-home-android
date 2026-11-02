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

    /** Window class names seen on the TV (`uiautomator events`). */
    private val GOOGLE_TV_HOME_SCREENS = setOf(
        "$GOOGLE_TV.home.HomeActivity",
        "$GOOGLE_TV.home.VanillaModeHomeActivity",
    )

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
        return GOOGLE_TV_HOME_SCREENS.contains(className)
    }
}
