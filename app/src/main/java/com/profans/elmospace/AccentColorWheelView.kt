package com.profans.elmospace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class AccentColorWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val selectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
        color = Color.WHITE
    }
    private val selectorShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 4f
        color = 0x66000000
    }
    private var wheelBitmap: Bitmap? = null
    private var wheelRadius = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var hsv = floatArrayOf(24f, 0.88f, 0.95f)
    var onColorChanged: ((Int) -> Unit)? = null

    fun setColor(color: Int) {
        Color.colorToHSV(color, hsv)
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        centerX = width / 2f
        centerY = height / 2f
        wheelRadius = min(width, height) / 2f - paddingLeft.coerceAtLeast(4)
        wheelBitmap = createWheelBitmap(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        wheelBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        val angle = Math.toRadians(hsv[0].toDouble())
        val distance = hsv[1].coerceIn(0f, 1f) * wheelRadius
        val x = centerX + cos(angle).toFloat() * distance
        val y = centerY + sin(angle).toFloat() * distance
        canvas.drawCircle(x, y, 9f * resources.displayMetrics.density, selectorShadowPaint)
        canvas.drawCircle(x, y, 9f * resources.displayMetrics.density, selectorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN && event.action != MotionEvent.ACTION_MOVE) {
            return true
        }
        val dx = event.x - centerX
        val dy = event.y - centerY
        val distance = sqrt(dx * dx + dy * dy)
        if (distance > wheelRadius) return true
        var hue = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (hue < 0f) hue += 360f
        hsv[0] = hue
        hsv[1] = (distance / wheelRadius).coerceIn(0f, 1f)
        hsv[2] = 0.95f
        val color = AppAccentColor.normalizeColor(Color.HSVToColor(hsv))
        onColorChanged?.invoke(color)
        invalidate()
        return true
    }

    private fun createWheelBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - centerX
                val dy = y - centerY
                val distance = sqrt(dx * dx + dy * dy)
                pixels[y * width + x] = if (distance <= wheelRadius) {
                    var hue = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (hue < 0f) hue += 360f
                    Color.HSVToColor(floatArrayOf(hue, (distance / wheelRadius).coerceIn(0f, 1f), 0.95f))
                } else {
                    Color.TRANSPARENT
                }
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
