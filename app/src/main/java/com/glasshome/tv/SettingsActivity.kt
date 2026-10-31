package com.glasshome.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as GlassHomeApp

        webView = WebView(this)
        webView.setBackgroundColor(Color.parseColor("#14181B"))
        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = webView.settings.userAgentString + " " + TV_MARKER
        webView.webViewClient = OnlyTheTvWebsite()
        setContentView(webView)

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        val token = app.server.signInGuard.newLocalSession()
        cookies.setCookie(ADDRESS, "gh=$token; Path=/; HttpOnly; SameSite=Strict")
        cookies.flush()

        webView.requestFocus()
        // Look first: themes and fonts are what people change on the TV; photos come from a phone.
        webView.loadUrl("$ADDRESS/#look")
    }

    // Back closes settings. The website is one page: jumping between its
    // sections (#look, #dock) only adds history, nothing worth going back through.

    override fun onDestroy() {
        webView.destroy()
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
