package com.glasshome.tv

import io.nayuki.qrcodegen.QrCode

/** The black and white squares of a QR code, with a white border around them. */
object QrModules {

    /** [row][column] = true for a dark module. */
    fun of(text: String, border: Int): Array<BooleanArray> {
        val qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM)
        val side = qr.size + border * 2
        val rows = Array(side) { BooleanArray(side) }
        for (y in 0 until side) {
            for (x in 0 until side) {
                rows[y][x] = qr.getModule(x - border, y - border)
            }
        }
        return rows
    }
}
