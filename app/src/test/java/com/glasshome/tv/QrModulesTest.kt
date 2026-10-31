package com.glasshome.tv

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class QrModulesTest {

    private val link = "http://192.168.1.20:8090/?code=123456"

    @Test
    fun hasAWhiteBorderAllAround() {
        val modules = QrModules.of(link, border = 2)
        val side = modules.size
        for (index in 0 until side) {
            for (depth in 0 until 2) {
                assertFalse(modules[depth][index])
                assertFalse(modules[side - 1 - depth][index])
                assertFalse(modules[index][depth])
                assertFalse(modules[index][side - 1 - depth])
            }
        }
    }

    @Test
    fun isSquare() {
        val modules = QrModules.of(link, border = 2)
        for (row in modules) {
            assertEquals(modules.size, row.size)
        }
    }

    /** What a phone camera does: read the picture back into the link. */
    @Test
    fun decodesToTheSignInLink() {
        val modules = QrModules.of(link, border = 4)
        val scale = 8
        val side = modules.size * scale
        val pixels = IntArray(side * side)
        for (y in 0 until side) {
            for (x in 0 until side) {
                val dark = modules[y / scale][x / scale]
                pixels[y * side + x] = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        val hints = mapOf(DecodeHintType.PURE_BARCODE to true)

        val decoded = QRCodeReader().decode(bitmap, hints)

        assertEquals(link, decoded.text)
    }
}
