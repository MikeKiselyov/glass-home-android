package com.glasshome.tv

/**
 * Whether Glass Home's home screen is the activity in front right now. Set by
 * MainActivity, read by HomeOverrideService and the updater, all in the
 * launcher's process.
 */
object HomeScreenState {
    @Volatile
    var inFront: Boolean = false

    /** The screensaver is on screen; set by HomeOverrideService from Android's broadcasts. */
    @Volatile
    var dreaming: Boolean = false
}
