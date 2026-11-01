package com.glasshome.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

/** One column of the week panel. */
data class WeekDay(
    val weekday: String,
    val iconResource: Int,
    val iconColor: Int,
    val high: String,
    val low: String,
    val rain: String,
)

/**
 * The seven days on the week weather panel, drawn in one view: weekday, icon,
 * high, low and chance of rain in each column.
 *
 * It used to be 7 columns of 5 TextViews. Building those took about 130 ms
 * every time the home screen came back, which on TVs that close apps in the
 * background (Developer options > Don't keep activities) is every Home press.
 * Drawing the same text here takes a few milliseconds.
 */
class WeekForecastView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
) : View(context, attributes) {

    private var days: List<WeekDay> = emptyList()
    private val icons = ArrayList<Drawable?>()

    private val weekdayPaint = textPaint(12f, 0xD9FFFFFF.toInt())
    private val highPaint = textPaint(16f, 0xFFFFFFFF.toInt())
    private val lowPaint = textPaint(12f, 0xB3FFFFFF.toInt())
    private val rainPaint = textPaint(10f, 0xB3FFFFFF.toInt())

    private val columnWidth = dp(COLUMN_WIDTH_DP)
    private val iconSize = dp(ICON_DP)
    private val iconMargin = dp(ICON_MARGIN_DP)

    fun setDays(newDays: List<WeekDay>) {
        if (newDays == days) {
            return
        }
        days = newDays
        icons.clear()
        for (day in newDays) {
            val icon = context.getDrawable(day.iconResource)?.mutate()
            icon?.colorFilter = PorterDuffColorFilter(day.iconColor, PorterDuff.Mode.SRC_ATOP)
            icons.add(icon)
        }
        contentDescription = describe(newDays)
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = columnWidth * days.size + paddingLeft + paddingRight
        val height = columnHeight() + paddingTop + paddingBottom
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        for (index in days.indices) {
            val day = days[index]
            val left = paddingLeft + columnWidth * index
            val centerX = left + columnWidth / 2f
            var top = paddingTop.toFloat()

            // A long word ("100%", another language) stays inside its own column.
            val saved = canvas.save()
            canvas.clipRect(left, 0, left + columnWidth, height)

            top = drawLine(canvas, day.weekday, weekdayPaint, centerX, top)

            top += iconMargin
            val icon = icons[index]
            if (icon != null) {
                val iconLeft = (centerX - iconSize / 2f).toInt()
                icon.setBounds(iconLeft, top.toInt(), iconLeft + iconSize, top.toInt() + iconSize)
                icon.draw(canvas)
            }
            top += iconSize + iconMargin

            top = drawLine(canvas, day.high, highPaint, centerX, top)
            top = drawLine(canvas, day.low, lowPaint, centerX, top)
            drawLine(canvas, day.rain, rainPaint, centerX, top)
            canvas.restoreToCount(saved)
        }
    }

    /** Draws one centred line the way a TextView would place it; returns where the next one starts. */
    private fun drawLine(canvas: Canvas, text: String, paint: Paint, centerX: Float, top: Float): Float {
        val metrics = paint.fontMetricsInt
        canvas.drawText(text, centerX, top - metrics.top, paint)
        return top + lineHeight(paint)
    }

    private fun columnHeight(): Int {
        var height = 0
        height += lineHeight(weekdayPaint)
        height += iconMargin + iconSize + iconMargin
        height += lineHeight(highPaint)
        height += lineHeight(lowPaint)
        height += lineHeight(rainPaint)
        return height
    }

    /** A TextView's line height with font padding, as the columns had before. */
    private fun lineHeight(paint: Paint): Int {
        val metrics = paint.fontMetricsInt
        return metrics.bottom - metrics.top
    }

    private fun textPaint(sizeDp: Float, color: Int): Paint {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.typeface = LIGHT
        paint.color = color
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sizeDp, resources.displayMetrics)
        return paint
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun describe(newDays: List<WeekDay>): String {
        val parts = ArrayList<String>()
        for (day in newDays) {
            parts.add(day.weekday + " " + day.high + " " + day.low)
        }
        return parts.joinToString(", ")
    }

    companion object {
        private const val COLUMN_WIDTH_DP = 46
        private const val ICON_DP = 22
        private const val ICON_MARGIN_DP = 3
        private val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
}
