package com.profans.elmospace

import android.content.Context
import android.content.res.Configuration

object AppTheme {
    fun wrap(context: Context): Context {
        val configuration = Configuration(context.resources.configuration)
        val nightMode = if (isDarkMode(context)) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        configuration.uiMode =
            (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        return context.createConfigurationContext(configuration)
    }

    fun isDarkMode(context: Context): Boolean = when (AppPreferences.getDarkMode(context)) {
        AppPreferences.DARK_MODE_ON -> true
        AppPreferences.DARK_MODE_OFF -> false
        else -> context.applicationContext.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
}
