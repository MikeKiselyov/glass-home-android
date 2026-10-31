package com.glasshome.tv

import android.content.Context
import android.graphics.Typeface

/** The clock typefaces offered on the phone website. */
object Fonts {

    fun clock(context: Context, font: String): Typeface {
        return when (font) {
            "dmserif" -> context.resources.getFont(R.font.dm_serif_display_regular)
            "bebas" -> context.resources.getFont(R.font.bebas_neue_regular)
            "spacemono" -> context.resources.getFont(R.font.space_mono_regular)
            "modern" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            else -> context.resources.getFont(R.font.instrument_serif_regular)
        }
    }

    /** For the date and weather: the italic cut when the family has one. */
    fun caption(context: Context, font: String): Typeface {
        return when (font) {
            "dmserif" -> context.resources.getFont(R.font.dm_serif_display_italic)
            "bebas" -> context.resources.getFont(R.font.bebas_neue_regular)
            "spacemono" -> context.resources.getFont(R.font.space_mono_italic)
            "modern" -> Typeface.create("sans-serif-light", Typeface.ITALIC)
            else -> context.resources.getFont(R.font.instrument_serif_italic)
        }
    }

    /** Bebas and Space Mono are wide or tall; they read best a little smaller. */
    fun clockScale(font: String): Float {
        return when (font) {
            "bebas" -> 1.05f
            "spacemono" -> 0.72f
            "modern" -> 0.9f
            else -> 1f
        }
    }
}
