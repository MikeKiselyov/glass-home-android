package com.glasshome.tv

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.graphics.drawable.Drawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.util.Log

/** A tile on the dock: an installed app (packageName) or an HDMI port (inputId). */
class DockItem(
    val key: String,
    val label: String,
    val packageName: String?,
    val inputId: String?,
    val icon: Drawable?,
    /** The app's own icon; null for HDMI devices. */
    val originalIcon: Drawable?,
    /** HDMI devices: switched on (the TV reports the input as connected and active). */
    val isOn: Boolean = false,
    /**
     * Changes when the icon does: the icon chosen for this tile, and for apps
     * when the app was last updated (an update can bring a new icon).
     */
    val iconVersion: String = "",
)

/**
 * Installed TV apps and connected HDMI ports, in the viewer's order, minus the
 * hidden ones. Order and hidden keys live in SharedPreferences.
 */
class AppCatalog(private val context: Context, private val icons: IconStore) {

    private val prefs: SharedPreferences = context.getSharedPreferences("dock", Context.MODE_PRIVATE)

    fun dockItems(): List<DockItem> {
        val hidden = hiddenKeys()
        val result = ArrayList<DockItem>()
        for (item in allItems()) {
            if (!hidden.contains(item.key)) {
                result.add(item)
            }
        }
        return result
    }

    /** Every tile in dock order, hidden ones included (for the phone website). */
    fun everyItem(): List<DockItem> {
        return allItems()
    }

    fun isHidden(key: String): Boolean {
        return hiddenKeys().contains(key)
    }

    fun unhide(key: String) {
        val hidden = HashSet(hiddenKeys())
        hidden.remove(key)
        prefs.edit().putStringSet("hidden", hidden).apply()
    }

    fun hiddenCount(): Int {
        return hiddenKeys().size
    }

    fun hide(key: String) {
        val hidden = HashSet(hiddenKeys())
        hidden.add(key)
        prefs.edit().putStringSet("hidden", hidden).apply()
    }

    fun showAll() {
        prefs.edit().remove("hidden").apply()
    }

    /** Moves a tile one place left (-1) or right (+1) among the visible tiles. */
    fun move(key: String, step: Int) {
        val keys = ArrayList<String>()
        for (item in allItems()) {
            keys.add(item.key)
        }
        val from = keys.indexOf(key)
        val to = from + step
        if (from < 0 || to < 0 || to >= keys.size) {
            return
        }
        keys.removeAt(from)
        keys.add(to, key)
        prefs.edit().putString("order", keys.joinToString(",")).apply()
    }

    fun open(item: DockItem): Boolean {
        if (item.key == SETTINGS_KEY) {
            val app = context.applicationContext as? GlassHomeApp
            if (app != null) {
                return start(app.settingsIntent(context))
            }
            return start(SettingsActivity.intent(context, null))
        }
        val packageName = item.packageName
        if (packageName != null) {
            val packageManager = context.packageManager
            val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
                ?: packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            return start(intent)
        }
        val inputId = item.inputId ?: return false
        val intent = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId))
        return start(intent)
    }

    /**
     * True for apps the viewer installed (or the TV maker's ones that are not
     * part of the system): Android shows its own "Uninstall?" question for them.
     * Apps built into the system can only be switched off, in App info.
     */
    fun canUninstall(item: DockItem): Boolean {
        val packageName = appPackage(item) ?: return false
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            info.flags and ApplicationInfo.FLAG_SYSTEM == 0
        } catch (notFound: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** True for app tiles; HDMI devices and Glass Home's own settings tile have no app to show. */
    fun hasAppInfo(item: DockItem): Boolean {
        return appPackage(item) != null
    }

    /** Asks Android to uninstall the app; Android shows the question and does it. */
    fun uninstall(item: DockItem): Boolean {
        val packageName = appPackage(item) ?: return false
        return start(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
    }

    /** The system's App info screen: force stop, clear data, switch off, uninstall updates. */
    fun openAppInfo(item: DockItem): Boolean {
        val packageName = appPackage(item) ?: return false
        return start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun appPackage(item: DockItem): String? {
        if (item.key == SETTINGS_KEY || item.inputId != null) {
            return null
        }
        return item.packageName
    }

    private fun allItems(): List<DockItem> {
        val items = ArrayList<DockItem>()
        items.addAll(apps())
        items.addAll(connectedInputs())
        items.add(settingsTile())

        val savedOrder = (prefs.getString("order", "") ?: "").split(",")
        items.sortWith { first, second ->
            val firstRank = rank(first, savedOrder)
            val secondRank = rank(second, savedOrder)
            if (firstRank != secondRank) {
                firstRank - secondRank
            } else {
                first.label.compareTo(second.label, ignoreCase = true)
            }
        }
        return items
    }

    /** Saved order first; new items fall back to the default order. */
    private fun rank(item: DockItem, savedOrder: List<String>): Int {
        val savedIndex = savedOrder.indexOf(item.key)
        if (savedIndex >= 0) {
            return savedIndex
        }
        val preferredIndex = PREFERRED.indexOf(item.key)
        if (preferredIndex >= 0) {
            return 1000 + preferredIndex
        }
        if (item.inputId != null) {
            return 1500
        }
        val lastIndex = LAST.indexOf(item.key)
        if (lastIndex >= 0) {
            return 3000 + lastIndex
        }
        return 2000
    }

    private fun apps(): List<DockItem> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val activities = packageManager.queryIntentActivities(intent, 0)

        val result = ArrayList<DockItem>()
        val seenPackages = HashSet<String>()
        for (resolveInfo in activities) {
            val packageName = resolveInfo.activityInfo.packageName
            if (packageName == context.packageName || EXCLUDED.contains(packageName)) {
                continue
            }
            if (!seenPackages.add(packageName)) {
                continue
            }
            val label = LABELS[packageName] ?: resolveInfo.loadLabel(packageManager).toString()
            val original = packageManager.getApplicationIcon(resolveInfo.activityInfo.applicationInfo)
            val icon = icons.resolve(packageName, original, null)
            val iconVersion = (icons.chosenId(packageName) ?: "") + "|" + lastUpdate(packageName)
            result.add(DockItem(packageName, label, packageName, null, icon, original, iconVersion = iconVersion))
        }
        return result
    }

    /**
     * Glass Home's own settings (the phone website, on the TV), so the remote
     * alone is enough to change the theme and the rest. Last on the dock unless
     * moved; it can be hidden like any tile.
     */
    private fun settingsTile(): DockItem {
        val original = context.getDrawable(R.drawable.icon)
        val icon = icons.resolve(SETTINGS_KEY, original, null)
        val iconVersion = icons.chosenId(SETTINGS_KEY) ?: ""
        return DockItem(SETTINGS_KEY, I18n.t("tv.settings_tile"), context.packageName, null, icon, original, iconVersion = iconVersion)
    }

    /** HDMI ports with something plugged in, named by HDMI-CEC when the device reports a name. */
    private fun connectedInputs(): List<DockItem> {
        val result = ArrayList<DockItem>()
        val tvInputManager = context.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager?
            ?: return result
        val allInputs = tvInputManager.tvInputList

        val cecNameByParent = HashMap<String, String>()
        for (input in allInputs) {
            val parentId = input.parentId
            if (parentId != null) {
                cecNameByParent[parentId] = input.loadLabel(context).toString()
            }
        }

        for (input in allInputs) {
            if (input.type != TvInputInfo.TYPE_HDMI || input.parentId != null) {
                continue
            }
            val cecName = cecNameByParent[input.id]
            val connected = tvInputManager.getInputState(input.id) == TvInputManager.INPUT_STATE_CONNECTED
            if (cecName == null && !connected) {
                continue
            }
            val label = cecName ?: input.loadLabel(context).toString()
            val key = "input:" + input.id
            val icon = icons.resolve(key, null, IconStore.deviceType(label))
            val iconVersion = icons.chosenId(key) ?: ""
            result.add(DockItem(key, label, null, input.id, icon, null, connected, iconVersion))
        }
        return result
    }

    private fun lastUpdate(packageName: String): Long {
        return try {
            context.packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        } catch (notFound: Exception) {
            0L
        }
    }

    private fun hiddenKeys(): Set<String> {
        return prefs.getStringSet("hidden", emptySet()) ?: emptySet()
    }

    private fun start(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (notFound: ActivityNotFoundException) {
            Log.w(TAG, "No activity for $intent")
            false
        } catch (denied: SecurityException) {
            Log.w(TAG, "Not allowed to start $intent", denied)
            false
        }
    }

    companion object {
        private const val TAG = "GlassHome"

        private val PREFERRED = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.spotify.tv.android",
        )
        /** The dock tile that opens Glass Home's settings on the TV. */
        const val SETTINGS_KEY = "glasshome:settings"

        private val LAST = listOf(
            "com.android.vending",
            "com.android.tv.settings",
            SETTINGS_KEY,
        )
        private val EXCLUDED = setOf(
            "com.mediatek.androidbox",
        )
        private val LABELS = mapOf(
            "com.google.android.videos" to "Google TV",
            "com.android.vending" to "Play Store",
            "com.android.tv.settings" to "Settings",
        )
    }
}
