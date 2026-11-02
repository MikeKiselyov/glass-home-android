package com.glasshome.tv

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Google TV keeps its own launcher on the Home button whatever app is chosen,
 * and disabling that launcher (or its setup wizard) is risky. This service
 * gets Glass Home on screen instead, two ways:
 *
 * - It sees the Home key from the remote before the TV does (key filtering)
 *   and, in the default "catch" mode, opens Glass Home and swallows the key:
 *   Google TV does not start and its screen does not flash. See [HomeButton].
 * - When Google TV's home screen opens anyway (the "after" mode, or a way to
 *   it other than the Home key), it brings Glass Home to the front right away.
 *   It only receives window events from the stock launchers listed in
 *   res/xml/home_override.xml, and only their home screen counts
 *   ([StockHomeScreens]): Google TV's settings panel is a launcher window too.
 *
 * It never reads what is on screen. Every key from the remote passes through
 * [onKeyEvent], so that stays a few comparisons with no work behind them.
 *
 * Enabled with scripts/install/set-home.sh (or Settings > Accessibility > Glass Home).
 * Force-stopping Glass Home switches it off (Android does that for every
 * accessibility service); set-home.sh turns it on again.
 */
class HomeOverrideService : AccessibilityService() {

    private val settings by lazy { HomeSettings(this) }
    private val homeButton = HomeButton()

    /**
     * The screensaver is on screen (Android announces it starting and stopping).
     * Android has no public way to ask, so a service started while the
     * screensaver already runs (Glass Home updated during it) counts it as off
     * until it stops once: that one Home press opens Glass Home behind it.
     */
    private var dreaming = false

    private val dreamReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            dreaming = intent.action == Intent.ACTION_DREAMING_STARTED
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_DREAMING_STARTED)
        filter.addAction(Intent.ACTION_DREAMING_STOPPED)
        registerReceiver(dreamReceiver, filter)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(dreamReceiver)
        } catch (notRegistered: IllegalArgumentException) {
            // Already gone.
        }
        super.onDestroy()
    }

    /**
     * Android connects the service again after Glass Home is updated. While the
     * update went in, Android showed the TV's own home screen, before this
     * service was back to catch it: put Glass Home back if it asked for that.
     */
    public override fun onServiceConnected() {
        super.onServiceConnected()
        if (Updater.takeReopenRequest(this, System.currentTimeMillis())) {
            openGlassHome()
        }
    }

    private fun screenAsleep(): Boolean {
        if (dreaming) {
            return true
        }
        val power = getSystemService(PowerManager::class.java)
        return power != null && !power.isInteractive
    }

    public override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_HOME && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            // For scripts/measure/home-key.sh: from the key press to Glass Home's first frame.
            Log.i(TIMING_TAG, "home key " + event.eventTime)
        }
        if (event.keyCode != KeyEvent.KEYCODE_HOME) {
            return false
        }
        val action = homeButton.decide(event.keyCode, event.action, event.repeatCount, settings.homeButton, screenAsleep())
        if (action == HomeButton.Action.PASS) {
            return false
        }
        if (action == HomeButton.Action.OPEN_HOME) {
            openGlassHome()
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }
        val packageName = event.packageName?.toString() ?: return
        if (!StockHomeScreens.isHomeScreen(packageName, event.className?.toString())) {
            return
        }
        if (HomeScreenState.inFront) {
            // Found on the TV: Google TV's launcher also draws notifications
            // ("Wireless debugging connected") over Glass Home. Opening Glass Home
            // again then closed whatever was open on it, the update question too.
            return
        }
        openGlassHome()
    }

    private fun openGlassHome() {
        val intent = Intent(this, MainActivity::class.java)
        // No opening animation: the home screen is simply there, as the stock one is.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        startActivity(intent)
    }

    override fun onInterrupt() {
        // Nothing to stop: the service only reacts to keys and window changes.
    }

    companion object {
        const val TIMING_TAG = "GlassHomeTiming"
    }
}
