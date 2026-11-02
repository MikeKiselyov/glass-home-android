package com.glasshome.tv

import org.json.JSONObject

/** A release on GitHub that the TV can install: its version and the APK with its SHA-256. */
data class Release(val version: String, val apkUrl: String, val sha256: String)

/**
 * Reading GitHub's "latest release" reply and deciding whether it is newer than
 * what is installed. No Android here, so it is tested as plain logic.
 */
object UpdateCheck {

    const val LATEST_URL = "https://api.github.com/repos/MikeKiselyov/glass-home-android/releases/latest"

    /** The asset scripts/dev/release.sh attaches to every release. */
    const val APK_NAME = "glass-home.apk"

    private const val APK_URL_START = "https://github.com/MikeKiselyov/glass-home-android/releases/download/"

    /**
     * The release in GitHub's reply, or null when it has no usable APK: a draft or
     * a pre-release, no glass-home.apk, a download outside this repository's
     * releases, or no SHA-256 to check the download against.
     */
    fun parseLatest(body: String): Release? {
        val reply = JSONObject(body)
        if (reply.optBoolean("draft") || reply.optBoolean("prerelease")) {
            return null
        }
        val version = versionFromTag(reply.optString("tag_name")) ?: return null
        val assets = reply.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            if (asset.optString("name") != APK_NAME) {
                continue
            }
            val url = asset.optString("browser_download_url")
            if (!url.startsWith(APK_URL_START)) {
                return null
            }
            val sha256 = sha256FromDigest(asset.optString("digest")) ?: return null
            return Release(version, url, sha256)
        }
        return null
    }

    /** "v0.4.0" -> "0.4.0"; null for a tag that is not a version. */
    fun versionFromTag(tag: String): String? {
        val version = tag.removePrefix("v")
        if (parts(version) == null) {
            return null
        }
        return version
    }

    /** True when [candidate] is a later version than [installed]: 0.10.0 is later than 0.9.0. */
    fun isNewer(candidate: String, installed: String): Boolean {
        val candidateParts = parts(candidate) ?: return false
        val installedParts = parts(installed) ?: return true
        val length = maxOf(candidateParts.size, installedParts.size)
        for (index in 0 until length) {
            val candidatePart = candidateParts.getOrElse(index) { 0 }
            val installedPart = installedParts.getOrElse(index) { 0 }
            if (candidatePart != installedPart) {
                return candidatePart > installedPart
            }
        }
        return false
    }

    /** True when the last check is a day old, or the clock went back past it. */
    fun isDue(lastCheckMillis: Long, nowMillis: Long): Boolean {
        if (nowMillis < lastCheckMillis) {
            return true
        }
        return nowMillis - lastCheckMillis >= CHECK_EVERY_MS
    }

    const val CHECK_EVERY_MS = 24 * 3600_000L

    /**
     * Whether to ask about [version] now: not after "Skip this version" for it,
     * and not before [askAgainAtMillis] after "Later". A clock that went back
     * more than a day past "Later" asks again rather than never.
     */
    fun shouldOffer(version: String, skippedVersion: String, askAgainAtMillis: Long, nowMillis: Long): Boolean {
        if (version == skippedVersion) {
            return false
        }
        if (askAgainAtMillis - nowMillis > CHECK_EVERY_MS) {
            return true
        }
        return nowMillis >= askAgainAtMillis
    }

    /**
     * True when the downloaded APK is signed by the same certificates as the
     * installed app (SHA-256 of each). Nothing known on either side is never a match.
     */
    fun sameSigners(installed: List<String>, downloaded: List<String>): Boolean {
        if (installed.isEmpty() || downloaded.isEmpty()) {
            return false
        }
        return installed.toSet() == downloaded.toSet()
    }

    /**
     * A "put Glass Home back on screen" request from just before an update
     * counts for a few minutes. An older one (an install that failed, a
     * service connecting after a reboot days later) must not pull Glass Home
     * over whatever is on screen.
     */
    fun isRecentReopen(requestedAtMillis: Long, nowMillis: Long): Boolean {
        val age = nowMillis - requestedAtMillis
        return age >= 0 && age <= REOPEN_WITHIN_MS
    }

    const val REOPEN_WITHIN_MS = 5 * 60_000L

    private fun sha256FromDigest(digest: String): String? {
        if (!digest.startsWith("sha256:")) {
            return null
        }
        val hex = digest.removePrefix("sha256:").lowercase()
        if (!Regex("[0-9a-f]{64}").matches(hex)) {
            return null
        }
        return hex
    }

    private fun parts(version: String): List<Int>? {
        if (!Regex("[0-9]+(\\.[0-9]+){0,3}").matches(version)) {
            return null
        }
        val result = ArrayList<Int>()
        for (part in version.split(".")) {
            result.add(part.toInt())
        }
        return result
    }
}
