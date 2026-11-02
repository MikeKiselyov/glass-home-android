package com.glasshome.tv

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Every piece of interface text, for the TV and the phone website, comes from
 * assets/i18n/<language>.json. English (en.json) is complete; another language
 * file only needs the keys it translates, the rest falls back to English.
 *
 * In the text, {name} is replaced by the value given for "name". When a "count"
 * value is 1 and a "<key>_one" entry exists, that entry is used instead.
 */
object I18n {

    private var english = JSONObject()
    private var chosen = JSONObject()

    var language = "en"
        private set

    fun load(context: Context, wanted: String) {
        english = read(context, "en") ?: JSONObject()
        val other = if (wanted == "en") null else read(context, wanted)
        chosen = other ?: english
        language = if (other == null) "en" else wanted
    }

    /** t("menu.show_hidden", "count" to 3) -> "Show hidden apps (3)". */
    fun t(key: String, vararg values: Pair<String, Any>): String {
        var lookupKey = key
        for (value in values) {
            if (value.first == "count" && value.second == 1 && has("${key}_one")) {
                lookupKey = "${key}_one"
            }
        }
        var text = chosen.optString(lookupKey, "")
        if (text.isEmpty()) {
            text = english.optString(lookupKey, key)
        }
        for (value in values) {
            text = text.replace("{" + value.first + "}", value.second.toString())
        }
        return text
    }

    /** A list entry such as "date.months". */
    fun list(key: String): List<String> {
        val array: JSONArray = chosen.optJSONArray(key) ?: english.optJSONArray(key) ?: return emptyList()
        val result = ArrayList<String>()
        for (index in 0 until array.length()) {
            result.add(array.getString(index))
        }
        return result
    }

    /** English with the chosen language on top, for the phone website. */
    fun merged(): String {
        val result = JSONObject(english.toString())
        for (key in chosen.keys()) {
            result.put(key, chosen.get(key))
        }
        return result.toString()
    }

    /** The name a language file gives itself (language.name, in that language), for the language list; the file name if it has none. */
    fun nameOf(context: Context, code: String): String {
        val file = read(context, code) ?: return code
        val name = file.optString("language.name", "")
        if (name.isEmpty()) {
            return code
        }
        return name
    }

    private fun has(key: String): Boolean {
        return chosen.has(key) || english.has(key)
    }

    private fun read(context: Context, code: String): JSONObject? {
        return try {
            val text = context.assets.open("i18n/$code.json").use { input -> input.bufferedReader().readText() }
            JSONObject(text)
        } catch (missing: java.io.IOException) {
            null
        } catch (broken: org.json.JSONException) {
            Log.w("GlassHome", "i18n/$code.json is not valid JSON", broken)
            null
        }
    }
}
