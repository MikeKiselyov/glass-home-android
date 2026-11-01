package com.glasshome.tv

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Pictures in README.md and the docs folder exist, and docs/images holds nothing unused. */
class DocsImagesTest {

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

    private fun documents(): List<File> {
        val result = ArrayList<File>()
        result.add(File(root, "README.md"))
        val docs = File(root, "docs").listFiles() ?: emptyArray()
        for (file in docs) {
            if (file.name.endsWith(".md")) {
                result.add(file)
            }
        }
        return result
    }

    /** Relative picture paths from <img src="..."> and ![...](...), resolved from the document. */
    private fun picturesIn(document: File): List<File> {
        val text = document.readText()
        val patterns = listOf(
            Regex("<img[^>]*\\ssrc=\"([^\"]+)\""),
            Regex("!\\[[^\\]]*\\]\\(([^)\\s]+)"),
        )
        val pictures = ArrayList<File>()
        for (pattern in patterns) {
            for (match in pattern.findAll(text)) {
                val path = match.groupValues[1]
                if (path.startsWith("http://") || path.startsWith("https://")) {
                    continue
                }
                pictures.add(File(document.parentFile, path).canonicalFile)
            }
        }
        return pictures
    }

    @Test
    fun everyPictureInTheDocsExists() {
        val missing = ArrayList<String>()
        for (document in documents()) {
            for (picture in picturesIn(document)) {
                if (!picture.isFile) {
                    missing.add(document.name + " -> " + picture.relativeTo(root))
                }
            }
        }
        assertTrue("Missing pictures: $missing", missing.isEmpty())
    }

    @Test
    fun everyFileInDocsImagesIsUsed() {
        val used = HashSet<File>()
        for (document in documents()) {
            used.addAll(picturesIn(document))
        }
        val unused = ArrayList<String>()
        val images = File(root, "docs/images").listFiles() ?: emptyArray()
        for (image in images) {
            if (!used.contains(image.canonicalFile)) {
                unused.add(image.name)
            }
        }
        assertTrue("Pictures nothing links to: $unused", unused.isEmpty())
    }

    @Test
    fun picturesHaveAltText() {
        val withoutAlt = ArrayList<String>()
        for (document in documents()) {
            for (tag in Regex("<img[^>]*>").findAll(document.readText())) {
                if (!Regex("\\salt=\"[^\"]+\"").containsMatchIn(tag.value)) {
                    withoutAlt.add(document.name + ": " + tag.value.take(60))
                }
            }
        }
        assertTrue("Pictures without alt text: $withoutAlt", withoutAlt.isEmpty())
    }
}
