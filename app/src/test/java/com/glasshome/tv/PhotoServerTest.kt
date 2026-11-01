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
    ) : NanoHTTPD.IHTTPSession {
        private val headers = HashMap<String, String>()

        init {
            if (host != null) {
                headers["host"] = host
            }
            if (cookie != null) {
                headers["cookie"] = cookie
            }
            if (method == NanoHTTPD.Method.POST) {
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
}
