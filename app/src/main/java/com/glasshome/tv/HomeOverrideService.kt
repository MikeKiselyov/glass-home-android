package com.glasshome.tv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Google TV keeps its own launcher on the Home button whatever app is chosen,
 * and disabling that launcher (or its setup wizard) is risky. Instead, this
 * service notices the stock home screen opening and brings Glass Home to the
 * front right away. It only receives events from the stock launchers listed in
 * res/xml/home_override.xml; other apps are invisible to it.
 *
 * Enabled with scripts/set-home.sh (or Settings > Accessibility > Glass Home).
 */
class HomeOverrideService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }
        val packageName = event.packageName?.toString() ?: return
        if (!STOCK_HOMES.contains(packageName)) {
            return
        }
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        startActivity(intent)
    }

    override fun onInterrupt() {
        // Nothing to stop: the service only reacts to window changes.
    }

    companion object {
        val STOCK_HOMES = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
        )
    }
}
