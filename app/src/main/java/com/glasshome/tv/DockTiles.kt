package com.glasshome.tv

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Builds dock tiles in the style of each theme:
 *   clock    small icons, the name beside the focused one
 *   glass    large icons on a frosted bar, the name under the bar ([onFocusName])
 *   postcard a vertical menu: icon and name on each row
 *   type     no icons: app names as large words
 * Focus handling and the power dot for HDMI devices are shared.
 */
class DockTiles(
    private val context: Context,
    private val theme: String,
    private val onFocused: (DockItem) -> Unit,
) {

    fun make(item: DockItem, typeface: Typeface): View {
        val tile = when (theme) {
            "glass" -> glassTile(item)
            "postcard" -> menuRow(item)
            "type" -> wordTile(item, typeface)
            else -> clockTile(item)
        }
        tile.tag = item.key
        tile.isFocusable = true
        tile.isClickable = true
        tile.isLongClickable = true

        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        when (theme) {
            "glass" -> params.marginEnd = dp(14)
            "postcard" -> params.bottomMargin = dp(2)
            "type" -> params.marginEnd = dp(34)
            else -> params.marginEnd = dp(21)
        }
        tile.layoutParams = params
        return tile
    }

    // ---------- clock: small icons ----------

    private fun clockTile(item: DockItem): View {
        val tile = LinearLayout(context)
        tile.orientation = LinearLayout.HORIZONTAL
        tile.gravity = Gravity.CENTER_VERTICAL

        val parts = iconFrame(item, CLOCK_ICON)
        tile.addView(parts.frame, LinearLayout.LayoutParams(dp(CLOCK_ICON), dp(CLOCK_ICON)))

        val name = label(item.label, 13f)
        name.visibility = View.GONE
        val nameParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        nameParams.marginStart = dp(12)
        tile.addView(name, nameParams)

        parts.frame.alpha = UNFOCUSED_ALPHA
        tile.setOnFocusChangeListener { _, hasFocus ->
            val size = if (hasFocus) dp(CLOCK_ICON_FOCUSED) else dp(CLOCK_ICON)
            val params = parts.frame.layoutParams
            params.width = size
            params.height = size
            parts.frame.layoutParams = params
            parts.frame.alpha = if (hasFocus) 1f else UNFOCUSED_ALPHA
            parts.ring.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
            name.visibility = if (hasFocus) View.VISIBLE else View.GONE
            if (hasFocus) {
                onFocused(item)
            }
        }
        return tile
    }

    // ---------- glass: large icons, lift on focus ----------

    private fun glassTile(item: DockItem): View {
        val parts = iconFrame(item, GLASS_ICON)
        val tile = FrameLayout(context)
        tile.clipChildren = false
        tile.addView(parts.frame, FrameLayout.LayoutParams(dp(GLASS_ICON), dp(GLASS_ICON)))
        tile.setOnFocusChangeListener { _, hasFocus ->
            val scale = if (hasFocus) 1.16f else 1f
            val lift = if (hasFocus) -dp(6).toFloat() else 0f
            tile.animate().scaleX(scale).scaleY(scale).translationY(lift).setDuration(180).start()
            parts.ring.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
            tile.elevation = if (hasFocus) dp(12).toFloat() else 0f
            if (hasFocus) {
                onFocused(item)
            }
        }
        return tile
    }

    // ---------- postcard: menu rows ----------

    private fun menuRow(item: DockItem): View {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setBackgroundResource(R.drawable.menu_item)
        row.setPadding(dp(9), dp(5), dp(14), dp(5))

        val parts = iconFrame(item, MENU_ICON)
        row.addView(parts.frame, LinearLayout.LayoutParams(dp(MENU_ICON), dp(MENU_ICON)))

        val name = TextView(context)
        name.text = item.label
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        name.setTextColor(context.resources.getColorStateList(R.color.menu_text, context.theme))
        name.paint.isFakeBoldText = true
        // The row takes focus, not the name: share the row's state so the name
        // turns dark on the row's white focus background.
        name.isDuplicateParentStateEnabled = true
        val nameParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        nameParams.marginStart = dp(10)
        row.addView(name, nameParams)

        row.setOnFocusChangeListener { _, hasFocus ->
            name.isSelected = hasFocus
            if (hasFocus) {
                onFocused(item)
            }
        }
        return row
    }

    // ---------- type: words ----------

    private fun wordTile(item: DockItem, typeface: Typeface): View {
        val word = TextView(context)
        word.text = item.label
        word.typeface = typeface
        word.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 40f)
        word.setTextColor(0x8CFFFFFF.toInt())
        word.setShadowLayer(16f, 0f, 2f, 0x99000000.toInt())
        word.includeFontPadding = false
        if (item.isOn) {
            word.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.power_dot, 0)
            word.compoundDrawablePadding = dp(8)
        }
        word.setOnFocusChangeListener { _, hasFocus ->
            word.setTextColor(if (hasFocus) 0xFFFFFFFF.toInt() else 0x8CFFFFFF.toInt())
            word.paint.isFakeBoldText = hasFocus
            word.invalidate()
            if (hasFocus) {
                onFocused(item)
            }
        }
        return word
    }

    // ---------- shared ----------

    private class IconParts(val frame: FrameLayout, val ring: View)

    /** Rounded icon with a focus ring and, for HDMI devices that are on, a green dot. */
    private fun iconFrame(item: DockItem, sizeDp: Int): IconParts {
        val frame = FrameLayout(context)
        val icon = ImageView(context)
        icon.setImageDrawable(item.icon)
        icon.scaleType = ImageView.ScaleType.CENTER_CROP
        icon.setBackgroundResource(R.drawable.tile_clip)
        icon.clipToOutline = true
        frame.addView(icon, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val ring = View(context)
        ring.setBackgroundResource(R.drawable.tile_ring)
        ring.visibility = View.INVISIBLE
        frame.addView(ring, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        if (item.isOn) {
            val dotSize = if (sizeDp < 32) 7 else 10
            val dot = View(context)
            dot.tag = POWER_DOT
            dot.setBackgroundResource(R.drawable.power_dot)
            val dotParams = FrameLayout.LayoutParams(dp(dotSize), dp(dotSize), Gravity.BOTTOM or Gravity.END)
            dotParams.setMargins(0, 0, dp(2), dp(2))
            frame.addView(dot, dotParams)
        }
        return IconParts(frame, ring)
    }

    private fun label(text: String, sizeDp: Float): TextView {
        val view = TextView(context)
        view.text = text
        view.setTextColor(0xFFFFFFFF.toInt())
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp)
        view.setShadowLayer(8f, 0f, 1f, 0x99000000.toInt())
        view.paint.isFakeBoldText = true
        return view
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val CLOCK_ICON = 40
        private const val CLOCK_ICON_FOCUSED = 52
        private const val GLASS_ICON = 62
        private const val MENU_ICON = 26
        private const val UNFOCUSED_ALPHA = 0.78f

        /** Tag of the green dot on HDMI devices that are on. */
        const val POWER_DOT = "power-dot"
    }
}
