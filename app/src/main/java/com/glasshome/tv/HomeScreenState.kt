package com.glasshome.tv

/**
 * Whether Glass Home's home screen is the activity in front right now. Set by
 * MainActivity, read by HomeOverrideService and the updater, all in the
 * launcher's process.
 */
object HomeScreenState {
    @Volatile
    var inFront: Boolean = false
}
