package com.glasshome.tv

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The home screen's update question: nothing installs without "Install", Back is "Later". */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class UpdateOfferTest {

    private lateinit var activity: Activity
    private lateinit var container: FrameLayout
    private lateinit var overlays: Overlays
    private val picked = ArrayList<String>()

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        container = FrameLayout(activity)
        container.visibility = View.GONE
        activity.setContentView(container)
        overlays = Overlays(activity, container, GlassHomeApp())
        I18n.load(activity, "en")
        picked.clear()
    }

    private fun open() {
        overlays.openUpdateOffer(
            "0.4.0",
            onInstall = { picked.add("install") },
            onLater = { picked.add("later") },
            onSkip = { picked.add("skip") },
        )
    }

    private fun button(label: String): TextView {
        val found = findText(container, label)
        if (found == null) {
            throw AssertionError("No \"$label\" in the panel")
        }
        return found
    }

    private fun findText(view: View, text: String): TextView? {
        if (view is TextView && view.text.toString() == text) {
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
    fun opensWithTheVersionAndFocusOnInstall() {
        open()
        assertTrue(overlays.isOpen)
        button("Glass Home 0.4.0 is ready")
        assertTrue(button("Install").isFocused)
        assertTrue(picked.isEmpty())
    }

    @Test
    fun installOnlyWhenPicked() {
        open()
        button("Install").performClick()
        assertEquals(listOf("install"), picked)
        assertFalse(overlays.isOpen)
    }

    @Test
    fun skipIsRememberedAndIsNotAlsoLater() {
        open()
        button("Skip this version").performClick()
        assertEquals(listOf("skip"), picked)
    }

    @Test
    fun closingWithoutAnAnswerIsLater() {
        open()
        overlays.close()
        assertEquals(listOf("later"), picked)
        // Closing something else afterwards does not count again.
        overlays.close()
        assertEquals(listOf("later"), picked)
    }

    @Test
    fun laterButtonIsLaterOnce() {
        open()
        button("Later").performClick()
        assertEquals(listOf("later"), picked)
    }
}
