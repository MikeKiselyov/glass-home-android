package com.glasshome.tv

import android.app.Activity
import android.app.Application
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class DockTilesTest {

    private lateinit var activity: Activity
    private lateinit var dock: LinearLayout

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        dock = LinearLayout(activity)
        dock.orientation = LinearLayout.VERTICAL
        activity.setContentView(dock)
    }

    private fun item(label: String): DockItem {
        return DockItem(
            key = "app:$label",
            label = label,
            packageName = "com.example.$label",
            inputId = null,
            icon = ColorDrawable(0xFF336699.toInt()),
            originalIcon = null,
        )
    }

    /** A tile as the TV makes it; Robolectric starts in touch mode, a TV never is. */
    private fun tile(tiles: DockTiles, label: String): View {
        val view = tiles.make(item(label), Typeface.DEFAULT)
        view.isFocusableInTouchMode = true
        dock.addView(view)
        return view
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

    /**
     * Regression: the focused Postcard row turns white, but its name stayed
     * white too, so the focused app showed as an empty white bar.
     */
    @Test
    fun focusedPostcardRowShowsItsNameInDarkText() {
        val tiles = DockTiles(activity, "postcard") { }
        val first = tile(tiles, "YouTube")
        val second = tile(tiles, "Netflix")

        second.requestFocus()
        first.requestFocus()

        val focusedName = findText(first, "YouTube")
        val otherName = findText(second, "Netflix")
        assertNotNull(focusedName)
        assertNotNull(otherName)
        assertEquals(0xFF1F2326.toInt(), focusedName!!.currentTextColor)
        assertEquals(0xE6FFFFFF.toInt(), otherName!!.currentTextColor)
    }

    @Test
    fun postcardNameTurnsLightAgainWhenFocusMoves() {
        val tiles = DockTiles(activity, "postcard") { }
        val first = tile(tiles, "YouTube")
        val second = tile(tiles, "Netflix")

        first.requestFocus()
        second.requestFocus()

        assertEquals(0xE6FFFFFF.toInt(), findText(first, "YouTube")!!.currentTextColor)
        assertEquals(0xFF1F2326.toInt(), findText(second, "Netflix")!!.currentTextColor)
    }

    @Test
    fun focusReportsTheItem() {
        var focusedKey = ""
        val tiles = DockTiles(activity, "postcard") { focused -> focusedKey = focused.key }
        val row = tile(tiles, "Spotify")

        row.requestFocus()

        assertEquals("app:Spotify", focusedKey)
    }

    // ---------- every theme ----------

    private val allThemes = listOf("clock", "glass", "postcard", "type")

    private fun settle() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
    }

    @Test
    fun everyThemeMakesTilesTheRemoteCanUse() {
        for (theme in allThemes) {
            dock.removeAllViews()
            val tile = tile(DockTiles(activity, theme) { }, "YouTube")
            assertEquals(theme, "app:YouTube", tile.tag)
            assertEquals(theme, true, tile.isFocusable)
            assertEquals(theme, true, tile.isClickable)
            assertEquals(theme, true, tile.isLongClickable)
        }
    }

    @Test
    fun everyThemeReportsTheFocusedApp() {
        for (theme in allThemes) {
            dock.removeAllViews()
            var focusedKey = ""
            val tiles = DockTiles(activity, theme) { focused -> focusedKey = focused.key }
            val first = tile(tiles, "YouTube")
            val second = tile(tiles, "Netflix")
            first.requestFocus()
            second.requestFocus()
            assertEquals(theme, "app:Netflix", focusedKey)
        }
    }

    /** Clock: only the focused icon shows its name beside it. */
    @Test
    fun clockShowsTheNameOfTheFocusedAppOnly() {
        val tiles = DockTiles(activity, "clock") { }
        val first = tile(tiles, "YouTube")
        val second = tile(tiles, "Netflix")

        first.requestFocus()

        assertEquals(View.VISIBLE, findText(first, "YouTube")!!.visibility)
        assertEquals(View.GONE, findText(second, "Netflix")!!.visibility)
    }

    /** Glass: the focused icon lifts and grows; the others stay flat. */
    @Test
    fun glassLiftsTheFocusedIcon() {
        val tiles = DockTiles(activity, "glass") { }
        val first = tile(tiles, "YouTube")
        val second = tile(tiles, "Netflix")

        first.requestFocus()
        settle()

        assertTrue("scale " + first.scaleX, first.scaleX > 1.1f)
        assertTrue("lift " + first.translationY, first.translationY < 0f)
        assertEquals(1f, second.scaleX, 0.001f)
        assertTrue(first.elevation > second.elevation)
    }

    /** Type: the focused word is bright white, the rest dimmed. */
    @Test
    fun typeBrightensTheFocusedWord() {
        val tiles = DockTiles(activity, "type") { }
        val first = tile(tiles, "YouTube") as TextView
        val second = tile(tiles, "Netflix") as TextView

        first.requestFocus()

        assertEquals(0xFFFFFFFF.toInt(), first.currentTextColor)
        assertEquals(0x8CFFFFFF.toInt(), second.currentTextColor)
    }

    /** HDMI devices that are on get a green dot in every theme. */
    @Test
    fun switchedOnDevicesShowThePowerDotInEveryTheme() {
        for (theme in allThemes) {
            dock.removeAllViews()
            val device = DockItem("input:hdmi1", "PlayStation 5", null, "hdmi1", ColorDrawable(0xFF003791.toInt()), null, isOn = true)
            val tile = DockTiles(activity, theme) { }.make(device, Typeface.DEFAULT)
            dock.addView(tile)
            assertTrue(theme, hasPowerDot(tile))
        }
    }

    @Test
    fun devicesThatAreOffAndAppsHaveNoDot() {
        for (theme in allThemes) {
            dock.removeAllViews()
            val tiles = DockTiles(activity, theme) { }
            val device = DockItem("input:hdmi1", "PlayStation 5", null, "hdmi1", ColorDrawable(0xFF003791.toInt()), null, isOn = false)
            assertFalse(theme, hasPowerDot(tiles.make(device, Typeface.DEFAULT)))
            assertFalse(theme, hasPowerDot(tiles.make(item("YouTube"), Typeface.DEFAULT)))
        }
    }

    /** The dot is a tagged view on icon tiles, and a drawable after the word in Type. */
    private fun hasPowerDot(view: View): Boolean {
        if (view.tag == DockTiles.POWER_DOT) {
            return true
        }
        if (view is TextView) {
            for (drawable in view.compoundDrawablesRelative) {
                if (drawable != null) {
                    return true
                }
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (hasPowerDot(view.getChildAt(index))) {
                    return true
                }
            }
        }
        return false
    }
}
