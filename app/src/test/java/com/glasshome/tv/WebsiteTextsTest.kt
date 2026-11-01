package com.glasshome.tv

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every text the website asks for by name is in the language file. */
class WebsiteTextsTest {

    private val root = findRoot()

    private fun findRoot(): File {
        var folder: File? = File("").absoluteFile
        while (folder != null) {
            if (File(folder, "settings.gradle.kts").isFile) {
                return folder
            }
            folder = folder.parentFile
        }
        throw IllegalStateException("Project root not found")
    }

    @Test
    fun everyTextTheWebsiteNamesExists() {
        val page = File(root, "app/src/main/assets/upload.html").readText()
        // Keys of en.json ("web.saved": "..."); org.json is only a stub in JVM tests.
        val english = HashSet<String>()
        val key = Regex("^\\s*\"([^\"]+)\"\\s*:", RegexOption.MULTILINE)
        for (match in key.findAll(File(root, "app/src/main/assets/i18n/en.json").readText())) {
            english.add(match.groupValues[1])
        }

        val names = HashSet<String>()
        val attribute = Regex("data-i18n(?:-placeholder|-aria-label)?=\"([^\"]+)\"")
        for (match in attribute.findAll(page)) {
            names.add(match.groupValues[1])
        }
        // t('web.saved') with a fixed name; t('web.theme.' + id) builds names and is left out.
        val call = Regex("\\bt\\('([a-z0-9_.]+)'\\s*[,)]")
        for (match in call.findAll(page)) {
            names.add(match.groupValues[1])
        }
        // Names kept in lists for later, e.g. CLOCK_FORMATS: { id: '24', key: 'web.look.clock_24_short' }.
        val listed = Regex("\\bkey: '([a-z0-9_.]+)'")
        for (match in listed.findAll(page)) {
            names.add(match.groupValues[1])
        }

        val missing = ArrayList<String>()
        for (name in names) {
            if (!english.contains(name)) {
                missing.add(name)
            }
        }
        assertTrue("Missing in en.json: $missing", missing.isEmpty())
        assertTrue("found only ${names.size} names", names.size > 50)
    }
}
