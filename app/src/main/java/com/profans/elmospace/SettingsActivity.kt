package com.profans.elmospace

import android.Manifest
import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.app.AlertDialog
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.animation.addListener
import androidx.core.content.ContextCompat
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

class SettingsActivity : ComponentActivity() {
    private lateinit var scheduledSwitch: Switch
    private lateinit var timeRow: LinearLayout
    private lateinit var timeValue: TextView
    private lateinit var cacheSizeValue: TextView
    private lateinit var preloadScreensRow: LinearLayout
    private lateinit var preloadScreensValue: TextView
    private lateinit var darkModeValue: TextView
    private lateinit var likeEffectRow: LinearLayout
    private lateinit var likeEffectDivider: View
    private lateinit var likeEffectManagerRow: LinearLayout
    private lateinit var likeEffectManagerDivider: View
    private lateinit var likeEffectOnUnlikeRow: LinearLayout
    private lateinit var likeEffectOnUnlikeDivider: View
    private lateinit var likeEffectValue: TextView
    private lateinit var likeEffectDurationDivider: View
    private lateinit var likeEffectDurationRow: LinearLayout
    private lateinit var likeEffectDurationValue: TextView
    private lateinit var likeEffectSizeDivider: View
    private lateinit var likeEffectSizeRow: LinearLayout
    private lateinit var likeEffectSizeValue: TextView
    private lateinit var likeEffectPreviewDivider: View
    private lateinit var likeEffectPreviewRow: LinearLayout
    private lateinit var likeEffectPreviewButton: ImageView
    private lateinit var notificationPermissionStatus: TextView
    private lateinit var exactAlarmPermissionStatus: TextView
    private lateinit var cameraPermissionStatus: TextView
    private lateinit var locationPermissionStatus: TextView
    private var runTestAfterNotificationPermission = false
    private val likeEffectPreviewHandler = Handler(Looper.getMainLooper())
    private var likeEffectPreviewRunning = false
    private val likeEffectPreviewRunnable = object : Runnable {
        override fun run() {
            if (!likeEffectPreviewRunning) return
            spawnLikeEffectPreview()
            likeEffectPreviewHandler.postDelayed(this, LIKE_EFFECT_PREVIEW_INTERVAL_MS)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (runTestAfterNotificationPermission) {
            runTestAfterNotificationPermission = false
            if (granted) {
                startTestScheduledSignIn()
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppTheme.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)
        applyInsets()

        scheduledSwitch = findViewById(R.id.scheduledSignSwitch)
        timeRow = findViewById(R.id.timeRow)
        timeValue = findViewById(R.id.timeValue)
        cacheSizeValue = findViewById(R.id.cacheSizeValue)
        preloadScreensRow = findViewById(R.id.preloadScreensRow)
        preloadScreensValue = findViewById(R.id.preloadScreensValue)
        darkModeValue = findViewById(R.id.darkModeValue)
        likeEffectRow = findViewById(R.id.likeEffectRow)
        likeEffectDivider = findViewById(R.id.likeEffectDivider)
        likeEffectManagerRow = findViewById(R.id.likeEffectManagerRow)
        likeEffectManagerDivider = findViewById(R.id.likeEffectManagerDivider)
        likeEffectOnUnlikeRow = findViewById(R.id.likeEffectOnUnlikeRow)
        likeEffectOnUnlikeDivider = findViewById(R.id.likeEffectOnUnlikeDivider)
        likeEffectValue = findViewById(R.id.likeEffectValue)
        likeEffectDurationDivider = findViewById(R.id.likeEffectDurationDivider)
        likeEffectDurationRow = findViewById(R.id.likeEffectDurationRow)
        likeEffectDurationValue = findViewById(R.id.likeEffectDurationValue)
        likeEffectSizeDivider = findViewById(R.id.likeEffectSizeDivider)
        likeEffectSizeRow = findViewById(R.id.likeEffectSizeRow)
        likeEffectSizeValue = findViewById(R.id.likeEffectSizeValue)
        likeEffectPreviewDivider = findViewById(R.id.likeEffectPreviewDivider)
        likeEffectPreviewRow = findViewById(R.id.likeEffectPreviewRow)
        likeEffectPreviewButton = findViewById(R.id.likeEffectPreviewButton)
        notificationPermissionStatus = findViewById(R.id.notificationPermissionStatus)
        exactAlarmPermissionStatus = findViewById(R.id.exactAlarmPermissionStatus)
        cameraPermissionStatus = findViewById(R.id.cameraPermissionStatus)
        locationPermissionStatus = findViewById(R.id.locationPermissionStatus)

        findViewById<View>(R.id.settingsBack).setOnClickListener { finishWithTransition() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishWithTransition()
        })

        findViewById<View>(R.id.privacyRow).setOnClickListener { openOfficialPath("/m/priSet") }
        findViewById<View>(R.id.feedbackRow).setOnClickListener { openOfficialPath("/m/feedback") }
        findViewById<View>(R.id.logoutRow).setOnClickListener {
            openOfficialPath("/m/set", allowOfficialSettings = true)
        }

        val deviceSecurityCheckSwitch = findViewById<Switch>(R.id.deviceSecurityCheckSwitch)
        deviceSecurityCheckSwitch.isChecked =
            AppPreferences.isDeviceSecurityCheckEnabled(this)
        deviceSecurityCheckSwitch.setOnCheckedChangeListener { _, checked ->
            AppPreferences.setDeviceSecurityCheckEnabled(this, checked)
        }
        findViewById<View>(R.id.deviceSecurityCheckRow).setOnClickListener {
            deviceSecurityCheckSwitch.isChecked = !deviceSecurityCheckSwitch.isChecked
        }

        val autoSignSwitch = findViewById<Switch>(R.id.autoSignSwitch)
        autoSignSwitch.isChecked = AppPreferences.isAutoSignInEnabled(this)
        autoSignSwitch.setOnCheckedChangeListener { _, checked ->
            AppPreferences.setAutoSignInEnabled(this, checked)
        }
        findViewById<View>(R.id.autoSignRow).setOnClickListener {
            autoSignSwitch.isChecked = !autoSignSwitch.isChecked
        }

        val signRefreshSwitch = findViewById<Switch>(R.id.signRefreshSwitch)
        signRefreshSwitch.isChecked = AppPreferences.isRefreshHomeAfterSignInEnabled(this)
        signRefreshSwitch.setOnCheckedChangeListener { _, checked ->
            AppPreferences.setRefreshHomeAfterSignInEnabled(this, checked)
        }
        findViewById<View>(R.id.signRefreshRow).setOnClickListener {
            signRefreshSwitch.isChecked = !signRefreshSwitch.isChecked
        }

        scheduledSwitch.isChecked = AppPreferences.isScheduledSignInEnabled(this)
        scheduledSwitch.setOnCheckedChangeListener { _, checked ->
            AppPreferences.setScheduledSignInEnabled(this, checked)
            updateTimeEnabledState()
            if (checked) {
                if (!SignInScheduler.scheduleNext(this)) {
                    showExactAlarmPermissionDialogIfNeeded()
                }
                showNotificationPermissionDialogIfNeeded()
            } else {
                SignInScheduler.cancel(this)
            }
        }
        findViewById<View>(R.id.scheduledSignRow).setOnClickListener {
            scheduledSwitch.isChecked = !scheduledSwitch.isChecked
        }

        timeRow.setOnClickListener { showTimePicker() }
        findViewById<View>(R.id.testScheduledSignRow).setOnClickListener {
            testScheduledSignIn()
        }

        val mobileDataWarningSwitch = findViewById<Switch>(R.id.mobileDataWarningSwitch)
        mobileDataWarningSwitch.isChecked = AppPreferences.isMobileDataWarningEnabled(this)
        mobileDataWarningSwitch.setOnCheckedChangeListener { _, checked ->
            AppPreferences.setMobileDataWarningEnabled(this, checked)
        }
        findViewById<View>(R.id.mobileDataWarningRow).setOnClickListener {
            mobileDataWarningSwitch.isChecked = !mobileDataWarningSwitch.isChecked
        }
        findViewById<View>(R.id.mobileDataUsageRow).setOnClickListener {
            openMobileDataUsage()
        }
        findViewById<View>(R.id.notificationPermissionButton).setOnClickListener {
            showPermissionChangeDialog(R.string.permission_notification_impact) {
                openNotificationSettings()
            }
        }
        findViewById<View>(R.id.exactAlarmPermissionButton).setOnClickListener {
            showPermissionChangeDialog(R.string.permission_exact_alarm_impact) {
                openExactAlarmSettings()
            }
        }
        findViewById<View>(R.id.cameraPermissionButton).setOnClickListener {
            showPermissionChangeDialog(R.string.permission_camera_impact) {
                showAppInfoRedirectDialog(R.string.permission_camera_app_info_redirect)
            }
        }
        findViewById<View>(R.id.locationPermissionButton).setOnClickListener {
            showPermissionChangeDialog(R.string.permission_location_impact) {
                showAppInfoRedirectDialog(R.string.permission_location_app_info_redirect)
            }
        }
        findViewById<View>(R.id.powerPolicyButton).setOnClickListener {
            openAppSystemSettings()
        }

        val preloadSwitch = findViewById<Switch>(R.id.feedPreloadSwitch)
        preloadSwitch.isChecked = AppPreferences.isFeedPreloadEnabled(this)
        preloadSwitch.setOnCheckedChangeListener { _, checked ->
            AppPreferences.setFeedPreloadEnabled(this, checked)
            updatePreloadEnabledState(checked)
        }
        findViewById<View>(R.id.feedPreloadRow).setOnClickListener {
            preloadSwitch.isChecked = !preloadSwitch.isChecked
        }
        preloadScreensRow.setOnClickListener { showPreloadScreensPicker() }
        findViewById<View>(R.id.darkModeRow).setOnClickListener { showDarkModePicker() }
        val enhancedLikeSwitch = findViewById<Switch>(R.id.enhancedLikeSwitch)
        enhancedLikeSwitch.isChecked = AppPreferences.isEnhancedLikeInteractionEnabled(this)
        enhancedLikeSwitch.setOnCheckedChangeListener { _, checked ->
            AppPreferences.setEnhancedLikeInteractionEnabled(this, checked)
            updateLikeEffectVisibility(checked)
        }
        findViewById<View>(R.id.enhancedLikeRow).setOnClickListener {
            enhancedLikeSwitch.isChecked = !enhancedLikeSwitch.isChecked
        }
        val likeEffectOnUnlikeSwitch = findViewById<Switch>(R.id.likeEffectOnUnlikeSwitch)
        likeEffectOnUnlikeSwitch.isChecked =
            AppPreferences.isLikeEffectOnUnlikeEnabled(this)
        likeEffectOnUnlikeSwitch.setOnCheckedChangeListener { _, checked ->
            AppPreferences.setLikeEffectOnUnlikeEnabled(this, checked)
        }
        likeEffectOnUnlikeRow.setOnClickListener {
            likeEffectOnUnlikeSwitch.isChecked = !likeEffectOnUnlikeSwitch.isChecked
        }
        likeEffectRow.setOnClickListener { showLikeEffectPicker() }
        likeEffectManagerRow.setOnClickListener { openLikeEffectManager() }
        likeEffectDurationRow.setOnClickListener { showLikeEffectDurationPicker() }
        likeEffectSizeRow.setOnClickListener { showLikeEffectSizePicker() }
        likeEffectPreviewRow.setOnClickListener { toggleLikeEffectPreview() }
        likeEffectPreviewButton.setOnClickListener { toggleLikeEffectPreview() }
        findViewById<View>(R.id.changelogRow).setOnClickListener { openChangelog() }

        findViewById<View>(R.id.clearCacheRow).setOnClickListener { clearWebCache() }
        updateDisplayedTime()
        updateTimeEnabledState()
        updateDisplayedPreloadScreens()
        updatePreloadEnabledState(preloadSwitch.isChecked)
        updateDarkModeValue()
        updateLikeEffectValue()
        updateLikeEffectDurationValue()
        updateLikeEffectSizeValue()
        updateLikeEffectVisibility(enhancedLikeSwitch.isChecked)
        updatePermissionStatuses()
        updateCacheSize()
    }

    override fun onResume() {
        super.onResume()
        if (AppPreferences.isScheduledSignInEnabled(this)) {
            SignInScheduler.scheduleNext(this)
        }
        updatePermissionStatuses()
        updateLikeEffectValue()
    }

    override fun onPause() {
        super.onPause()
        stopLikeEffectPreview()
    }

    private fun applyInsets() {
        val root = findViewById<View>(R.id.settingsRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun showTimePicker() {
        if (!scheduledSwitch.isChecked) return
        TimePickerDialog(
            this,
            { _, hour, minute ->
                AppPreferences.setSignTime(this, hour, minute)
                updateDisplayedTime()
                if (!SignInScheduler.scheduleNext(this)) {
                    showExactAlarmPermissionDialogIfNeeded()
                }
            },
            AppPreferences.getSignHour(this),
            AppPreferences.getSignMinute(this),
            true
        ).show()
    }

    private fun updateDisplayedTime() {
        timeValue.text = String.format(
            Locale.getDefault(),
            "%02d:%02d",
            AppPreferences.getSignHour(this),
            AppPreferences.getSignMinute(this)
        )
    }

    private fun updateTimeEnabledState() {
        timeRow.isEnabled = scheduledSwitch.isChecked
        timeRow.alpha = if (scheduledSwitch.isChecked) 1f else 0.45f
    }

    private fun testScheduledSignIn() {
        if (!SignInScheduler.canScheduleExactAlarms(this)) {
            showExactAlarmPermissionDialogIfNeeded()
            return
        }
        if (!hasNotificationPermission()) {
            runTestAfterNotificationPermission = true
            showNotificationPermissionDialogIfNeeded()
            return
        }
        if (isStrictPowerManagementRom()) {
            showStrictPowerManagementDialog()
            return
        }
        startTestScheduledSignIn()
    }

    private fun showStrictPowerManagementDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.strict_power_management_title)
            .setMessage(R.string.strict_power_management_message)
            .setPositiveButton(R.string.strict_power_management_go_settings) { _, _ ->
                openAppSystemSettings()
            }
            .setNegativeButton(R.string.strict_power_management_continue_test) { _, _ ->
                startTestScheduledSignIn()
            }
            .show()
    }

    private fun isStrictPowerManagementRom(): Boolean {
        val values = listOf(Build.MANUFACTURER, Build.BRAND)
            .map { it.lowercase(Locale.ROOT) }
        val markers = listOf(
            "xiaomi",
            "redmi",
            "poco",
            "oppo",
            "oneplus",
            "realme",
            "vivo",
            "iqoo",
            "huawei",
            "honor"
        )
        return values.any { value -> markers.any { marker -> value.contains(marker) } }
    }

    private fun openAppSystemSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
        )
    }

    private fun startTestScheduledSignIn() {
        if (!SignInScheduler.scheduleTest(this)) {
            showExactAlarmPermissionDialogIfNeeded()
            return
        }
        val finishAtMillis = System.currentTimeMillis() + TestSignInCountdownService.COUNTDOWN_MS
        ContextCompat.startForegroundService(
            this,
            Intent(this, TestSignInCountdownService::class.java)
                .putExtra(TestSignInCountdownService.EXTRA_FINISH_AT_MILLIS, finishAtMillis)
        )
        AlertDialog.Builder(this)
            .setMessage(R.string.test_scheduled_sign_notice)
            .setPositiveButton(R.string.permission_confirm, null)
            .show()
    }

    private fun createScheduledSignInNotificationChannel() {
        val channel = NotificationChannel(
            ScheduledSignInService.CHANNEL_ID,
            "定时自动签到",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "显示定时自动签到执行状态和结果" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun showPreloadScreensPicker() {
        if (!preloadScreensRow.isEnabled) return
        showModernNumberPicker(
            titleRes = R.string.preload_screens,
            minValue = 1,
            maxValue = 10,
            selectedValue = AppPreferences.getFeedPreloadScreens(this),
            displayedValues = Array(10) { index ->
                getString(R.string.preload_screen_value, index + 1)
            }
        ) { value ->
                AppPreferences.setFeedPreloadScreens(this, value)
                updateDisplayedPreloadScreens()
        }
    }

    private fun updateDisplayedPreloadScreens() {
        preloadScreensValue.text = getString(
            R.string.preload_screen_value,
            AppPreferences.getFeedPreloadScreens(this)
        )
    }

    private fun updatePreloadEnabledState(enabled: Boolean) {
        preloadScreensRow.isEnabled = enabled
        preloadScreensRow.alpha = if (enabled) 1f else 0.45f
    }

    private fun showDarkModePicker() {
        val modes = intArrayOf(
            AppPreferences.DARK_MODE_FOLLOW_SYSTEM,
            AppPreferences.DARK_MODE_ON,
            AppPreferences.DARK_MODE_OFF
        )
        val labels = arrayOf(
            getString(R.string.dark_mode_follow_system),
            getString(R.string.dark_mode_on),
            getString(R.string.dark_mode_off)
        )
        val checked = modes.indexOf(AppPreferences.getDarkMode(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.dark_mode)
            .setSingleChoiceItems(labels, checked) { dialog, index ->
                AppPreferences.setDarkMode(this, modes[index])
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(R.string.permission_cancel, null)
            .show()
    }

    private fun updateDarkModeValue() {
        darkModeValue.setText(
            when (AppPreferences.getDarkMode(this)) {
                AppPreferences.DARK_MODE_ON -> R.string.dark_mode_on
                AppPreferences.DARK_MODE_OFF -> R.string.dark_mode_off
                else -> R.string.dark_mode_follow_system
            }
        )
    }

    private fun showLikeEffectPicker() {
        val options = LikeEffectAssets.pickerOptions(this)
        val selectedId = AppPreferences.getLikeEffect(this)
        val checked = options.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        val labels = options.map { it.displayName }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.select_like_effect)
            .setSingleChoiceItems(labels, checked) { dialog, index ->
                AppPreferences.setLikeEffect(this, options[index].id)
                updateLikeEffectValue()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.permission_cancel, null)
            .show()
    }

    private fun updateLikeEffectValue() {
        likeEffectValue.text = LikeEffectAssets.findSelection(
            this,
            AppPreferences.getLikeEffect(this)
        ).displayName
    }

    private fun showLikeEffectDurationPicker() {
        showModernNumberPicker(
            titleRes = R.string.like_effect_duration,
            minValue = 1,
            maxValue = 10,
            selectedValue = AppPreferences.getLikeEffectDurationSeconds(this),
            displayedValues = Array(10) { index ->
                getString(R.string.like_effect_duration_value, index + 1)
            }
        ) { value ->
            AppPreferences.setLikeEffectDurationSeconds(this, value)
            updateLikeEffectDurationValue()
        }
    }

    private fun updateLikeEffectDurationValue() {
        likeEffectDurationValue.text = getString(
            R.string.like_effect_duration_value,
            AppPreferences.getLikeEffectDurationSeconds(this)
        )
    }

    private fun showLikeEffectSizePicker() {
        val labels = Array(9) { index -> formatLikeEffectSize(1f + index * 0.5f) }
        val selectedIndex = (
            (AppPreferences.getLikeEffectSizeMultiplier(this) - 1f) * 2f + 0.5f
        ).toInt().coerceIn(0, 8)
        showModernNumberPicker(
            titleRes = R.string.like_effect_size,
            minValue = 0,
            maxValue = labels.lastIndex,
            selectedValue = selectedIndex,
            displayedValues = labels
        ) { value ->
            AppPreferences.setLikeEffectSizeMultiplier(this, 1f + value * 0.5f)
            updateLikeEffectSizeValue()
        }
    }

    private fun showModernNumberPicker(
        @StringRes titleRes: Int,
        minValue: Int,
        maxValue: Int,
        selectedValue: Int,
        displayedValues: Array<String>,
        onConfirm: (Int) -> Unit
    ) {
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.dialog_number_picker)
            setCanceledOnTouchOutside(true)
        }
        val picker = dialog.findViewById<NumberPicker>(R.id.modernNumberPicker).apply {
            this.displayedValues = null
            this.minValue = minValue
            this.maxValue = maxValue
            this.displayedValues = displayedValues
            value = selectedValue.coerceIn(minValue, maxValue)
            wrapSelectorWheel = false
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                textColor = ContextCompat.getColor(this@SettingsActivity, R.color.error_text)
            } else {
                for (index in 0 until childCount) {
                    (getChildAt(index) as? TextView)?.setTextColor(
                        ContextCompat.getColor(this@SettingsActivity, R.color.error_text)
                    )
                }
            }
        }
        dialog.findViewById<TextView>(R.id.pickerDialogTitle).setText(titleRes)
        dialog.findViewById<View>(R.id.pickerCancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.pickerConfirm).setOnClickListener {
            onConfirm(picker.value)
            dialog.dismiss()
        }
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.48f }
        }
        dialog.show()
        val margin = (32 * resources.displayMetrics.density).toInt()
        dialog.window?.setLayout(
            resources.displayMetrics.widthPixels - margin,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun updateLikeEffectSizeValue() {
        likeEffectSizeValue.text = formatLikeEffectSize(
            AppPreferences.getLikeEffectSizeMultiplier(this)
        )
    }

    private fun formatLikeEffectSize(multiplier: Float): String {
        val number = if (multiplier % 1f == 0f) {
            multiplier.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", multiplier)
        }
        return getString(R.string.like_effect_size_value, number)
    }

    private fun toggleLikeEffectPreview() {
        if (likeEffectPreviewRunning) {
            stopLikeEffectPreview()
        } else {
            startLikeEffectPreview()
        }
    }

    private fun startLikeEffectPreview() {
        if (likeEffectPreviewRunning) return
        likeEffectPreviewRunning = true
        likeEffectPreviewButton.setImageResource(R.drawable.ic_pause)
        spawnLikeEffectPreview()
        likeEffectPreviewHandler.postDelayed(
            likeEffectPreviewRunnable,
            LIKE_EFFECT_PREVIEW_INTERVAL_MS
        )
    }

    private fun stopLikeEffectPreview() {
        if (!likeEffectPreviewRunning) return
        likeEffectPreviewRunning = false
        likeEffectPreviewHandler.removeCallbacks(likeEffectPreviewRunnable)
        if (::likeEffectPreviewButton.isInitialized) {
            likeEffectPreviewButton.setImageResource(R.drawable.ic_play_arrow)
        }
    }

    private fun spawnLikeEffectPreview() {
        if (!::likeEffectPreviewButton.isInitialized || !likeEffectPreviewButton.isShown) return
        val content = window.decorView.findViewById<FrameLayout>(android.R.id.content) ?: return
        val effectId = AppPreferences.getLikeEffect(this)
        val option = if (effectId == LikeEffectAssets.RANDOM_ID) {
            LikeEffectAssets.options(this).random()
        } else {
            LikeEffectAssets.find(this, effectId)
        }

        val multiplier = AppPreferences.getLikeEffectSizeMultiplier(this).coerceIn(1f, 5f)
        val duration = (AppPreferences.getLikeEffectDurationSeconds(this) * 1000L)
            .coerceIn(1000L, 10000L)
        val contentLocation = IntArray(2)
        val buttonLocation = IntArray(2)
        content.getLocationOnScreen(contentLocation)
        likeEffectPreviewButton.getLocationOnScreen(buttonLocation)

        val baseWidth = content.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val size = (
            baseWidth *
                LIKE_EFFECT_DETAIL_SIZE_RATIO *
                LIKE_EFFECT_BASE_SCALE *
                LIKE_EFFECT_PREVIEW_SIZE_CALIBRATION *
                multiplier
        ).toInt().coerceAtLeast(1)

        val startX = buttonLocation[0] - contentLocation[0] +
            likeEffectPreviewButton.width / 2f - size / 2f
        val startY = buttonLocation[1] - contentLocation[1] - size * 0.9f
        val endY = content.height + size * 1.25f
        val leftTravel = minOf(content.width * 0.28f, size * 4.2f)
        val jumpHeight = size * 2.4f

        val image = ImageView(this).apply {
            if (option.type == LikeEffectAssetType.CUSTOM) {
                setImageURI(Uri.fromFile(LikeEffectCustomAssetRepository.imageFile(this@SettingsActivity, option.fileName)))
            } else {
                setImageResource(option.drawableRes)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = false
            isFocusable = false
            alpha = 1f
            rotation = 0f
            layoutParams = FrameLayout.LayoutParams(size, size)
            elevation = 30f
        }
        content.addView(image)

        ValueAnimator.ofFloat(0f, 1f).apply {
            interpolator = LinearInterpolator()
            this.duration = duration
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                val x = startX - leftTravel * t
                val y = startY -
                    jumpHeight * 4f * t * (1f - t) +
                    (endY - startY) * t * t
                image.translationX = x
                image.translationY = y
                image.rotation = -35f * t
                image.alpha = if (t < 0.86f) 1f else (1f - t) / 0.14f
            }
            addListener(
                onEnd = { content.removeView(image) },
                onCancel = { content.removeView(image) }
            )
            start()
        }
    }

    private fun updateLikeEffectVisibility(enabled: Boolean) {
        if (!enabled) stopLikeEffectPreview()
        val visibility = if (enabled) View.VISIBLE else View.GONE
        listOf(
            likeEffectOnUnlikeDivider,
            likeEffectOnUnlikeRow,
            likeEffectDivider,
            likeEffectRow,
            likeEffectManagerDivider,
            likeEffectManagerRow,
            likeEffectDurationDivider,
            likeEffectDurationRow,
            likeEffectSizeDivider,
            likeEffectSizeRow,
            likeEffectPreviewDivider,
            likeEffectPreviewRow
        ).forEach { it.visibility = visibility }
    }

    private fun updateCacheSize() {
        cacheSizeValue.text = getString(R.string.cache_size_calculating)
        Thread {
            val size = WebCacheUtils.getCacheSize(applicationContext)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    cacheSizeValue.text = WebCacheUtils.formatSize(size)
                }
            }
        }.start()
    }

    private fun clearWebCache() {
        val cacheRow = findViewById<View>(R.id.clearCacheRow)
        cacheRow.isEnabled = false
        cacheSizeValue.text = getString(R.string.cache_clearing)

        // Resource cache is separate from cookies and DOM storage, so login state remains intact.
        WebView(this).apply {
            clearCache(true)
            destroy()
        }
        Thread {
            WebCacheUtils.clearAppCache(applicationContext)
            val size = WebCacheUtils.getCacheSize(applicationContext)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    cacheSizeValue.text = WebCacheUtils.formatSize(size)
                    cacheRow.isEnabled = true
                    Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun updatePermissionStatuses() {
        setPermissionStatus(notificationPermissionStatus, hasNotificationPermission())
        setPermissionStatus(exactAlarmPermissionStatus, SignInScheduler.canScheduleExactAlarms(this))
        setPermissionStatus(cameraPermissionStatus, hasRuntimePermission(Manifest.permission.CAMERA))
        setPermissionStatus(locationPermissionStatus, hasLocationPermission())
    }

    private fun setPermissionStatus(view: TextView, enabled: Boolean) {
        view.setText(
            if (enabled) R.string.permission_status_enabled else R.string.permission_status_disabled
        )
        view.setTextColor(
            ContextCompat.getColor(
                this,
                if (enabled) R.color.nav_selected else R.color.nav_unselected
            )
        )
    }

    private fun showPermissionChangeDialog(@StringRes messageRes: Int, onContinue: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_change_title)
            .setMessage(messageRes)
            .setPositiveButton(R.string.permission_continue_change) { _, _ -> onContinue() }
            .setNegativeButton(R.string.permission_cancel, null)
            .show()
    }

    private fun showAppInfoRedirectDialog(@StringRes messageRes: Int) {
        AlertDialog.Builder(this)
            .setMessage(messageRes)
            .setPositiveButton(R.string.permission_ok) { _, _ -> openAppSystemSettings() }
            .show()
    }

    private fun hasRuntimePermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocationPermission(): Boolean {
        return hasRuntimePermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasRuntimePermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } else {
            openAppSystemSettings()
        }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                )
            }.getOrElse {
                startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS))
            }
        } else {
            openAppSystemSettings()
        }
    }

    private fun showNotificationPermissionDialogIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (hasNotificationPermission()) return

        AlertDialog.Builder(this)
            .setTitle(R.string.notification_permission_title)
            .setMessage(R.string.notification_permission_message)
            .setPositiveButton(R.string.permission_confirm) { _, _ ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton(R.string.permission_cancel) { _, _ ->
                runTestAfterNotificationPermission = false
            }
            .setOnCancelListener {
                runTestAfterNotificationPermission = false
            }
            .show()
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun showExactAlarmPermissionDialogIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (SignInScheduler.canScheduleExactAlarms(this)) return

        AlertDialog.Builder(this)
            .setTitle(R.string.exact_alarm_permission_title)
            .setMessage(R.string.exact_alarm_permission_message)
            .setPositiveButton(R.string.exact_alarm_permission_go) { _, _ ->
                openExactAlarmSettings()
            }
            .setNegativeButton(R.string.permission_cancel, null)
            .show()
    }

    private fun openOfficialPath(path: String, allowOfficialSettings: Boolean = false) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_URL, WebConstants.siteUrl(path))
            .putExtra(MainActivity.EXTRA_ALLOW_OFFICIAL_SETTINGS, allowOfficialSettings)
            .putExtra(MainActivity.EXTRA_RETURN_TO_NATIVE_SETTINGS, true)
        val options = ActivityOptions.makeCustomAnimation(
            this,
            R.anim.settings_enter,
            R.anim.activity_hold
        )
        startActivity(intent, options.toBundle())
        finish()
    }

    private fun openChangelog() {
        val options = ActivityOptions.makeCustomAnimation(
            this,
            R.anim.settings_enter,
            R.anim.activity_hold
        )
        startActivity(Intent(this, ChangelogActivity::class.java), options.toBundle())
    }

    private fun openMobileDataUsage() {
        val options = ActivityOptions.makeCustomAnimation(
            this,
            R.anim.settings_enter,
            R.anim.activity_hold
        )
        startActivity(Intent(this, MobileDataUsageActivity::class.java), options.toBundle())
    }

    private fun openLikeEffectManager() {
        val options = ActivityOptions.makeCustomAnimation(
            this,
            R.anim.settings_enter,
            R.anim.activity_hold
        )
        startActivity(Intent(this, LikeEffectManagerActivity::class.java), options.toBundle())
    }

    @Suppress("DEPRECATION")
    private fun finishWithTransition() {
        finish()
        overridePendingTransition(R.anim.activity_hold, R.anim.settings_exit)
    }

    private companion object {
        private const val LIKE_EFFECT_PREVIEW_INTERVAL_MS = 1000L
        private const val LIKE_EFFECT_BASE_SCALE = 2.5f
        private const val LIKE_EFFECT_DETAIL_SIZE_RATIO = 0.0533333333f
        private const val LIKE_EFFECT_PREVIEW_SIZE_CALIBRATION = 0.75f
    }
}
