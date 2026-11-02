package com.glasshome.tv

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reading GitHub's latest release, comparing versions, and when to ask about an update. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class UpdateCheckTest {

    private val sha = "6091faa8d2de8988ac489d024fe22925b41505e066198c659338f3d81243266d"
    private val apkUrl = "https://github.com/MikeKiselyov/glass-home-android/releases/download/v0.4.0/glass-home.apk"

    /** The shape of api.github.com/repos/.../releases/latest, trimmed to what is read. */
    private fun reply(
        tag: String = "v0.4.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        name: String = "glass-home.apk",
        url: String = apkUrl,
        digest: String? = "sha256:$sha",
    ): String {
        var digestField = ""
        if (digest != null) {
            digestField = """, "digest": "$digest""""
        }
        return """
            {"tag_name": "$tag", "draft": $draft, "prerelease": $prerelease,
             "assets": [
               {"name": "notes.txt", "browser_download_url": "https://github.com/x/notes.txt"},
               {"name": "$name", "size": 7340429, "browser_download_url": "$url"$digestField}
             ]}
        """.trimIndent()
    }

    @Test
    fun readsTheReleaseApkAndItsSha256() {
        assertEquals(Release("0.4.0", apkUrl, sha), UpdateCheck.parseLatest(reply()))
    }

    @Test
    fun ignoresDraftsAndPreReleases() {
        assertNull(UpdateCheck.parseLatest(reply(draft = true)))
        assertNull(UpdateCheck.parseLatest(reply(prerelease = true)))
    }

    @Test
    fun needsTheApkFromThisRepositoryWithASha256() {
        assertNull(UpdateCheck.parseLatest(reply(name = "other.apk")))
        assertNull(UpdateCheck.parseLatest(reply(url = "https://example.com/glass-home.apk")))
        assertNull(UpdateCheck.parseLatest(reply(url = "https://github.com/someone/fork/releases/download/v0.4.0/glass-home.apk")))
        assertNull(UpdateCheck.parseLatest(reply(digest = null)))
        assertNull(UpdateCheck.parseLatest(reply(digest = "md5:abc")))
        assertNull(UpdateCheck.parseLatest(reply(digest = "sha256:not-hex")))
    }

    @Test
    fun ignoresTagsThatAreNotVersions() {
        assertNull(UpdateCheck.parseLatest(reply(tag = "nightly")))
        assertEquals("0.4.0", UpdateCheck.versionFromTag("v0.4.0"))
        assertEquals("1.2", UpdateCheck.versionFromTag("1.2"))
    }

    @Test
    fun comparesVersionsByNumberNotByText() {
        assertTrue(UpdateCheck.isNewer("0.4.0", "0.3.0"))
        assertTrue(UpdateCheck.isNewer("0.10.0", "0.9.0"))
        assertTrue(UpdateCheck.isNewer("1.0", "0.9.9"))
        assertTrue(UpdateCheck.isNewer("0.3.1", "0.3"))
        assertFalse(UpdateCheck.isNewer("0.3.0", "0.3.0"))
        assertFalse(UpdateCheck.isNewer("0.3", "0.3.0"))
        assertFalse(UpdateCheck.isNewer("0.2.9", "0.3.0"))
        assertFalse(UpdateCheck.isNewer("nonsense", "0.3.0"))
    }

    @Test
    fun checksGitHubOnceADay() {
        val hour = 3600_000L
        val now = 1_000_000_000_000L
        assertFalse(UpdateCheck.isDue(now - 23 * hour, now))
        assertTrue(UpdateCheck.isDue(now - 24 * hour, now))
        assertTrue(UpdateCheck.isDue(0L, now))
        // The clock went back (a TV without its time yet): check rather than wait for days.
        assertTrue(UpdateCheck.isDue(now + hour, now))
    }

    @Test
    fun skipThisVersionIsNotAskedAgainButTheNextOneIs() {
        val now = 1_000_000_000_000L
        assertFalse(UpdateCheck.shouldOffer("0.4.0", "0.4.0", 0L, now))
        assertTrue(UpdateCheck.shouldOffer("0.4.1", "0.4.0", 0L, now))
    }

    @Test
    fun laterAsksAgainTheNextDay() {
        val hour = 3600_000L
        val now = 1_000_000_000_000L
        val askAgainAt = now + 24 * hour
        assertFalse(UpdateCheck.shouldOffer("0.4.0", "", askAgainAt, now))
        assertFalse(UpdateCheck.shouldOffer("0.4.0", "", askAgainAt, now + 23 * hour))
        assertTrue(UpdateCheck.shouldOffer("0.4.0", "", askAgainAt, now + 24 * hour))
        // "Later" pressed under a clock far in the future must not silence the question for good.
        assertTrue(UpdateCheck.shouldOffer("0.4.0", "", now + 30 * 24 * hour, now))
    }
    /** A build from source signed with its own key must not be offered releases Android would refuse. */
    @Test
    fun onlyTheSameSigningKeyCanUpdate() {
        val release = listOf("952f6d18b0ce7c4b6c77ae4e00a9b5aac9576c3666ec604a4f9e060440e7afc6")
        val debug = listOf("1111111111111111111111111111111111111111111111111111111111111111")
        assertTrue(UpdateCheck.sameSigners(release, release))
        assertFalse(UpdateCheck.sameSigners(debug, release))
        assertFalse(UpdateCheck.sameSigners(emptyList(), release))
        assertFalse(UpdateCheck.sameSigners(release, emptyList()))
    }
}
