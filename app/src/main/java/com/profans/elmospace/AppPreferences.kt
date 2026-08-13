package com.profans.elmospace

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppPreferences {
    private const val FILE_NAME = "app_settings"
    private const val KEY_AUTO_SIGN_IN = "auto_sign_in"
    private const val KEY_REFRESH_HOME_AFTER_SIGN_IN = "refresh_home_after_sign_in"
    private const val KEY_SIGN_REFRESH_NOTICE_ACKNOWLEDGED =
        "sign_refresh_notice_acknowledged"
    private const val KEY_SCHEDULED_SIGN_IN = "scheduled_sign_in"
    private const val KEY_SIGN_HOUR = "sign_hour"
    private const val KEY_SIGN_MINUTE = "sign_minute"
    private const val KEY_SIGN_AUTH_TOKEN = "sign_auth_token"
    private const val KEY_SCHEDULED_SIGN_IN_AUTO_ENABLED_ONCE =
        "scheduled_sign_in_auto_enabled_once"
    private const val KEY_AUTO_EXCHANGE_ENABLED = "auto_exchange_enabled"
    private const val KEY_AUTO_EXCHANGE_SELECTED_IDS = "auto_exchange_selected_ids"
    private const val KEY_AUTO_EXCHANGE_TARGET_COUNTS = "auto_exchange_target_counts"
    private const val KEY_AUTO_EXCHANGE_RESERVE_SCORE = "auto_exchange_reserve_score"
    private const val KEY_AUTO_EXCHANGE_LAST_SYNC_ITEMS = "auto_exchange_last_sync_items"
    private const val KEY_AUTO_EXCHANGE_LAST_SYNC_TIME = "auto_exchange_last_sync_time"
    private const val KEY_FEED_PRELOAD = "feed_preload"
    private const val KEY_FEED_PRELOAD_SCREENS = "feed_preload_screens"
    private const val KEY_MOBILE_DATA_WARNING = "mobile_data_warning"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_SPLASH_ANIMATION = "splash_animation"
    private const val KEY_ACCENT_COLOR_MODE = "accent_color_mode"
    private const val KEY_ACCENT_COLOR = "accent_color"
    private const val KEY_ENHANCED_LIKE_INTERACTION = "enhanced_like_interaction"
    private const val KEY_LIKE_EFFECT = "like_effect"
    private const val KEY_LIKE_EFFECT_DURATION_SECONDS = "like_effect_duration_seconds"
    private const val KEY_LIKE_EFFECT_SIZE_MULTIPLIER = "like_effect_size_multiplier"
    private const val KEY_LIKE_EFFECT_ON_UNLIKE = "like_effect_on_unlike"
    private const val KEY_DEVICE_SECURITY_CHECK = "device_security_check"
    private const val KEY_STARTUP_UPDATE_CHECK = "startup_update_check"
    private const val KEY_STARTUP_UPDATE_CHECKS_PER_DAY = "startup_update_checks_per_day"
    private const val KEY_STARTUP_UPDATE_CHECK_DATE = "startup_update_check_date"
    private const val KEY_STARTUP_UPDATE_CHECK_COUNT = "startup_update_check_count"
    private const val KEY_UPDATE_DOWNLOAD_MODE = "update_download_mode"
    private const val KEY_IGNORED_UPDATE_VERSION_CODE = "ignored_update_version_code"
    private const val KEY_REMIND_UPDATE_NEXT_STARTUP_VERSION_CODE =
        "remind_update_next_startup_version_code"

    const val DARK_MODE_FOLLOW_SYSTEM = 0
    const val DARK_MODE_OFF = 1
    const val DARK_MODE_ON = 2

    const val ACCENT_COLOR_DEFAULT = 0
    const val ACCENT_COLOR_WHEEL = 1
    const val ACCENT_COLOR_HEX = 2
    const val ACCENT_COLOR_RGB = 3

    const val UPDATE_DOWNLOAD_MODE_BUILT_IN = 0
    const val UPDATE_DOWNLOAD_MODE_BROWSER = 1

    private fun preferences(context: Context) =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun isAutoSignInEnabled(context: Context) =
        preferences(context).getBoolean(KEY_AUTO_SIGN_IN, true)

    fun setAutoSignInEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_AUTO_SIGN_IN, enabled).apply()
    }

    fun isRefreshHomeAfterSignInEnabled(context: Context) =
        preferences(context).getBoolean(KEY_REFRESH_HOME_AFTER_SIGN_IN, true)

    fun setRefreshHomeAfterSignInEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit()
            .putBoolean(KEY_REFRESH_HOME_AFTER_SIGN_IN, enabled)
            .apply()
    }

    fun isSignRefreshNoticeAcknowledged(context: Context) =
        preferences(context).getBoolean(KEY_SIGN_REFRESH_NOTICE_ACKNOWLEDGED, false)

    fun acknowledgeSignRefreshNotice(context: Context) {
        preferences(context).edit()
            .putBoolean(KEY_SIGN_REFRESH_NOTICE_ACKNOWLEDGED, true)
            .apply()
    }

    fun isScheduledSignInEnabled(context: Context) =
        preferences(context).getBoolean(KEY_SCHEDULED_SIGN_IN, false)

    fun setScheduledSignInEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_SCHEDULED_SIGN_IN, enabled).apply()
    }

    fun getSignHour(context: Context) = preferences(context).getInt(KEY_SIGN_HOUR, 8)

    fun getSignMinute(context: Context) = preferences(context).getInt(KEY_SIGN_MINUTE, 0)

    fun setSignTime(context: Context, hour: Int, minute: Int) {
        preferences(context).edit()
            .putInt(KEY_SIGN_HOUR, hour)
            .putInt(KEY_SIGN_MINUTE, minute)
            .apply()
    }

    fun getSignAuthToken(context: Context) =
        preferences(context).getString(KEY_SIGN_AUTH_TOKEN, null).orEmpty()

    fun setSignAuthToken(context: Context, token: String) {
        preferences(context).edit()
            .putString(KEY_SIGN_AUTH_TOKEN, token)
            .apply()
    }

    fun clearSignAuthToken(context: Context) {
        preferences(context).edit()
            .remove(KEY_SIGN_AUTH_TOKEN)
            .apply()
    }

    fun isScheduledSignInAutoEnabledOnce(context: Context) =
        preferences(context).getBoolean(KEY_SCHEDULED_SIGN_IN_AUTO_ENABLED_ONCE, false)

    fun markScheduledSignInAutoEnabledOnce(context: Context) {
        preferences(context).edit()
            .putBoolean(KEY_SCHEDULED_SIGN_IN_AUTO_ENABLED_ONCE, true)
            .apply()
    }

    fun isAutoExchangeEnabled(context: Context) =
        preferences(context).getBoolean(KEY_AUTO_EXCHANGE_ENABLED, false)

    fun setAutoExchangeEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit()
            .putBoolean(KEY_AUTO_EXCHANGE_ENABLED, enabled)
            .apply()
    }

    fun getAutoExchangeSelectedIds(context: Context): List<Int> =
        preferences(context).getString(KEY_AUTO_EXCHANGE_SELECTED_IDS, null)
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            .orEmpty()

    fun setAutoExchangeSelectedIds(context: Context, ids: List<Int>) {
        preferences(context).edit()
            .putString(KEY_AUTO_EXCHANGE_SELECTED_IDS, ids.distinct().joinToString(","))
            .apply()
    }

    fun getAutoExchangeTargetCount(context: Context, exchangeId: Int): Int {
        if (exchangeId <= 0) return 1
        val json = preferences(context).getString(KEY_AUTO_EXCHANGE_TARGET_COUNTS, null).orEmpty()
        return runCatching { JSONObject(json).optInt(exchangeId.toString(), 1) }
            .getOrDefault(1)
            .coerceAtLeast(1)
    }

    fun setAutoExchangeTargetCount(context: Context, exchangeId: Int, count: Int) {
        if (exchangeId <= 0) return
        val prefs = preferences(context)
        val source = prefs.getString(KEY_AUTO_EXCHANGE_TARGET_COUNTS, null).orEmpty()
        val json = runCatching { JSONObject(source) }.getOrDefault(JSONObject())
        json.put(exchangeId.toString(), count.coerceAtLeast(1))
        prefs.edit()
            .putString(KEY_AUTO_EXCHANGE_TARGET_COUNTS, json.toString())
            .apply()
    }

    fun getAutoExchangeReserveScore(context: Context) =
        preferences(context).getInt(KEY_AUTO_EXCHANGE_RESERVE_SCORE, 0).coerceIn(0, 99_999)

    fun setAutoExchangeReserveScore(context: Context, score: Int) {
        preferences(context).edit()
            .putInt(KEY_AUTO_EXCHANGE_RESERVE_SCORE, score.coerceIn(0, 99_999))
            .apply()
    }

    fun getAutoExchangeLastSyncItems(context: Context) =
        preferences(context).getString(KEY_AUTO_EXCHANGE_LAST_SYNC_ITEMS, null).orEmpty()

    fun setAutoExchangeLastSyncItems(context: Context, itemsJson: String) {
        preferences(context).edit()
            .putString(KEY_AUTO_EXCHANGE_LAST_SYNC_ITEMS, itemsJson)
            .putLong(KEY_AUTO_EXCHANGE_LAST_SYNC_TIME, System.currentTimeMillis())
            .apply()
    }

    fun getAutoExchangeLastSyncTime(context: Context) =
        preferences(context).getLong(KEY_AUTO_EXCHANGE_LAST_SYNC_TIME, 0L)

    fun isFeedPreloadEnabled(context: Context) =
        preferences(context).getBoolean(KEY_FEED_PRELOAD, true)

    fun setFeedPreloadEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_FEED_PRELOAD, enabled).apply()
    }

    fun getFeedPreloadScreens(context: Context) =
        preferences(context).getInt(KEY_FEED_PRELOAD_SCREENS, 1).coerceIn(1, 10)

    fun setFeedPreloadScreens(context: Context, screens: Int) {
        preferences(context).edit()
            .putInt(KEY_FEED_PRELOAD_SCREENS, screens.coerceIn(1, 10))
            .apply()
    }

    fun isMobileDataWarningEnabled(context: Context) =
        preferences(context).getBoolean(KEY_MOBILE_DATA_WARNING, true)

    fun setMobileDataWarningEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_MOBILE_DATA_WARNING, enabled).apply()
    }

    fun getDarkMode(context: Context) =
        preferences(context).getInt(KEY_DARK_MODE, DARK_MODE_FOLLOW_SYSTEM)
            .coerceIn(DARK_MODE_FOLLOW_SYSTEM, DARK_MODE_ON)

    fun setDarkMode(context: Context, mode: Int) {
        preferences(context).edit()
            .putInt(KEY_DARK_MODE, mode.coerceIn(DARK_MODE_FOLLOW_SYSTEM, DARK_MODE_ON))
            .apply()
    }

    fun isSplashAnimationEnabled(context: Context) =
        preferences(context).getBoolean(KEY_SPLASH_ANIMATION, true)

    fun setSplashAnimationEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit()
            .putBoolean(KEY_SPLASH_ANIMATION, enabled)
            .apply()
    }

    fun getAccentColorMode(context: Context) =
        preferences(context).getInt(KEY_ACCENT_COLOR_MODE, ACCENT_COLOR_DEFAULT)
            .coerceIn(ACCENT_COLOR_DEFAULT, ACCENT_COLOR_RGB)

    fun getAccentColor(context: Context) =
        preferences(context).getInt(KEY_ACCENT_COLOR, AppAccentColor.DEFAULT_COLOR)

    fun setDefaultAccentColor(context: Context) {
        preferences(context).edit()
            .putInt(KEY_ACCENT_COLOR_MODE, ACCENT_COLOR_DEFAULT)
            .putInt(KEY_ACCENT_COLOR, AppAccentColor.DEFAULT_COLOR)
            .apply()
    }

    fun setCustomAccentColor(context: Context, mode: Int, color: Int) {
        preferences(context).edit()
            .putInt(KEY_ACCENT_COLOR_MODE, mode.coerceIn(ACCENT_COLOR_WHEEL, ACCENT_COLOR_RGB))
            .putInt(KEY_ACCENT_COLOR, AppAccentColor.normalizeColor(color))
            .apply()
    }

    fun isEnhancedLikeInteractionEnabled(context: Context) =
        preferences(context).getBoolean(KEY_ENHANCED_LIKE_INTERACTION, false)

    fun setEnhancedLikeInteractionEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_ENHANCED_LIKE_INTERACTION, enabled).apply()
    }

    fun getLikeEffect(context: Context) =
        preferences(context).getString(KEY_LIKE_EFFECT, LikeEffectAssets.DEFAULT_ID)
            ?: LikeEffectAssets.DEFAULT_ID

    fun setLikeEffect(context: Context, effectId: String) {
        preferences(context).edit().putString(KEY_LIKE_EFFECT, effectId).apply()
    }

    fun getLikeEffectDurationSeconds(context: Context) =
        preferences(context).getInt(KEY_LIKE_EFFECT_DURATION_SECONDS, 2).coerceIn(1, 10)

    fun setLikeEffectDurationSeconds(context: Context, seconds: Int) {
        preferences(context).edit()
            .putInt(KEY_LIKE_EFFECT_DURATION_SECONDS, seconds.coerceIn(1, 10))
            .apply()
    }

    fun getLikeEffectSizeMultiplier(context: Context) =
        preferences(context).getFloat(KEY_LIKE_EFFECT_SIZE_MULTIPLIER, 1.5f)
            .coerceIn(1f, 5f)

    fun setLikeEffectSizeMultiplier(context: Context, multiplier: Float) {
        val halfStepValue = (multiplier * 2).toInt().coerceIn(2, 10) / 2f
        preferences(context).edit()
            .putFloat(KEY_LIKE_EFFECT_SIZE_MULTIPLIER, halfStepValue)
            .apply()
    }

    fun isLikeEffectOnUnlikeEnabled(context: Context) =
        preferences(context).getBoolean(KEY_LIKE_EFFECT_ON_UNLIKE, false)

    fun setLikeEffectOnUnlikeEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit()
            .putBoolean(KEY_LIKE_EFFECT_ON_UNLIKE, enabled)
            .apply()
    }

    fun isDeviceSecurityCheckEnabled(context: Context) =
        preferences(context).getBoolean(KEY_DEVICE_SECURITY_CHECK, true)

    fun setDeviceSecurityCheckEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit()
            .putBoolean(KEY_DEVICE_SECURITY_CHECK, enabled)
            .apply()
    }

    fun isStartupUpdateCheckEnabled(context: Context) =
        preferences(context).getBoolean(KEY_STARTUP_UPDATE_CHECK, false)

    fun setStartupUpdateCheckEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit()
            .putBoolean(KEY_STARTUP_UPDATE_CHECK, enabled)
            .apply()
    }

    fun getStartupUpdateChecksPerDay(context: Context) =
        preferences(context).getInt(KEY_STARTUP_UPDATE_CHECKS_PER_DAY, 1).coerceIn(1, 3)

    fun setStartupUpdateChecksPerDay(context: Context, count: Int) {
        preferences(context).edit()
            .putInt(KEY_STARTUP_UPDATE_CHECKS_PER_DAY, count.coerceIn(1, 3))
            .apply()
    }

    fun getUpdateDownloadMode(context: Context) =
        preferences(context).getInt(KEY_UPDATE_DOWNLOAD_MODE, UPDATE_DOWNLOAD_MODE_BUILT_IN)
            .coerceIn(UPDATE_DOWNLOAD_MODE_BUILT_IN, UPDATE_DOWNLOAD_MODE_BROWSER)

    fun setUpdateDownloadMode(context: Context, mode: Int) {
        preferences(context).edit()
            .putInt(
                KEY_UPDATE_DOWNLOAD_MODE,
                mode.coerceIn(UPDATE_DOWNLOAD_MODE_BUILT_IN, UPDATE_DOWNLOAD_MODE_BROWSER)
            )
            .apply()
    }

    fun canConsumeStartupUpdateCheck(context: Context): Boolean {
        val prefs = preferences(context)
        val today = todayKey()
        val savedDate = prefs.getString(KEY_STARTUP_UPDATE_CHECK_DATE, null)
        val currentCount = if (savedDate == today) {
            prefs.getInt(KEY_STARTUP_UPDATE_CHECK_COUNT, 0)
        } else {
            0
        }
        return currentCount < getStartupUpdateChecksPerDay(context)
    }

    fun consumeStartupUpdateCheck(context: Context): Boolean {
        if (consumeRemindUpdateNextStartup(context)) return true
        if (!canConsumeStartupUpdateCheck(context)) return false
        val prefs = preferences(context)
        val today = todayKey()
        val savedDate = prefs.getString(KEY_STARTUP_UPDATE_CHECK_DATE, null)
        val currentCount = if (savedDate == today) {
            prefs.getInt(KEY_STARTUP_UPDATE_CHECK_COUNT, 0)
        } else {
            0
        }
        prefs.edit()
            .putString(KEY_STARTUP_UPDATE_CHECK_DATE, today)
            .putInt(KEY_STARTUP_UPDATE_CHECK_COUNT, currentCount + 1)
            .apply()
        return true
    }

    fun getIgnoredUpdateVersionCode(context: Context) =
        preferences(context).getInt(KEY_IGNORED_UPDATE_VERSION_CODE, 0)

    fun setIgnoredUpdateVersionCode(context: Context, versionCode: Int) {
        preferences(context).edit()
            .putInt(KEY_IGNORED_UPDATE_VERSION_CODE, versionCode.coerceAtLeast(0))
            .apply()
    }

    fun remindUpdateNextStartup(context: Context, versionCode: Int) {
        preferences(context).edit()
            .putInt(KEY_REMIND_UPDATE_NEXT_STARTUP_VERSION_CODE, versionCode.coerceAtLeast(0))
            .apply()
    }

    private fun consumeRemindUpdateNextStartup(context: Context): Boolean {
        val prefs = preferences(context)
        val versionCode = prefs.getInt(KEY_REMIND_UPDATE_NEXT_STARTUP_VERSION_CODE, 0)
        if (versionCode <= 0) return false
        prefs.edit()
            .remove(KEY_REMIND_UPDATE_NEXT_STARTUP_VERSION_CODE)
            .apply()
        return true
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
}
