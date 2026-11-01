package com.glasshome.tv

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The "set up from your phone" panel on a 1920x1080 TV (density 2).
 * Regression: a 1px spacer view made the whole panel 1px wide inside, so the
 * QR code showed as a thin line.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class PhoneInfoPanelTest {

    private lateinit var activity: Activity
    private lateinit var container: FrameLayout
    private lateinit var overlays: Overlays
    private var newCodeTaps = 0

    @Before
    fun setUp() {
        // A TV is never in touch mode; panels rely on that for focus.
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        container = FrameLayout(activity)
        container.visibility = View.GONE
        activity.setContentView(container)
        overlays = Overlays(activity, container, GlassHomeApp())
        I18n.load(activity, "en")
    }

    private fun info(link: String?): Overlays.PhoneInfo {
        return Overlays.PhoneInfo(
            signInLink = link,
            friendlyAddress = "http://glasshome.local:8090",
            ipAddress = "http://192.168.1.20:8090",
            code = "123456",
            onNewCode = { newCodeTaps += 1 },
        )
    }

    private fun layOut() {
        val width = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        container.measure(width, height)
        container.layout(0, 0, 1920, 1080)
    }

    private fun findQr(view: View): ImageView? {
        if (view is ImageView && view.contentDescription == I18n.t("phone.qr_description")) {
            return view
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = findQr(view.getChildAt(index))
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    private fun findText(view: View, text: String): View? {
        if (view is android.widget.TextView && view.text.toString() == text) {
            return view
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = findText(view.getChildAt(index), text)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    @Test
    fun qrCodeIsShownAtFullSize() {
        overlays.showPhoneInfo(info("http://192.168.1.20:8090/?code=123456"))
        layOut()

        val qr = findQr(container)
        assertNotNull(qr)
        // 150dp at density 2.
        assertEquals(300, qr!!.width)
        assertEquals(300, qr.height)
    }

    @Test
    fun panelIsWiderThanTheQrCode() {
        overlays.showPhoneInfo(info("http://192.168.1.20:8090/?code=123456"))
        layOut()

        val panel = container.getChildAt(0) as LinearLayout
        assertTrue("panel is ${panel.width}px wide", panel.width > 300)
        for (index in 0 until panel.childCount) {
            assertTrue(panel.getChildAt(index).width > 1)
        }
    }

    @Test
    fun panelWithoutWifiStillHasWidth() {
        overlays.showPhoneInfo(info(null))
        layOut()

        val panel = container.getChildAt(0) as LinearLayout
        assertTrue("panel is ${panel.width}px wide", panel.width > 200)
    }

    @Test
    fun showsTheCodeAndANewCodeButton() {
        overlays.showPhoneInfo(info("http://192.168.1.20:8090/?code=123456"))
        layOut()

        assertNotNull(findText(container, "123456"))
        val newCode = findText(container, I18n.t("phone.new_code"))
        assertNotNull(newCode)
        newCode!!.performClick()
        assertEquals(1, newCodeTaps)
    }

    @Test
    fun doneHasTheFocusNotNewCode() {
        overlays.showPhoneInfo(info("http://192.168.1.20:8090/?code=123456"))
        layOut()

        val done = findText(container, I18n.t("menu.done"))
        assertNotNull(done)
        assertTrue(done!!.isFocused)
    }

    @Test
    fun openSettingsHereStartsTheSettingsScreen() {
        overlays.showPhoneInfo(info("http://192.168.1.20:8090/?code=123456"))
        layOut()

        val button = findText(container, I18n.t("phone.open_here"))
        assertNotNull(button)
        button!!.performClick()

        val started = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertEquals(SettingsActivity::class.java.name, started.component!!.className)
        assertFalse("the panel closes", overlays.isOpen)
    }
}
