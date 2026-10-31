package com.glasshome.tv

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/** One photo: bundled with the app (assetPath) or uploaded from a phone (file). */
class Wallpaper(
    val key: String,
    val caption: String,
    val assetPath: String?,
    val file: File?,
) {
    val isOwn: Boolean
        get() = file != null
}

/**
 * The photos and which of them the backdrop rotates through.
 *
 * Mode "all" rotates every photo, "own" only uploaded ones (falls back to all
 * while there are none), "pinned" keeps one photo on screen.
 */
class WallpaperStore(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("wallpapers", Context.MODE_PRIVATE)
    private val ownDir = File(context.filesDir, "photos")
    private val bundled: List<Wallpaper> = readBundled()

    var mode: String
        get() = prefs.getString("mode", MODE_ALL) ?: MODE_ALL
        set(value) = prefs.edit().putString("mode", value).apply()

    var pinnedKey: String?
        get() = prefs.getString("pinned", null)
        set(value) = prefs.edit().putString("pinned", value).apply()

    /** Uploaded and album photos: in the order set on the phone website, new ones last. */
    fun own(): List<Wallpaper> {
        val files = ownDir.listFiles() ?: return emptyList()
        val photos = ArrayList<File>()
        for (file in files) {
            if (file.name.endsWith(".jpg")) {
                photos.add(file)
            }
        }
        val savedOrder = (prefs.getString("order", "") ?: "").split(",")
        photos.sortWith { first, second ->
            val firstIndex = savedOrder.indexOf(first.name)
            val secondIndex = savedOrder.indexOf(second.name)
            when {
                firstIndex >= 0 && secondIndex >= 0 -> firstIndex - secondIndex
                firstIndex >= 0 -> -1
                secondIndex >= 0 -> 1
                else -> first.lastModified().compareTo(second.lastModified())
            }
        }

        val result = ArrayList<Wallpaper>()
        for (photo in photos) {
            result.add(Wallpaper("own:" + photo.name, "", null, photo))
        }
        return result
    }

    /** Saves the order of uploaded photos (file names, first shown first). */
    fun setOrder(names: List<String>) {
        val known = HashSet<String>()
        for (wallpaper in own()) {
            val file = wallpaper.file ?: continue
            known.add(file.name)
        }
        val kept = ArrayList<String>()
        for (name in names) {
            if (known.contains(name)) {
                kept.add(name)
            }
        }
        prefs.edit().putString("order", kept.joinToString(",")).apply()
    }

    fun all(): List<Wallpaper> {
        return own() + bundled
    }

    fun find(key: String): Wallpaper? {
        for (wallpaper in all()) {
            if (wallpaper.key == key) {
                return wallpaper
            }
        }
        return null
    }

    /** The photos the backdrop should rotate through right now. */
    fun playlist(): List<Wallpaper> {
        if (mode == MODE_PINNED) {
            val pinned = pinnedKey?.let { key -> find(key) }
            if (pinned != null) {
                return listOf(pinned)
            }
        }
        if (mode == MODE_OWN) {
            val ownPhotos = own()
            if (ownPhotos.isNotEmpty()) {
                return ownPhotos
            }
        }
        return all()
    }

    /**
     * Stores an uploaded image as a JPEG no larger than the screen. Returns
     * false when the data is not an image Android can decode.
     */
    fun addOwn(source: File): Boolean {
        return saveScaled(source, "photo-" + System.currentTimeMillis() + ".jpg")
    }

    fun hasAlbumPhoto(id: String): Boolean {
        return File(ownDir, "album-$id.jpg").isFile
    }

    fun addAlbumPhoto(source: File, id: String): Boolean {
        return saveScaled(source, "album-$id.jpg")
    }

    /** Deletes album photos whose id is not in [keep] (they left the shared album). */
    fun removeAlbumPhotosExcept(keep: Set<String>) {
        val files = ownDir.listFiles() ?: return
        for (file in files) {
            val name = file.name
            if (!name.startsWith("album-") || !name.endsWith(".jpg")) {
                continue
            }
            val id = name.removePrefix("album-").removeSuffix(".jpg")
            if (!keep.contains(id)) {
                file.delete()
            }
        }
    }

    private fun saveScaled(source: File, targetName: String): Boolean {
        ownDir.mkdirs()
        val target = File(ownDir, targetName)

        // A JPEG that is already about screen size (the phone website resizes
        // before uploading) is kept as it is: compressing it again only loses quality.
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return false
        }
        val coverScale = maxOf(SCREEN_WIDTH.toFloat() / bounds.outWidth, SCREEN_HEIGHT.toFloat() / bounds.outHeight)
        if (bounds.outMimeType == "image/jpeg" && coverScale >= 0.9f) {
            source.copyTo(target, overwrite = true)
            return true
        }

        val decoded = decodeFile(source, SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888) ?: return false
        val bitmap = scaleToCover(decoded, SCREEN_WIDTH, SCREEN_HEIGHT)
        FileOutputStream(target).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        }
        bitmap.recycle()
        return true
    }

    fun deleteOwn(name: String): Boolean {
        if (name.contains("/") || !name.endsWith(".jpg")) {
            return false
        }
        val file = File(ownDir, name)
        val deleted = file.delete()
        File(File(context.cacheDir, "thumbs"), name).delete()
        if (deleted && pinnedKey == "own:$name") {
            mode = MODE_ALL
        }
        return deleted
    }

    /**
     * Full-screen bitmap in full colour (RGB_565 shows bands in skies), sized
     * for how it will be shown: landscape photos cover the screen, portrait ones
     * fill its height. Never much bigger than the screen, so about 8 MB at most.
     */
    fun loadScreen(wallpaper: Wallpaper): Bitmap? {
        val decoded = load(wallpaper, SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888) ?: return null
        val aspect = decoded.width.toFloat() / decoded.height
        val scale = if (aspect < PORTRAIT_BELOW_ASPECT) {
            SCREEN_HEIGHT.toFloat() / decoded.height
        } else {
            maxOf(SCREEN_WIDTH.toFloat() / decoded.width, SCREEN_HEIGHT.toFloat() / decoded.height)
        }
        if (scale >= 1f) {
            return decoded
        }
        val width = maxOf(1, (decoded.width * scale).toInt())
        val height = maxOf(1, (decoded.height * scale).toInt())
        val sized = Bitmap.createScaledBitmap(decoded, width, height, true)
        if (sized !== decoded) {
            decoded.recycle()
        }
        return sized
    }

    fun loadThumb(wallpaper: Wallpaper): Bitmap? {
        return load(wallpaper, THUMB_WIDTH, THUMB_HEIGHT, Bitmap.Config.RGB_565)
    }

    private fun load(wallpaper: Wallpaper, width: Int, height: Int, config: Bitmap.Config): Bitmap? {
        val file = wallpaper.file
        if (file != null) {
            return decodeFile(file, width, height, config)
        }
        val assetPath = wallpaper.assetPath ?: return null
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        context.assets.open(assetPath).use { input -> BitmapFactory.decodeStream(input, null, bounds) }

        val options = BitmapFactory.Options()
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, width, height)
        options.inPreferredConfig = config
        return context.assets.open(assetPath).use { input -> BitmapFactory.decodeStream(input, null, options) }
    }

    private fun decodeFile(file: File, width: Int, height: Int, config: Bitmap.Config): Bitmap? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        val options = BitmapFactory.Options()
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, width, height)
        options.inPreferredConfig = config
        val decoded = BitmapFactory.decodeFile(file.path, options) ?: return null
        return fitInside(decoded, width * 2, height * 2)
    }

    /** Scales down a bitmap that is still much larger than needed after sampling. */
    private fun fitInside(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) {
            return bitmap
        }
        val scale = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
        val width = (bitmap.width * scale).toInt()
        val height = (bitmap.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        bitmap.recycle()
        return scaled
    }

    /** Smallest size that still covers the screen, so a crop to 16:9 stays sharp. */
    private fun scaleToCover(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        val scale = maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        if (scale >= 1f) {
            return bitmap
        }
        val scaledWidth = (bitmap.width * scale).toInt()
        val scaledHeight = (bitmap.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        bitmap.recycle()
        return scaled
    }

    /** Largest power of two that keeps the image at least as big as the target (cover). */
    private fun sampleSize(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth && sourceHeight / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample
    }

    private fun readBundled(): List<Wallpaper> {
        val text = context.assets.open("wallpapers/manifest.json").use { input -> readText(input) }
        val entries = JSONArray(text)
        val result = ArrayList<Wallpaper>()
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            val fileName = entry.getString("file")
            val title = entry.optString("title", "")
            result.add(Wallpaper("bundled:$fileName", title, "wallpapers/$fileName", null))
        }
        return result
    }

    private fun readText(input: InputStream): String {
        return input.bufferedReader().use { reader -> reader.readText() }
    }

    companion object {
        const val MODE_ALL = "all"
        const val MODE_OWN = "own"
        const val MODE_PINNED = "pinned"

        /** Matches PhotoFraming: narrower photos are shown whole, not cropped. */
        private const val PORTRAIT_BELOW_ASPECT = 1.25f
        private const val SCREEN_WIDTH = 1920
        private const val SCREEN_HEIGHT = 1080
        private const val THUMB_WIDTH = 384
        private const val THUMB_HEIGHT = 216
    }
}
