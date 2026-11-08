package com.glasshome.tv

import android.app.Application
import fi.iki.elonen.NanoHTTPD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * The phone website's checks as a request meets them in serve(): home network,
 * Host header, sign-in code and session cookie. The rules themselves are tested
 * in RequestChecksTest and SignInGuardTest; this makes sure they are used.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PhotoServerTest {

    private lateinit var server: PhotoServer

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        I18n.load(context, "en")
        val icons = IconStore(context)
        server = PhotoServer(
            context,
            WallpaperStore(context),
            icons,
            Weather(context),
            HomeSettings(context),
            AppCatalog(context, icons),
        )
    }

    private inner class FakeSession(
        private val method: NanoHTTPD.Method,
        private val uri: String,
        private val remote: String = "192.168.1.30",
        host: String? = "192.168.1.20:8090",
        cookie: String? = null,
        private val form: Map<String, String> = emptyMap(),
        websiteHeader: Boolean = true,
    ) : NanoHTTPD.IHTTPSession {
        private val headers = HashMap<String, String>()

        init {
            if (host != null) {
                headers["host"] = host
            }
            if (cookie != null) {
                headers["cookie"] = cookie
            }
            if (method == NanoHTTPD.Method.POST && websiteHeader) {
                headers["x-glass-home"] = "1"
            }
        }

        override fun execute() {}
        override fun getCookies(): NanoHTTPD.CookieHandler = server.CookieHandler(headers)
        override fun getHeaders(): Map<String, String> = headers
        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun getMethod(): NanoHTTPD.Method = method

        @Deprecated("NanoHTTPD's own deprecation")
        override fun getParms(): Map<String, String> = form

        override fun getParameters(): Map<String, List<String>> {
            val parameters = HashMap<String, List<String>>()
            for ((name, value) in form) {
                parameters[name] = listOf(value)
            }
            return parameters
        }

        override fun getQueryParameterString(): String = ""
        override fun getUri(): String = uri
        override fun parseBody(files: MutableMap<String, String>) {}
        override fun getRemoteIpAddress(): String = remote
        override fun getRemoteHostName(): String = remote
    }

    private fun signIn(code: String, remote: String = "192.168.1.30"): NanoHTTPD.Response {
        return server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/login", remote = remote, form = mapOf("code" to code)))
    }

    private fun wrongCode(): String {
        if (server.code == "000000") {
            return "000001"
        }
        return "000000"
    }

    private fun sessionCookie(response: NanoHTTPD.Response): String {
        val header = response.getHeader("Set-Cookie")
        assertNotNull(header)
        return header!!.substringBefore(";")
    }

    @Test
    fun refusesAddressesOutsideTheHomeNetwork() {
        val response = server.serve(FakeSession(NanoHTTPD.Method.GET, "/", remote = "8.8.8.8"))
        assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, response.status)
    }

    @Test
    fun refusesAForeignHostHeader() {
        val response = server.serve(FakeSession(NanoHTTPD.Method.GET, "/", host = "evil.example.com:8090"))
        assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, response.status)
    }

    @Test
    fun refusesARequestWithoutHost() {
        val response = server.serve(FakeSession(NanoHTTPD.Method.GET, "/", host = null))
        assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, response.status)
    }

    @Test
    fun apiNeedsASession() {
        val response = server.serve(FakeSession(NanoHTTPD.Method.GET, "/api/settings"))
        assertEquals(NanoHTTPD.Response.Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun wrongCodeGivesNoSession() {
        val response = signIn(wrongCode())
        assertEquals(NanoHTTPD.Response.Status.UNAUTHORIZED, response.status)
        assertEquals(null, response.getHeader("Set-Cookie"))
    }

    @Test
    fun rightCodeGivesASessionTheApiAccepts() {
        val login = signIn(server.code)
        assertEquals(NanoHTTPD.Response.Status.OK, login.status)
        val cookie = sessionCookie(login)
        assertTrue(login.getHeader("Set-Cookie")!!.contains("HttpOnly"))

        val response = server.serve(FakeSession(NanoHTTPD.Method.GET, "/api/settings", cookie = cookie))
        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
    }

    @Test
    fun fiveWrongCodesLockThePhoneOut() {
        for (attempt in 1..5) {
            signIn(wrongCode())
        }
        val response = signIn(server.code)
        assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, response.status)
    }

    @Test
    fun manyPhonesGuessingLockEveryoneOut() {
        for (index in 0 until 5) {
            for (attempt in 1..4) {
                signIn(wrongCode(), remote = "fe80::$index")
            }
        }
        val response = signIn(server.code, remote = "192.168.1.99")
        assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, response.status)
    }

    @Test
    fun newCodeSignsSessionsOut() {
        val cookie = sessionCookie(signIn(server.code))
        server.signInGuard.resetCode()
        val response = server.serve(FakeSession(NanoHTTPD.Method.GET, "/api/settings", cookie = cookie))
        assertEquals(NanoHTTPD.Response.Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun postWithoutTheWebsiteHeaderIsRefused() {
        val session = object : NanoHTTPD.IHTTPSession by FakeSession(NanoHTTPD.Method.POST, "/api/login", form = mapOf("code" to "1")) {
            override fun getHeaders(): Map<String, String> = mapOf("host" to "192.168.1.20:8090")
        }
        assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, server.serve(session).status)
    }

    /** A place name is shown on other phones and on the TV: markup in it is refused. */
    @Test
    fun weatherPlaceWithMarkupIsRefused() {
        val cookie = sessionCookie(signIn(server.code))
        val form = mapOf("name" to "<img src=x onerror=alert(1)>", "latitude" to "45.1", "longitude" to "5.7")
        val response = server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/weather", cookie = cookie, form = form))
        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, response.status)
    }

    @Test
    fun ordinaryPlaceNamesAreKept() {
        val cookie = sessionCookie(signIn(server.code))
        val form = mapOf("name" to "Saint-Martin-d'Hères, Auvergne-Rhône-Alpes, France", "latitude" to "45.16", "longitude" to "5.77")
        val response = server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/weather", cookie = cookie, form = form))
        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
    }

    /** The Home button choice on the website reaches the setting the accessibility service reads. */
    @Test
    fun homeButtonChoiceIsSavedAndUnknownValuesAreIgnored() {
        val cookie = sessionCookie(signIn(server.code))
        val context = RuntimeEnvironment.getApplication()
        server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/settings", cookie = cookie, form = mapOf("homeButton" to "after")))
        assertEquals("after", HomeSettings(context).homeButton)
        server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/settings", cookie = cookie, form = mapOf("homeButton" to "nonsense")))
        assertEquals("after", HomeSettings(context).homeButton)

        val response = server.serve(FakeSession(NanoHTTPD.Method.GET, "/api/settings", cookie = cookie))
        val body = response.data.bufferedReader().readText()
        assertTrue(body.contains("\"homeButton\":\"after\""))
    }

    /** The clock chosen on the website is the TV's clock too (Google's screensaver). */
    @Test
    fun clockFormatReachesTheTv() {
        val cookie = sessionCookie(signIn(server.code))
        val context = RuntimeEnvironment.getApplication()
        server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/settings", cookie = cookie, form = mapOf("clockFormat" to "12")))
        assertEquals("12", android.provider.Settings.System.getString(context.contentResolver, android.provider.Settings.System.TIME_12_24))
    }

    /** Still photos (no slow zoom) is a switch on the website's Photos tab. */
    @Test
    fun photoMotionCanBeSwitchedOff() {
        val cookie = sessionCookie(signIn(server.code))
        val context = RuntimeEnvironment.getApplication()
        assertTrue(HomeSettings(context).photoMotion)
        server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/settings", cookie = cookie, form = mapOf("photoMotion" to "0")))
        assertEquals(false, HomeSettings(context).photoMotion)
        val body = server.serve(FakeSession(NanoHTTPD.Method.GET, "/api/settings", cookie = cookie)).data.bufferedReader().readText()
        assertTrue(body.contains("\"photoMotion\":false"))
    }
    /** The website can switch update checks off; no request installs anything without action=install. */
    @Test
    fun updatesAreSwitchedAndNeverInstalledUnasked() {
        val context = RuntimeEnvironment.getApplication()
        val updater = Updater(context)
        server.updater = updater
        val cookie = sessionCookie(signIn(server.code))

        val status = server.serve(FakeSession(NanoHTTPD.Method.GET, "/api/update", cookie = cookie))
        assertEquals(NanoHTTPD.Response.Status.OK, status.status)
        assertTrue(status.data.bufferedReader().readText().contains("\"checks\":true"))

        server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/update", cookie = cookie, form = mapOf("checks" to "0")))
        assertEquals(false, updater.checksEnabled)
        assertEquals(Updater.STATE_IDLE, updater.state)

        val unknown = server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/update", cookie = cookie, form = mapOf("action" to "install-silently")))
        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, unknown.status)
        assertEquals(Updater.STATE_IDLE, updater.state)
    }

    @Test
    fun updatesNeedASession() {
        server.updater = Updater(RuntimeEnvironment.getApplication())
        val response = server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/update", form = mapOf("action" to "install")))
        assertEquals(NanoHTTPD.Response.Status.UNAUTHORIZED, response.status)
    }

    /** "This TV" on the website shows the VPN and the address by IP. */
    @Test
    fun settingsSayWhetherAVpnIsOnAndTheAddress() {
        val cookie = sessionCookie(signIn(server.code))
        val settings = server.serve(FakeSession(NanoHTTPD.Method.GET, "/api/settings", cookie = cookie)).data.bufferedReader().readText()
        assertTrue(settings.contains("\"vpn\":false"))
        assertTrue(settings.contains("\"address\":"))
    }

    /** A signed-in phone sends a link; the TV opens it. Only web links get that far. */
    @Test
    fun aLinkFromThePhoneOpensOnTheTv() {
        val opened = ArrayList<String>()
        server.openLink = { link ->
            opened.add(link)
            LinkOpener.Result.OPENED
        }
        val cookie = sessionCookie(signIn(server.code))

        val response = server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/open", cookie = cookie, form = mapOf("link" to "Look https://example.com/film")))
        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertTrue(response.data.bufferedReader().readText().contains("\"result\":\"opened\""))
        assertEquals(listOf("https://example.com/film"), opened)

        val bad = server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/open", cookie = cookie, form = mapOf("link" to "file:///data/data/com.glasshome.tv/")))
        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, bad.status)
        assertTrue(bad.data.bufferedReader().readText().contains("bad-link"))
        assertEquals(1, opened.size)
    }

    @Test
    fun theTvSaysWhenNothingOpensLinks() {
        server.openLink = { LinkOpener.Result.NO_APP }
        val cookie = sessionCookie(signIn(server.code))
        val response = server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/open", cookie = cookie, form = mapOf("link" to "https://example.com/")))
        assertTrue(response.data.bufferedReader().readText().contains("\"result\":\"no-app\""))
    }

    @Test
    fun openingALinkNeedsASession() {
        val opened = ArrayList<String>()
        server.openLink = { link ->
            opened.add(link)
            LinkOpener.Result.OPENED
        }
        val response = server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/open", form = mapOf("link" to "https://example.com/")))
        assertEquals(NanoHTTPD.Response.Status.UNAUTHORIZED, response.status)
        assertTrue(opened.isEmpty())
    }

    /** Found in review: the website sends again after 8 s, and the first one may have arrived. */
    @Test
    fun theSameRequestSentAgainOpensOnce() {
        val opened = ArrayList<String>()
        server.openLink = { link ->
            opened.add(link)
            LinkOpener.Result.OPENED
        }
        val cookie = sessionCookie(signIn(server.code))
        val first = mapOf("link" to "https://example.com/", "request" to "r1")
        server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/open", cookie = cookie, form = first))
        val again = server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/open", cookie = cookie, form = first))
        assertTrue(again.data.bufferedReader().readText().contains("\"result\":\"opened\""))
        assertEquals(1, opened.size)

        val next = mapOf("link" to "https://example.com/", "request" to "r2")
        server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/open", cookie = cookie, form = next))
        assertEquals(2, opened.size)
    }

    @Test
    fun theTvSaysWhenItCannotOpenAppsNow() {
        server.openLink = { LinkOpener.Result.SERVICE_OFF }
        val cookie = sessionCookie(signIn(server.code))
        val response = server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/open", cookie = cookie, form = mapOf("link" to "https://example.com/")))
        assertTrue(response.data.bufferedReader().readText().contains("\"result\":\"service-off\""))
    }

    private fun body(response: NanoHTTPD.Response): String {
        return response.data.bufferedReader().readText()
    }

    private fun openedLinks(): ArrayList<String> {
        val opened = ArrayList<String>()
        server.openLink = { link ->
            opened.add(link)
            LinkOpener.Result.OPENED
        }
        return opened
    }

    /** The iPhone shortcut: the key in the address, no cookie, no website header. */
    @Test
    fun aShortcutWithTheKeyOpensALink() {
        val opened = openedLinks()
        val response = server.serve(FakeSession(NanoHTTPD.Method.POST, "/send/" + server.shareKey.key, form = mapOf("link" to "https://example.com/film"), websiteHeader = false))
        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals(I18n.t("web.open.opened"), body(response))
        assertEquals(listOf("https://example.com/film"), opened)
    }

    @Test
    fun aWrongKeyOpensNothing() {
        val opened = openedLinks()
        val wrong = server.serve(FakeSession(NanoHTTPD.Method.POST, "/send/0123456789abcdef0123456789abcdef", form = mapOf("link" to "https://example.com/"), websiteHeader = false))
        assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, wrong.status)
        val none = server.serve(FakeSession(NanoHTTPD.Method.POST, "/send/", form = mapOf("link" to "https://example.com/"), websiteHeader = false))
        assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, none.status)
        val get = server.serve(FakeSession(NanoHTTPD.Method.GET, "/send/" + server.shareKey.key))
        assertTrue(get.status != NanoHTTPD.Response.Status.OK)
        assertTrue(opened.isEmpty())
    }

    /** The key also only reaches the TV from the home network, under the TV's own name. */
    @Test
    fun theKeyDoesNotLiftTheNetworkChecks() {
        val opened = openedLinks()
        val path = "/send/" + server.shareKey.key
        server.serve(FakeSession(NanoHTTPD.Method.POST, path, remote = "8.8.8.8", form = mapOf("link" to "https://example.com/"), websiteHeader = false))
        server.serve(FakeSession(NanoHTTPD.Method.POST, path, host = "evil.example", form = mapOf("link" to "https://example.com/"), websiteHeader = false))
        assertTrue(opened.isEmpty())
    }

    /** "New code" on the TV: phones are signed out and the old shortcut stops working. */
    @Test
    fun aNewCodeRetiresTheKey() {
        val opened = openedLinks()
        val old = server.shareKey.key
        server.newCode()
        val response = server.serve(FakeSession(NanoHTTPD.Method.POST, "/send/$old", form = mapOf("link" to "https://example.com/"), websiteHeader = false))
        assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, response.status)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun signedInPhonesSeeAndReplaceTheKey() {
        val anonymous = server.serve(FakeSession(NanoHTTPD.Method.GET, "/api/share-key"))
        assertEquals(NanoHTTPD.Response.Status.UNAUTHORIZED, anonymous.status)
        val cookie = sessionCookie(signIn(server.code))
        val shown = body(server.serve(FakeSession(NanoHTTPD.Method.GET, "/api/share-key", cookie = cookie)))
        assertTrue(shown.contains(server.shareKey.key))
        val old = server.shareKey.key
        server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/share-key", cookie = cookie, form = mapOf("action" to "reset")))
        assertTrue(old != server.shareKey.key)
    }

    @Test
    fun sentLinksAreListedAndCleared() {
        openedLinks()
        val cookie = sessionCookie(signIn(server.code))
        server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/open", cookie = cookie, form = mapOf("link" to "https://a.example/")))
        server.serve(FakeSession(NanoHTTPD.Method.POST, "/send/" + server.shareKey.key, form = mapOf("link" to "https://b.example/"), websiteHeader = false))
        val listed = body(server.serve(FakeSession(NanoHTTPD.Method.GET, "/api/recent", cookie = cookie)))
        assertTrue(listed.indexOf("b.example") in 0 until listed.indexOf("a.example"))

        assertEquals(NanoHTTPD.Response.Status.UNAUTHORIZED, server.serve(FakeSession(NanoHTTPD.Method.GET, "/api/recent")).status)

        server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/recent", cookie = cookie, form = mapOf("action" to "clear")))
        val cleared = body(server.serve(FakeSession(NanoHTTPD.Method.GET, "/api/recent", cookie = cookie)))
        assertTrue(!cleared.contains("example"))
    }

    private class MemorySleepStorage : SleepTimerStorage {
        var saved = 0L
        override fun loadSleepAt(): Long = saved
        override fun saveSleepAt(millis: Long) {
            saved = millis
        }
    }

    @Test
    fun aSignedInPhoneSetsTheSleepTimer() {
        val timer = SleepTimer(MemorySleepStorage())
        server.sleepTimer = timer
        var changes = 0
        server.onSleepChanged = { changes += 1 }
        val cookie = sessionCookie(signIn(server.code))

        val set = server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/sleep", cookie = cookie, form = mapOf("minutes" to "60")))
        assertTrue(body(set).contains("\"minutesLeft\":60"))
        assertNotNull(timer.sleepAt())
        assertEquals(1, changes)

        val odd = server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/sleep", cookie = cookie, form = mapOf("minutes" to "1000")))
        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, odd.status)

        server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/sleep", cookie = cookie, form = mapOf("minutes" to "0")))
        assertEquals(null, timer.sleepAt())

        assertEquals(NanoHTTPD.Response.Status.UNAUTHORIZED, server.serve(FakeSession(NanoHTTPD.Method.POST, "/api/sleep", form = mapOf("minutes" to "30"))).status)
    }
}
