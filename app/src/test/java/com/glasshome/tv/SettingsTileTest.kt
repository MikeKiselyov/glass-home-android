package com.glasshome.tv

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The remote alone reaches Glass Home's settings: a tile on the dock in every theme. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SettingsTileTest {

    private lateinit var context: Application
    private lateinit var catalog: AppCatalog

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        I18n.load(context, "en")
        catalog = AppCatalog(context, IconStore(context))
    }

    @Test
    fun theDockEndsWithTheSettingsTile() {
        val items = catalog.dockItems()
        val last = items.last()
        assertEquals(AppCatalog.SETTINGS_KEY, last.key)
        assertEquals("Home settings", last.label)
        assertTrue(last.icon != null)
    }

    @Test
    fun theTileOpensTheSettingsOnTheTv() {
        val tile = catalog.dockItems().last()
        assertTrue(catalog.open(tile))
        val started = shadowOf(context).nextStartedActivity
        assertEquals(SettingsActivity::class.java.name, started.component?.className)
    }

    @Test
    fun itCanBeHiddenAndShownAgainFromTheWebsite() {
        catalog.hide(AppCatalog.SETTINGS_KEY)
        var onDock = false
        for (item in catalog.dockItems()) {
            if (item.key == AppCatalog.SETTINGS_KEY) {
                onDock = true
            }
        }
        assertFalse(onDock)

        var onWebsite = false
        for (item in catalog.everyItem()) {
            if (item.key == AppCatalog.SETTINGS_KEY) {
                onWebsite = true
            }
        }
        assertTrue(onWebsite)
    }
}
