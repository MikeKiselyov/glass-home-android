package com.glasshome.tv

import android.app.AppOpsManager
import android.app.Application
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Glass Home's clock setting and the TV's are one: Google's screensaver showed
 * 4:44 while Glass Home showed 16:44.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SystemClockFormatTest {

    private lateinit var context: Application
    private lateinit var settings: HomeSettings

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        settings = HomeSettings(context)
    }

    private fun tvSetting(): String? {
        return Settings.System.getString(context.contentResolver, Settings.System.TIME_12_24)
    }

    /** Someone changes it in the TV's own settings (Date & Time). */
    private fun changeOnTheTv(value: String) {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, value)
    }

    @Test
    fun choiceOnTheWebsiteGoesToTheTv() {
        settings.clockFormat = "12"
        SystemClockFormat.chosen(context, settings)
        assertEquals("12", tvSetting())
    }

    @Test
    fun glassHomeSettingGoesToATvWithoutOne() {
        SystemClockFormat.sync(context, settings)
        assertEquals("24", tvSetting())
    }

    /**
     * Found in review: Glass Home put its own clock back over a change made in
     * the TV's settings on the next Home press. Now Glass Home takes the change.
     */
    @Test
    fun changeInTheTvSettingsComesToGlassHome() {
        SystemClockFormat.sync(context, settings)
        changeOnTheTv("12")
        SystemClockFormat.sync(context, settings)
        assertEquals("12", settings.clockFormat)
        assertEquals("12", tvSetting())
    }

    /** The TV already had Glass Home's clock: a later change there still comes over. */
    @Test
    fun changeOnTheTvAfterTheyMatchedComesToGlassHome() {
        changeOnTheTv("24")
        SystemClockFormat.sync(context, settings)
        changeOnTheTv("12")
        SystemClockFormat.sync(context, settings)
        assertEquals("12", settings.clockFormat)
    }

    /** Before Glass Home ever wrote it, the TV's value is not a change made on purpose. */
    @Test
    fun firstSyncKeepsGlassHomeSetting() {
        changeOnTheTv("12")
        SystemClockFormat.sync(context, settings)
        assertEquals("24", settings.clockFormat)
        assertEquals("24", tvSetting())
    }

    /** A choice on the website wins over an older change on the TV. */
    @Test
    fun choiceOnTheWebsiteWinsOverAnOlderTvChange() {
        SystemClockFormat.sync(context, settings)
        changeOnTheTv("12")
        settings.clockFormat = "24"
        SystemClockFormat.chosen(context, settings)
        SystemClockFormat.sync(context, settings)
        assertEquals("24", settings.clockFormat)
        assertEquals("24", tvSetting())
    }

    /** "Auto" is the TV's own setting: Glass Home follows it and leaves it alone. */
    @Test
    fun autoLeavesTheTvAlone() {
        settings.clockFormat = "auto"
        SystemClockFormat.chosen(context, settings)
        SystemClockFormat.sync(context, settings)
        assertNull(tvSetting())
        assertEquals("auto", settings.clockFormat)
    }

    /** Without "Modify system settings" (scripts/install/allow-clock.sh) only Glass Home changes. */
    @Test
    fun withoutPermissionNothingChanges() {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        shadowOf(appOps).setMode(AppOpsManager.OPSTR_WRITE_SETTINGS, android.os.Process.myUid(), context.packageName, AppOpsManager.MODE_ERRORED)
        settings.clockFormat = "12"
        SystemClockFormat.chosen(context, settings)
        SystemClockFormat.sync(context, settings)
        assertNull(tvSetting())
        assertEquals("12", settings.clockFormat)
    }
}
