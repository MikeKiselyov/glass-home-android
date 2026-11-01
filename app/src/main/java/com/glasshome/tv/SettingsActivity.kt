package com.glasshome.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * The phone website, opened on the TV itself: the same pages, worked with the
 * remote. The TV signs itself in (no code to type) and the page switches to TV
 * mode (bigger focus ring, arrow keys move between controls, no file uploads).
 * Only the TV's own website is ever loaded here.
 */
class SettingsActivity : Activity() {

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.setBackgroundColor(Color.parseColor("#14181B"))
        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = webView.settings.userAgentString + " " + TV_MARKER
        webView.webViewClient = OnlyTheTvWebsite()
        setContentView(webView)

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        // Made by the home screen's process, where the website and its sign-in live.
        val token = intent.getStringExtra(EXTRA_SESSION)
        if (token != null) {
            cookies.setCookie(ADDRESS, "gh=$token; Path=/; HttpOnly; SameSite=Strict")
            cookies.flush()
        }

        webView.requestFocus()
        loadWhenServerIsUp(0)
    }

    /**
     * The website starts in the background when the app starts. Opened right
     * after (the app was restarted with settings on screen), wait for it a
     * little rather than show a "connection refused" page.
     */
    private fun loadWhenServerIsUp(attempt: Int) {
        if (isDestroyed) {
            return
        }
        // The website runs in the home screen's process: try its port, off the UI thread.
        Thread {
            val up = serverAnswers()
            runOnUiThread {
                if (isDestroyed) {
                    return@runOnUiThread
                }
                if (!loadNow(up, attempt)) {
                    handler.postDelayed({ loadWhenServerIsUp(attempt + 1) }, WAIT_STEP_MS)
                    return@runOnUiThread
                }
                load()
            }
        }.start()
    }

    private fun serverAnswers(): Boolean {
        return try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", PhotoServer.PORT), 200)
            }
            true
        } catch (closed: java.io.IOException) {
            false
        }
    }

    private fun load() {
        // Look first: themes and fonts are what people change on the TV; photos come from a phone.
        webView.loadUrl("$ADDRESS/#look")
    }

    // Back closes settings: the website is one page, and its tabs keep no
    // history worth going back through.

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
        if (isFinishing) {
            // This process only ever holds settings: end it, and the WebView's
            // memory goes with it. The home screen's process is not touched.
            handler.post { Process.killProcess(Process.myPid()) }
        }
        super.onDestroy()
    }

    private inner class OnlyTheTvWebsite : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            return !isTvWebsite(request.url.toString())
        }

        /** The page's renderer died: close settings instead of taking the launcher down with it. */
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            finish()
            return true
        }
    }

    companion object {
        const val ADDRESS = "http://127.0.0.1:" + PhotoServer.PORT

        private const val EXTRA_SESSION = "com.glasshome.tv.SESSION"

        /** Settings on the TV, signed in with [session] (a local session from the website's process). */
        fun intent(context: Context, session: String?): Intent {
            val intent = Intent(context, SettingsActivity::class.java)
            if (session != null) {
                intent.putExtra(EXTRA_SESSION, session)
            }
            return intent
        }

        fun sessionOf(intent: Intent): String? {
            return intent.getStringExtra(EXTRA_SESSION)
        }

        private const val WAIT_STEP_MS = 100L

        /** Up to 5 seconds; after that the page loads anyway and shows why it can't. */
        private const val WAIT_ATTEMPTS = 50

        fun loadNow(serverIsUp: Boolean, attempt: Int): Boolean {
            return serverIsUp || attempt >= WAIT_ATTEMPTS
        }

        /** Added to the user agent; the page turns on TV mode when it sees it. */
        const val TV_MARKER = "GlassHomeTV"

        /** Pages of the TV's own website; anything else (links out, redirects) stays closed. */
        fun isTvWebsite(address: String): Boolean {
            if (address == ADDRESS) {
                return true
            }
            if (!address.startsWith(ADDRESS)) {
                return false
            }
            // Right after the port: a path, a query or a fragment, not more digits or a host.
            val next = address[ADDRESS.length]
            return next == '/' || next == '?' || next == '#'
        }
    }
}
