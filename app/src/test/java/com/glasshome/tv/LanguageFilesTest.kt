package com.glasshome.tv

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Every language file next to en.json (ua.json, by.json, ...) fits it: only
 * keys English has, the same {placeholders}, lists of the same length, and a
 * name and tag for the language list on the website.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LanguageFilesTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun read(code: String): JSONObject {
        val text = context.assets.open("i18n/$code.json").use { input -> input.bufferedReader().readText() }
        return JSONObject(text)
    }

    private fun placeholders(text: String): Set<String> {
        val result = HashSet<String>()
        for (match in Regex("\\{([a-z_]+)\\}").findAll(text)) {
            result.add(match.groupValues[1])
        }
        return result
    }

    private fun otherLanguages(): List<String> {
        val codes = ArrayList<String>()
        for (file in context.assets.list("i18n") ?: emptyArray()) {
            if (file.endsWith(".json") && file != "en.json") {
                codes.add(file.removeSuffix(".json"))
            }
        }
        return codes
    }

    @Test
    fun ukrainianAndBelarusianAreThere() {
        assertTrue(otherLanguages().contains("ua"))
        assertTrue(otherLanguages().contains("by"))
        // The list on the website shows each language in its own name, not the file name.
        assertEquals(read("ua").getString("language.name"), I18n.nameOf(context, "ua"))
        assertEquals(read("by").getString("language.name"), I18n.nameOf(context, "by"))
        assertTrue(I18n.nameOf(context, "ua") != "ua")
        assertEquals("English", I18n.nameOf(context, "en"))
        assertEquals("uk", read("ua").getString("language.tag"))
        assertEquals("be", read("by").getString("language.tag"))
    }

    @Test
    fun everyLanguageFitsEnglish() {
        val english = read("en")
        for (code in otherLanguages()) {
            val other = read(code)
            assertTrue("$code.json: language.name", other.optString("language.name").isNotEmpty())
            assertTrue("$code.json: language.tag", Regex("[a-z]{2,3}(-[A-Za-z0-9]+)*").matches(other.optString("language.tag")))
            for (key in other.keys()) {
                assertTrue("$code.json has $key, which en.json doesn't", english.has(key))
                val value = other.get(key)
                val englishValue = english.get(key)
                if (englishValue is JSONArray) {
                    assertTrue("$code.json: $key should be a list", value is JSONArray)
                    assertEquals("$code.json: $key length", englishValue.length(), (value as JSONArray).length())
                } else {
                    assertEquals("$code.json: placeholders in $key", placeholders(englishValue.toString()), placeholders(value.toString()))
                }
            }
        }
    }

    /** A language picked on the website changes the TV's text, and unknown names are ignored. */
    @Test
    fun loadingALanguageUsesItAndFallsBackToEnglish() {
        I18n.load(context, "ua")
        assertEquals("ua", I18n.language)
        assertTrue(I18n.t("update.install") != "Install")
        I18n.load(context, "xx")
        assertEquals("en", I18n.language)
        assertEquals("Install", I18n.t("update.install"))
    }
}
