package com.profans.elmospace

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.ProgressBar
import android.widget.Switch
import androidx.core.content.ContextCompat

object AppAccentColor {
    val DEFAULT_COLOR: Int = Color.rgb(242, 108, 28)
    const val DEFAULT_HEX = "#F26C1C"
    const val DEFAULT_NAME = "追放橙"
    private val HEX_REGEX = Regex("^#?([0-9a-fA-F]{6})$")

    fun color(context: Context) = normalizeColor(AppPreferences.getAccentColor(context))

    fun mode(context: Context) = AppPreferences.getAccentColorMode(context)

    fun displayName(context: Context) =
        if (mode(context) == AppPreferences.ACCENT_COLOR_DEFAULT) DEFAULT_NAME else "自定义"

    fun normalizeColor(color: Int) = Color.rgb(Color.red(color), Color.green(color), Color.blue(color))

    fun hex(color: Int) = String.format("#%06X", 0xFFFFFF and color)

    fun hex(context: Context) = hex(color(context))

    fun rgbText(color: Int) = "(${Color.red(color)},${Color.green(color)},${Color.blue(color)})"

    fun rgbCss(context: Context): String {
        val color = color(context)
        return "${Color.red(color)},${Color.green(color)},${Color.blue(color)}"
    }

    fun parseHex(raw: String): Int? {
        val value = HEX_REGEX.matchEntire(raw.trim())?.groupValues?.get(1) ?: return null
        return Color.rgb(
            value.substring(0, 2).toInt(16),
            value.substring(2, 4).toInt(16),
            value.substring(4, 6).toInt(16)
        )
    }

    fun circleDrawable(color: Int, strokeColor: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(1, strokeColor)
        }

    fun selectedStateList(context: Context): ColorStateList {
        val unselected = ContextCompat.getColor(context, R.color.nav_unselected)
        return ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
            intArrayOf(color(context), unselected)
        )
    }

    fun switchThumbTint(context: Context): ColorStateList {
        val unselected = ContextCompat.getColor(context, R.color.nav_unselected)
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf()
            ),
            intArrayOf(color(context), withAlpha(unselected, 0.55f), unselected)
        )
    }

    fun switchTrackTint(context: Context): ColorStateList {
        val unselected = ContextCompat.getColor(context, R.color.nav_unselected)
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf()
            ),
            intArrayOf(withAlpha(color(context), 0.52f), withAlpha(unselected, 0.30f), withAlpha(unselected, 0.42f))
        )
    }

    fun tintSwitch(switch: Switch, context: Context) {
        switch.thumbTintList = switchThumbTint(context)
        switch.trackTintList = switchTrackTint(context)
    }

    fun tintProgress(progressBar: ProgressBar, context: Context) {
        progressBar.progressTintList = ColorStateList.valueOf(color(context))
    }

    fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}
