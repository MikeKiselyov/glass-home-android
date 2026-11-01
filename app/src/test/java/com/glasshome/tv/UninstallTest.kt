package com.glasshome.tv

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.provider.Settings
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

/** Apps can be removed from the home screen: hold OK on a tile > Uninstall, or App info. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class UninstallTest {

    private lateinit var context: Application
    private lateinit var catalog: AppCatalog

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        I18n.load(context, "en")
        catalog = AppCatalog(context, IconStore(context))
        install("com.disney.disneyplus", system = false)
        install("com.google.android.youtube.tv", system = true)
    }

    private fun install(packageName: String, system: Boolean) {
        val app = ApplicationInfo()
        app.packageName = packageName
        if (system) {
            app.flags = ApplicationInfo.FLAG_SYSTEM
        }
        val info = PackageInfo()
        info.packageName = packageName
        info.applicationInfo = app
        shadowOf(context.packageManager).installPackage(info)
    }

    private fun appTile(packageName: String): DockItem {
        return DockItem(packageName, packageName, packageName, null, null, null)
    }

    @Test
    fun anAppTheViewerInstalledCanBeUninstalled() {
        val tile = appTile("com.disney.disneyplus")
        assertTrue(catalog.canUninstall(tile))
        assertTrue(catalog.uninstall(tile))
        val started = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_DELETE, started.action)
        assertEquals("package:com.disney.disneyplus", started.data.toString())
    }

    /** Built into the TV: Android can't remove it, only switch it off in App info. */
    @Test
    fun aSystemAppOffersOnlyAppInfo() {
        val tile = appTile("com.google.android.youtube.tv")
        assertFalse(catalog.canUninstall(tile))
        assertTrue(catalog.hasAppInfo(tile))
        assertTrue(catalog.openAppInfo(tile))
        val started = shadowOf(context).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        assertEquals("package:com.google.android.youtube.tv", started.data.toString())
    }

    @Test
    fun hdmiDevicesAndTheSettingsTileHaveNothingToUninstall() {
        val hdmi = DockItem("input:hdmi1", "PlayStation 5", null, "hdmi1", null, null)
        val settings = catalog.dockItems().last()
        assertEquals(AppCatalog.SETTINGS_KEY, settings.key)
        for (tile in listOf(hdmi, settings)) {
            assertFalse(catalog.canUninstall(tile))
            assertFalse(catalog.hasAppInfo(tile))
            assertFalse(catalog.uninstall(tile))
        }
    }

    @Test
    fun anAppThatIsAlreadyGoneIsNotOffered() {
        assertFalse(catalog.canUninstall(appTile("com.example.gone")))
    }
}
