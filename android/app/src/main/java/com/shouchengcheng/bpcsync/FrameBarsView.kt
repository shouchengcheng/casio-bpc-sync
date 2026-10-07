package com.shouchengcheng.bpcsync

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class FrameBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var symbols: List<Int?> = List(60) { null }
    private var currentSecond = 0
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A2E22.toInt() }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = 0xFFD6FF4A.toInt()
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB7B39F.toInt()
        textSize = dp(12f)
    }
    private val rect = RectF()

    fun setState(symbols: List<Int?>, second: Int) {
        this.symbols = symbols
        currentSecond = second
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (dp(28f) * 3 + dp(8f) * 2 + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(max(width, suggestedMinimumWidth), height)
    }

    override fun onDraw(canvas: Canvas) {
        val labelWidth = dp(42f)
        val rowHeight = dp(28f)
        val gap = dp(8f)
        val cellGap = dp(3f)
        val contentWidth = width - paddingLeft - paddingRight - labelWidth
        val cellWidth = (contentWidth - cellGap * 19) / 20f
        val labels = arrayOf(":00", ":20", ":40")
        for (row in 0 until 3) {
            val top = paddingTop + row * (rowHeight + gap)
            val textY = top + rowHeight / 2f - (labelPaint.ascent() + labelPaint.descent()) / 2f
            canvas.drawText(labels[row], paddingLeft.toFloat(), textY, labelPaint)
            for (col in 0 until 20) {
                val index = row * 20 + col
                val left = paddingLeft + labelWidth + col * (cellWidth + cellGap)
                rect.set(left, top, left + cellWidth, top + rowHeight)
                canvas.drawRoundRect(rect, dp(2f), dp(2f), trackPaint)
                val symbol = symbols.getOrNull(index)
                val fraction = if (symbol == null) 1f else (symbol + 1) / 10f
                fillPaint.color = colorFor(symbol)
                val fill = RectF(rect.left, rect.top, rect.left + rect.width() * fraction, rect.bottom)
                canvas.drawRoundRect(fill, dp(2f), dp(2f), fillPaint)
                if (index == currentSecond) {
                    canvas.drawRoundRect(rect, dp(2f), dp(2f), strokePaint)
                }
            }
        }
    }

    private fun colorFor(symbol: Int?): Int {
        return when (symbol) {
            null -> 0xFF8D9280.toInt()
            0 -> 0xFF3D6BFF.toInt()
            1 -> 0xFFE2B340.toInt()
            2 -> 0xFFEF7D32.toInt()
            else -> 0xFFE24B4B.toInt()
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
