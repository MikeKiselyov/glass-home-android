package com.glasshome.tv

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34])
class IconUploadTest {

    private lateinit var context: Application
    private lateinit var icons: IconStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        icons = IconStore(context)
    }

    private fun pngFile(width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val file = File.createTempFile("icon", ".png", context.cacheDir)
        FileOutputStream(file).use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
        return file
    }

    /**
     * A small file that claims a huge picture (40000 x 40000 is 6.4 GB once
     * decoded). Only the header and one compressed row are real.
     */
    private fun decodeBombFile(width: Int, height: Int): File {
        val png = ByteArrayOutputStream()
        val data = DataOutputStream(png)
        data.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

        val header = ByteArrayOutputStream()
        val headerData = DataOutputStream(header)
        headerData.writeInt(width)
        headerData.writeInt(height)
        headerData.writeByte(8) // bit depth
        headerData.writeByte(6) // RGBA
        headerData.writeByte(0)
        headerData.writeByte(0)
        headerData.writeByte(0)
        writeChunk(data, "IHDR", header.toByteArray())

        val deflater = Deflater()
        deflater.setInput(ByteArray(width * 4 + 1))
        deflater.finish()
        val compressed = ByteArray(1024)
        val length = deflater.deflate(compressed)
        writeChunk(data, "IDAT", compressed.copyOf(length))
        writeChunk(data, "IEND", ByteArray(0))

        val file = File.createTempFile("bomb", ".png", context.cacheDir)
        file.writeBytes(png.toByteArray())
        return file
    }

    private fun writeChunk(output: DataOutputStream, type: String, body: ByteArray) {
        output.writeInt(body.size)
        val typeBytes = type.toByteArray()
        output.write(typeBytes)
        output.write(body)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(body)
        output.writeInt(crc.value.toInt())
    }

    @Test
    fun savesAnOrdinaryPictureAsASquareIcon() {
        assertTrue(icons.addUser(pngFile(1000, 800)))

        val saved = icons.userIcons()
        assertEquals(1, saved.size)
        val file = File(File(context.filesDir, "icons"), saved[0])
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.path, bounds)
        assertEquals(312, bounds.outWidth)
        assertEquals(312, bounds.outHeight)
    }

    @Test
    fun refusesAPictureThatClaimsToBeHuge() {
        val bomb = decodeBombFile(40_000, 40_000)
        assertTrue("the bomb file is small", bomb.length() < 50_000)

        assertFalse(icons.addUser(bomb))
        assertTrue(icons.userIcons().isEmpty())
    }

    /** Just over the limit: small enough that the old code would decode it. */
    @Test
    fun refusesAPictureJustOverTheLimit() {
        assertFalse(icons.addUser(decodeBombFile(10_001, 10_000)))
        assertTrue(icons.userIcons().isEmpty())
    }

    @Test
    fun refusesSomethingThatIsNotAPicture() {
        val file = File.createTempFile("text", ".png", context.cacheDir)
        file.writeText("hello")
        assertFalse(icons.addUser(file))
    }
}
