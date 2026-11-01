package com.glasshome.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockItemsTest {

    private fun item(key: String, label: String = key, isOn: Boolean = false, iconVersion: String = ""): DockItem {
        return DockItem(key, label, null, null, null, null, isOn, iconVersion)
    }

    @Test
    fun sameAppsInTheSameOrderAreTheSameDock() {
        val before = listOf(item("youtube"), item("netflix"))
        val after = listOf(item("youtube"), item("netflix"))
        assertTrue(DockItems.sameTiles(before, after))
    }

    @Test
    fun anAppThatCameOrWentChangesTheDock() {
        assertFalse(DockItems.sameTiles(listOf(item("youtube")), listOf(item("youtube"), item("netflix"))))
        assertFalse(DockItems.sameTiles(listOf(item("youtube"), item("netflix")), listOf(item("youtube"))))
    }

    @Test
    fun anotherOrderChangesTheDock() {
        assertFalse(DockItems.sameTiles(listOf(item("a"), item("b")), listOf(item("b"), item("a"))))
    }

    /** HDMI devices: CEC renames them ("HDMI 2" -> "PlayStation 5") and the dot shows when they are on. */
    @Test
    fun aRenamedOrSwitchedOnDeviceChangesTheDock() {
        assertFalse(DockItems.sameTiles(listOf(item("hdmi2", "HDMI 2")), listOf(item("hdmi2", "PlayStation 5"))))
        assertFalse(DockItems.sameTiles(listOf(item("hdmi2", isOn = false)), listOf(item("hdmi2", isOn = true))))
    }

    @Test
    fun emptyDocksAreTheSame() {
        assertTrue(DockItems.sameTiles(emptyList(), emptyList()))
    }

    /** An icon picked on the phone, or an app update, while the home screen was closed. */
    @Test
    fun aNewIconChangesTheDock() {
        assertFalse(DockItems.sameTiles(listOf(item("netflix", iconVersion = "|100")), listOf(item("netflix", iconVersion = "file:user-1.png|100"))))
        assertFalse(DockItems.sameTiles(listOf(item("netflix", iconVersion = "|100")), listOf(item("netflix", iconVersion = "|200"))))
    }
}
