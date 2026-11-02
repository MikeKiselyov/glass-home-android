package com.glasshome.tv

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Updates Glass Home from its GitHub releases, with no store and no other app,
 * and only when the viewer says so.
 *
 * Once a day it asks GitHub for the latest release. A newer one is downloaded
 * and checked against the SHA-256 GitHub lists for it. The home screen then
 * asks: Install, Later (asks again the next day) or Skip this version. Nothing
 * is installed without "Install", there or on the phone website. After
 * "Install", Android installs it straight away when Glass Home may update
 * itself (Android 12+, Glass Home is its own installer and may install apps:
 * what scripts/setup.sh and scripts/install/deploy.sh set up); otherwise Android asks
 * its own "Update this app?" first. Checking can be switched off on the website.
 */
class Updater(
    private val context: Context,
    /** Tests answer for GitHub here; null asks GitHub. */
    private val fetchForTests: ((String) -> String?)? = null,
) {

    private val prefs: SharedPreferences = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val executor = Background.executor("glasshome-update")
    private val handler = Handler(Looper.getMainLooper())
    private val folder = File(context.filesDir, "update")

    /** What the website shows; one of the STATE_ values. */
    @Volatile
    var state: String = STATE_IDLE
        private set

    @Volatile
    var latest: Release? = null
        private set

    /** Android's confirmation screen for an install that needs one, until it is shown. */
    @Volatile
    private var confirmation: Intent? = null

    val installedVersion: String
        get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    /** Builds from source with the debug key can't take a release signed with the release key. */
    val isDevelopmentBuild: Boolean
        get() = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** false: Glass Home never asks GitHub for updates. */
    var checksEnabled: Boolean
        get() = prefs.getBoolean("checks", true)
        set(value) = prefs.edit().putBoolean("checks", value).apply()

    var lastError: String
        get() = prefs.getString("last_error", "") ?: ""
        private set(value) = prefs.edit().putString("last_error", value).apply()

    val lastCheckMillis: Long
        get() = prefs.getLong("last_check", 0L)

    /**
     * After a restart: an update downloaded before it is ready again, without
     * waiting for the next check.
     */
    fun restore() {
        executor.execute {
            removeOldDownloads()
            val release = savedRelease() ?: return@execute
            if (!UpdateCheck.isNewer(release.version, installedVersion)) {
                return@execute
            }
            latest = release
            val apk = apkFile(release)
            if (apk.exists() && sha256(apk) == release.sha256) {
                if (isSignedLikeThisApp(apk)) {
                    state = STATE_READY
                } else {
                    state = STATE_OTHER_KEY
                }
            }
        }
    }

    /** Checks GitHub if the last check is a day old. Called every few hours. */
    fun checkIfDue() {
        if (isDevelopmentBuild || !checksEnabled) {
            return
        }
        if (UpdateCheck.isDue(lastCheckMillis, System.currentTimeMillis())) {
            check()
        }
    }

    /** Asks GitHub for the latest release now and downloads it when it is newer. */
    fun check() {
        executor.execute { checkNow() }
    }

    /** The version the home screen should ask about now, or null. */
    fun versionToOffer(nowMillis: Long): String? {
        val release = latest ?: return null
        if (state != STATE_READY) {
            return null
        }
        val skipped = prefs.getString("skipped_version", "") ?: ""
        val askAgainAt = prefs.getLong("ask_again_at", 0L)
        if (!checksEnabled || !UpdateCheck.shouldOffer(release.version, skipped, askAgainAt, nowMillis)) {
            return null
        }
        return release.version
    }

    /**
     * "Install" on the home screen or the website. [reopenHome]: Glass Home was
     * on the TV's screen when asked, so it comes back there after the update
     * instead of the TV's own home screen, which Android opens while it is gone.
     */
    @Synchronized
    fun install(reopenHome: Boolean) {
        // Only a download that passed the SHA-256 and signing key checks is READY;
        // INSTALLING is set here, not on the worker, so a second press does nothing.
        if (state != STATE_READY) {
            return
        }
        state = STATE_INSTALLING
        if (reopenHome) {
            prefs.edit().putLong(REOPEN_KEY, System.currentTimeMillis()).apply()
        }
        handler.postDelayed(installTimeout, INSTALL_TIMEOUT_MS)
        executor.execute { installNow() }
    }

    /** Android never answered about the install: offer it again rather than stay stuck. */
    private val installTimeout = Runnable {
        if (state == STATE_INSTALLING) {
            Log.w(TAG, "No answer about the install; ready again")
            state = STATE_READY
        }
    }

    /** "Later": asked again the next day. */
    fun later(nowMillis: Long) {
        prefs.edit().putLong("ask_again_at", nowMillis + UpdateCheck.CHECK_EVERY_MS).apply()
    }

    /** "Skip this version": not asked again until a newer one comes out. */
    fun skip() {
        val release = latest ?: return
        prefs.edit().putString("skipped_version", release.version).apply()
        // Android's own question for this version must not come up after all.
        confirmation = null
    }

    /**
     * The home screen came in front: Android's own question for an install the
     * viewer already said yes to, if it is still waiting.
     */
    fun showWaitingConfirmation(context: Context): Boolean {
        val waiting = confirmation ?: return false
        confirmation = null
        startConfirmation(context, waiting)
        return true
    }

    /** Called by [UpdateReceiver] with what Android said about the install. */
    fun onInstallResult(status: Int, message: String?, confirm: Intent?) {
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION && confirm != null) {
            state = STATE_CONFIRM
            confirmation = confirm
            // Shown now if the home screen is in front; otherwise when it opens.
            handler.post { onNews?.invoke() }
            return
        }
        handler.removeCallbacks(installTimeout)
        if (status == PackageInstaller.STATUS_SUCCESS) {
            // Android restarts Glass Home with the new version right after this.
            state = STATE_IDLE
            lastError = ""
            return
        }
        state = STATE_READY
        if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
            // "Cancel" on Android's question: the same as "Later".
            later(System.currentTimeMillis())
            return
        }
        lastError = message ?: "Install failed ($status)"
        Log.w(TAG, "Update not installed: $lastError")
        state = STATE_FAILED
    }

    /**
     * Set by the home screen while it is in front: an update is ready to ask
     * about, or Android's question is waiting.
     */
    @Volatile
    var onNews: (() -> Unit)? = null

    /** The check itself, on the caller's thread; [check] runs it in the background. Internal for tests. */
    internal fun checkNow() {
        if (state == STATE_DOWNLOADING || state == STATE_INSTALLING) {
            return
        }
        removeOldDownloads()
        state = STATE_CHECKING
        val fetch = fetchForTests
        val body = if (fetch != null) fetch(UpdateCheck.LATEST_URL) else get(UpdateCheck.LATEST_URL)
        if (body == null) {
            // Not counted as a check: the next look, a few hours later, asks again.
            // A TV asleep has no network, and waiting a whole day for that is too long.
            lastError = "GitHub did not answer"
            state = UpdateCheck.stateAfterUnansweredCheck(state)
            return
        }
        prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
        val release = try {
            UpdateCheck.parseLatest(body)
        } catch (error: Exception) {
            Log.w(TAG, "Unexpected reply from GitHub", error)
            null
        }
        Log.i(TAG, "Latest release: ${release?.version ?: "none usable"}, installed: $installedVersion")
        if (release == null || !UpdateCheck.isNewer(release.version, installedVersion)) {
            latest = release
            state = STATE_IDLE
            lastError = ""
            return
        }
        latest = release
        saveRelease(release)
        if (isDevelopmentBuild) {
            // Shown on the website, never installed: see isDevelopmentBuild.
            state = STATE_IDLE
            return
        }
        val apk = apkFile(release)
        if (!apk.exists() || sha256(apk) != release.sha256) {
            state = STATE_DOWNLOADING
            if (!download(release, apk)) {
                return
            }
        }
        if (!isSignedLikeThisApp(apk)) {
            // Kept, so it is not downloaded again every day; never offered.
            state = STATE_OTHER_KEY
            Log.w(TAG, "Glass Home ${release.version} is signed with another key than this build; not offered")
            return
        }
        state = STATE_READY
        lastError = ""
        Log.i(TAG, "Glass Home ${release.version} is downloaded; asking before installing")
        handler.post { onNews?.invoke() }
    }

    private fun installNow() {
        val release = latest ?: return
        val apk = apkFile(release)
        if (!apk.exists() || sha256(apk) != release.sha256) {
            fail("The downloaded update is missing or damaged")
            return
        }
        if (!isSignedLikeThisApp(apk)) {
            state = STATE_OTHER_KEY
            return
        }
        var sessionId = -1
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite(UpdateCheck.APK_NAME, 0, apk.length()).use { output ->
                    apk.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                }
                val intent = Intent(context, UpdateReceiver::class.java)
                var flags = PendingIntent.FLAG_UPDATE_CURRENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android fills in the result, so the intent must be mutable.
                    flags = flags or PendingIntent.FLAG_MUTABLE
                }
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
            Log.i(TAG, "Installing Glass Home ${release.version}")
        } catch (error: Exception) {
            Log.w(TAG, "Could not start the install", error)
            if (sessionId >= 0) {
                // Not left behind in PackageInstaller's list of open sessions.
                try {
                    context.packageManager.packageInstaller.abandonSession(sessionId)
                } catch (gone: Exception) {
                    // Already closed.
                }
            }
            fail(error.message ?: "Could not start the install")
        }
    }

    private fun startConfirmation(context: Context, confirm: Intent) {
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(confirm)
        } catch (error: Exception) {
            Log.w(TAG, "Could not show the install question", error)
            state = STATE_READY
        }
    }

    private fun download(release: Release, target: File): Boolean {
        folder.mkdirs()
        val partial = File(folder, target.name + ".part")
        var connection: HttpURLConnection? = null
        try {
            connection = URL(release.apkUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "GlassHomeTV")
            if (connection.responseCode != 200) {
                fail("Download failed: HTTP ${connection.responseCode}")
                return false
            }
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) {
                            break
                        }
                        total += count
                        if (total > MAX_APK_BYTES) {
                            output.close()
                            partial.delete()
                            fail("The update is larger than expected")
                            return false
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (hex(digest.digest()) != release.sha256) {
                partial.delete()
                fail("The download does not match its SHA-256")
                return false
            }
            if (!partial.renameTo(target)) {
                fail("Could not save the update")
                return false
            }
            return true
        } catch (error: Exception) {
            Log.w(TAG, "Download failed", error)
            partial.delete()
            fail("Download failed")
            return false
        } finally {
            connection?.disconnect()
        }
    }

    /** Leaves only the newest download that is still ahead of the installed version. */
    private fun removeOldDownloads() {
        val files = folder.listFiles() ?: return
        val installed = installedVersion
        for (file in files) {
            val version = file.name.removePrefix("glass-home-").removeSuffix(".apk")
            if (file.name.endsWith(".part") || !UpdateCheck.isNewer(version, installed)) {
                file.delete()
            }
        }
    }

    /**
     * Android installs an update only over an app signed with the same key. A
     * build from source without the release key (scripts/setup.sh without
     * keystore.properties) would be offered every release and refused every time.
     */
    private fun isSignedLikeThisApp(apk: File): Boolean {
        val packageManager = context.packageManager
        val installed = signerDigests(packageManager.getPackageInfo(context.packageName, signingFlags()))
        val downloaded = signerDigests(packageManager.getPackageArchiveInfo(apk.path, signingFlags()))
        return UpdateCheck.sameSigners(installed, downloaded)
    }

    private fun signingFlags(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return PackageManager.GET_SIGNING_CERTIFICATES
        }
        @Suppress("DEPRECATION")
        return PackageManager.GET_SIGNATURES
    }

    private fun signerDigests(info: PackageInfo?): List<String> {
        val result = ArrayList<String>()
        if (info == null) {
            return result
        }
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        if (signatures == null) {
            return result
        }
        for (signature in signatures) {
            val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            result.add(hex(digest))
        }
        return result
    }

    private fun saveRelease(release: Release) {
        prefs.edit()
            .putString("release_version", release.version)
            .putString("release_url", release.apkUrl)
            .putString("release_sha256", release.sha256)
            .apply()
    }

    private fun savedRelease(): Release? {
        val version = prefs.getString("release_version", null) ?: return null
        val url = prefs.getString("release_url", null) ?: return null
        val sha256 = prefs.getString("release_sha256", null) ?: return null
        return Release(version, url, sha256)
    }

    private fun apkFile(release: Release): File {
        return File(folder, "glass-home-${release.version}.apk")
    }

    private fun fail(message: String) {
        lastError = message
        state = STATE_FAILED
    }

    private fun get(address: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(address).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "GlassHomeTV")
            if (connection.responseCode != 200) {
                Log.w(TAG, "HTTP ${connection.responseCode} from GitHub")
                return null
            }
            connection.inputStream.bufferedReader().use { reader -> reader.readText() }
        } catch (error: Exception) {
            Log.w(TAG, "Update check failed", error)
            null
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val REOPEN_KEY = "reopen_home_at"

        /**
         * After an update: true once if Glass Home asked to be put back on screen
         * a moment ago (see [install]). Read by HomeOverrideService when Android
         * connects it again, which is after the new version is in.
         */
        fun takeReopenRequest(context: Context, nowMillis: Long): Boolean {
            val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
            val requestedAt = prefs.getLong(REOPEN_KEY, 0L)
            if (requestedAt == 0L) {
                return false
            }
            prefs.edit().remove(REOPEN_KEY).apply()
            return UpdateCheck.isRecentReopen(requestedAt, nowMillis)
        }

        const val STATE_IDLE = "idle"
        const val STATE_CHECKING = "checking"
        const val STATE_DOWNLOADING = "downloading"
        const val STATE_READY = "ready"
        const val STATE_INSTALLING = "installing"
        const val STATE_CONFIRM = "confirm"
        const val STATE_FAILED = "failed"

        /** The release is signed with another key than this build: Android would refuse it. */
        const val STATE_OTHER_KEY = "other-key"

        private const val TAG = "GlassHomeUpdate"
        private const val TIMEOUT_MS = 20_000

        /** The 0.3.0 APK is 7 MB; anything far larger is not ours. */
        private const val INSTALL_TIMEOUT_MS = 2 * 60_000L

        private const val MAX_APK_BYTES = 64L * 1024 * 1024

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) {
                        break
                    }
                    digest.update(buffer, 0, count)
                }
            }
            return hex(digest.digest())
        }

        private fun hex(bytes: ByteArray): String {
            val builder = StringBuilder()
            for (byte in bytes) {
                builder.append(String.format("%02x", byte))
            }
            return builder.toString()
        }
    }
}

/** Android's answer about an update install: done, failed, or "ask the viewer first". */
class UpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? GlassHomeApp ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
        app.updater.onInstallResult(status, message, confirm)
    }
}
