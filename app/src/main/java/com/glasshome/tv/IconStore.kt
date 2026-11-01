package com.glasshome.tv

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File
import java.io.FileOutputStream

/** One icon the viewer can pick for a tile. */
class IconChoice(val id: String, val group: String)

/**
 * Tile icons and the viewer's choices.
 *
 * Choice ids:
 *   "asset:<file>"  a drawn icon bundled with the app (assets/icons: glow-, toy-, line-)
 *   "file:<file>"   an icon on the TV in files/icons: official-*.png (copied by
 *                   scripts/push-icons.sh, never bundled) or user-*.png (uploaded from a phone)
 * No saved choice means automatic: the app's own icon, or for an HDMI device the
 * official tile when present, else the drawn Glow one.
 */
class IconStore(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("icons", Context.MODE_PRIVATE)
    private val iconsDir = File(context.filesDir, "icons")

    fun resolve(key: String, original: Drawable?, deviceType: String?): Drawable? {
        val chosenId = prefs.getString(key, null)
        if (chosenId != null) {
            val chosen = load(chosenId)
            if (chosen != null) {
                return chosen
            }
        }
        if (original != null) {
            return original
        }
        return deviceDefault(deviceType ?: DEVICE_HDMI)
    }

    fun chosenId(key: String): String? {
        return prefs.getString(key, null)
    }

    fun choose(key: String, id: String) {
        prefs.edit().putString(key, id).apply()
    }

    fun reset(key: String) {
        prefs.edit().remove(key).apply()
    }

    /** Every icon on offer, grouped for the picker: official, mine, then the drawn sets. */
    fun choices(): List<IconChoice> {
        val result = ArrayList<IconChoice>()
        for (name in filesStartingWith("official-")) {
            result.add(IconChoice("file:$name", GROUP_OFFICIAL))
        }
        for (name in filesStartingWith("user-")) {
            result.add(IconChoice("file:$name", GROUP_MINE))
        }
        val assetNames = context.assets.list("icons") ?: emptyArray()
        val sortedAssets = assetNames.sorted()
        for (style in DRAWN_STYLES) {
            for (name in sortedAssets) {
                if (name.startsWith("$style-")) {
                    result.add(IconChoice("asset:$name", style))
                }
            }
        }
        return result
    }

    fun load(id: String): Drawable? {
        val bitmap = when {
            id.startsWith("asset:") -> decodeAsset(id.removePrefix("asset:"))
            id.startsWith("file:") -> decodeIconFile(id.removePrefix("file:"))
            else -> null
        }
        if (bitmap == null) {
            return null
        }
        return BitmapDrawable(context.resources, bitmap)
    }

    fun userIcons(): List<String> {
        return filesStartingWith("user-")
    }

    /** Saves an uploaded image as a square PNG icon. False when it isn't a readable image. */
    fun addUser(source: File): Boolean {
        // Sizes first: a small file can claim a huge picture (a decode bomb).
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(source.path, bounds)
        if (!ImageLimits.isAcceptable(bounds.outWidth, bounds.outHeight)) {
            return false
        }
        val options = BitmapFactory.Options()
        options.inSampleSize = ImageLimits.sampleSize(bounds.outWidth, bounds.outHeight, ICON_SIZE)
        val decoded = try {
            BitmapFactory.decodeFile(source.path, options)
        } catch (error: OutOfMemoryError) {
            null
        } ?: return false
        val side = minOf(decoded.width, decoded.height)
        val left = (decoded.width - side) / 2
        val top = (decoded.height - side) / 2
        val square = Bitmap.createBitmap(decoded, left, top, side, side)
        val scaled = Bitmap.createScaledBitmap(square, ICON_SIZE, ICON_SIZE, true)

        iconsDir.mkdirs()
        val target = File(iconsDir, "user-" + System.currentTimeMillis() + ".png")
        FileOutputStream(target).use { output ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        decoded.recycle()
        if (square !== decoded) {
            square.recycle()
        }
        scaled.recycle()
        return true
    }

    fun deleteUser(name: String): Boolean {
        if (!isSafeName(name) || !name.startsWith("user-")) {
            return false
        }
        val deleted = File(iconsDir, name).delete()
        if (deleted) {
            forgetChoicesOf("file:$name")
        }
        return deleted
    }

    fun userIconFile(name: String): File? {
        if (!isSafeName(name) || !name.startsWith("user-")) {
            return null
        }
        val file = File(iconsDir, name)
        return if (file.isFile) file else null
    }

    private fun deviceDefault(deviceType: String): Drawable? {
        val official = File(iconsDir, "official-$deviceType.png")
        if (official.isFile) {
            return load("file:" + official.name)
        }
        return load("asset:glow-$deviceType.png")
    }

    private fun forgetChoicesOf(id: String) {
        val editor = prefs.edit()
        for ((key, value) in prefs.all) {
            if (value == id) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    private fun filesStartingWith(prefix: String): List<String> {
        val files = iconsDir.listFiles() ?: return emptyList()
        val names = ArrayList<String>()
        for (file in files) {
            if (file.name.startsWith(prefix) && file.name.endsWith(".png")) {
                names.add(file.name)
            }
        }
        names.sort()
        return names
    }

    private fun decodeAsset(name: String): Bitmap? {
        if (!isSafeName(name)) {
            return null
        }
        return try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            context.assets.open("icons/$name").use { input -> BitmapFactory.decodeStream(input, null, bounds) }
            val options = BitmapFactory.Options()
            options.inSampleSize = sampleFor(bounds.outWidth, tileIconPixels())
            context.assets.open("icons/$name").use { input -> BitmapFactory.decodeStream(input, null, options) }
        } catch (missing: java.io.IOException) {
            null
        }
    }

    private fun decodeIconFile(name: String): Bitmap? {
        if (!isSafeName(name)) {
            return null
        }
        val file = File(iconsDir, name)
        if (!file.isFile) {
            return null
        }
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.path, bounds)
        val options = BitmapFactory.Options()
        options.inSampleSize = sampleFor(bounds.outWidth, tileIconPixels())
        return BitmapFactory.decodeFile(file.path, options)
    }

    /** The largest icon the dock draws (Glass theme, focused), in pixels on this screen. */
    private fun tileIconPixels(): Int {
        return (LARGEST_TILE_ICON_DP * context.resources.displayMetrics.density).toInt()
    }

    private fun isSafeName(name: String): Boolean {
        return !name.contains("/") && name.endsWith(".png")
    }

    companion object {
        const val GROUP_OFFICIAL = "official"
        const val GROUP_MINE = "mine"
        const val DEVICE_HDMI = "hdmi"
        private const val ICON_SIZE = 312

        /** Glass theme's tile icon; the other themes draw smaller. */
        private const val LARGEST_TILE_ICON_DP = 62

        /**
         * How much to shrink an icon while decoding it: icons are stored at 312 px
         * and drawn at 26-62 dp. Halves while the result stays at least [needed]
         * pixels wide, so the icon is never drawn blurry. Each halving keeps a
         * quarter of the memory.
         */
        fun sampleFor(width: Int, needed: Int): Int {
            var sample = 1
            if (needed <= 0) {
                return sample
            }
            while (width / (sample * 2) >= needed) {
                sample *= 2
            }
            return sample
        }
        private val DRAWN_STYLES = listOf("glow", "toy", "line")

        /** Guesses the device from its HDMI-CEC name: "PlayStation 5" -> "playstation". */
        fun deviceType(label: String): String {
            val name = label.lowercase()
            if (name.contains("playstation") || name.contains("ps5") || name.contains("ps4")) {
                return "playstation"
            }
            if (name.contains("xbox")) {
                return "xbox"
            }
            if (name.contains("nintendo") || name.contains("switch")) {
                return "nintendo-switch"
            }
            if (name.contains("apple")) {
                return "apple-tv"
            }
            if (name.contains("chromecast") || name.contains("fire") || name.contains("roku") || name.contains("stick")) {
                return "stick"
            }
            return DEVICE_HDMI
        }
    }
}
