package com.profans.elmospace

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max

class MobileDataUsageChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val scaledDensity = density * resources.configuration.fontScale
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppAccentColor.color(context)
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.nav_divider)
        strokeWidth = density
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.error_text)
        textAlign = Paint.Align.CENTER
        textSize = 10f * scaledDensity
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.nav_unselected)
        textAlign = Paint.Align.CENTER
        textSize = 11f * scaledDensity
    }

    private var values = LongArray(0)
    private var labels = emptyList<String>()
    private var unit = UsageUnit.MB

    fun submitData(values: LongArray, labels: List<String>, unit: UsageUnit) {
        this.values = values
        this.labels = labels
        this.unit = unit
        contentDescription = labels.indices.joinToString(separator = "，") { index ->
            "${labels[index]} ${formatValue(values.getOrElse(index) { 0L })}"
        }
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = max(
            (values.size * 64f * density + 32f * density).toInt(),
            suggestedMinimumWidth
        )
        val desiredHeight = max((360f * density).toInt(), suggestedMinimumHeight)
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return

        val leftPadding = 16f * density
        val rightPadding = 16f * density
        val topPadding = 48f * density
        val bottomPadding = 42f * density
        val baseline = height - bottomPadding
        val chartHeight = max(1f, baseline - topPadding)
        val slotWidth = (width - leftPadding - rightPadding) / values.size
        val barWidth = minOf(30f * density, slotWidth * 0.58f)
        val maximum = max(1L, values.maxOrNull() ?: 0L)

        canvas.drawLine(leftPadding, baseline, width - rightPadding, baseline, axisPaint)
        values.forEachIndexed { index, value ->
            val centerX = leftPadding + slotWidth * (index + 0.5f)
            val ratio = value.toFloat() / maximum.toFloat()
            val visibleHeight = if (value == 0L) 2f * density else max(3f * density, chartHeight * ratio)
            val barTop = baseline - visibleHeight
            canvas.drawRoundRect(
                centerX - barWidth / 2,
                barTop,
                centerX + barWidth / 2,
                baseline,
                5f * density,
                5f * density,
                barPaint
            )
            canvas.drawText(
                formatValue(value),
                centerX,
                max(valuePaint.textSize, barTop - 7f * density),
                valuePaint
            )
            canvas.drawText(
                labels.getOrElse(index) { "" },
                centerX,
                baseline + 22f * density,
                labelPaint
            )
        }
    }

    private fun formatValue(bytes: Long): String {
        val value = bytes.toDouble() / unit.divisor
        val number = when {
            value >= 100 -> String.format(java.util.Locale.getDefault(), "%.0f", value)
            value >= 10 -> String.format(java.util.Locale.getDefault(), "%.1f", value)
            else -> String.format(java.util.Locale.getDefault(), "%.2f", value)
        }
        return "$number ${unit.label}"
    }
}

enum class UsageUnit(val label: String, val divisor: Double) {
    KB("KB", 1024.0),
    MB("MB", 1024.0 * 1024.0),
    GB("GB", 1024.0 * 1024.0 * 1024.0)
}
