package com.profans.elmospace

import android.Manifest
import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.app.AlertDialog
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.res.ColorStateList
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
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
    private lateinit var settingsTitle: TextView
    private lateinit var settingsContent: LinearLayout
    private lateinit var settingsContentScroll: ScrollView
    private lateinit var settingsCategoryPage: LinearLayout
    private val settingsPages = mutableMapOf<SettingsPage, View>()
    private var currentSettingsPage = SettingsPage.CATEGORIES
    private var settingsPageTransitionRunning = false
    private lateinit var scheduledSwitch: Switch
    private lateinit var timeRow: LinearLayout
    private lateinit var timeValue: TextView
    private lateinit var autoExchangeRow: LinearLayout
    private lateinit var autoExchangeSwitch: Switch
    private lateinit var autoExchangeDivider: View
    private lateinit var autoExchangeResourceRow: LinearLayout
    private lateinit var autoExchangeResourceDivider: View
    private lateinit var autoExchangeReserveRow: LinearLayout
    private lateinit var autoExchangeReserveDivider: View
    private lateinit var autoExchangeReserveValue: TextView
    private lateinit var cacheSizeValue: TextView
    private lateinit var storageSpaceValue: TextView
    private lateinit var storageSpaceProgress: ProgressBar
    private lateinit var preloadScreensRow: LinearLayout
    private lateinit var preloadScreensValue: TextView
    private lateinit var darkModeValue: TextView
    private lateinit var accentColorRow: View
    private lateinit var accentColorSwatch: View
    private lateinit var accentColorName: TextView
    private lateinit var accentColorValue: TextView
    private lateinit var accentCustomContainer: LinearLayout
    private lateinit var accentColorWheel: AccentColorWheelView
    private lateinit var accentHexContainer: LinearLayout
    private lateinit var accentHexInput: EditText
    private lateinit var accentHexConfirm: TextView
    private lateinit var accentRgbContainer: LinearLayout
    private lateinit var accentRgbConfirm: TextView
    private lateinit var accentRedSeek: SeekBar
    private lateinit var accentGreenSeek: SeekBar
    private lateinit var accentBlueSeek: SeekBar
    private lateinit var accentRedInput: EditText
    private lateinit var accentGreenInput: EditText
    private lateinit var accentBlueInput: EditText
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
    private lateinit var startupUpdateCheckSwitch: Switch
    private lateinit var startupUpdateCheckFrequencyRow: LinearLayout
    private lateinit var startupUpdateCheckFrequencyValue: TextView
    private lateinit var updateDownloadModeValue: TextView
    private var runTestAfterNotificationPermission = false
    private var updatingAccentControls = false
    private var lastAppliedAccentColor: Int? = null
    private var browseDataProgressDialog: AlertDialog? = null
    private var browseDataProgressText: TextView? = null
    private var browseDataProgressBar: ProgressBar? = null
    private val consumedBrowseDataEventIds = mutableSetOf<Long>()
    private val browseDataTaskListener: (BrowsingHistoryDataTaskManager.State) -> Unit = {
        handleBrowseDataTaskState(it)
    }
    private val likeEffectPreviewHandler = Handler(Looper.getMainLooper())
    private var likeEffectPreviewRunning = false
    private val likeEffectPreviewRunnable = object : Runnable {
        override fun run() {
            if (!likeEffectPreviewRunning) return
            spawnLikeEffectPreview()
            likeEffectPreviewHandler.postDelayed(this, LIKE_EFFECT_PREVIEW_INTERVAL_MS)
        }
    }

    private data class SettingsCategoryDefinition(
        val rowId: Int,
        val page: SettingsPage
    )

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
        WindowLayout.lockPhonePortrait(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)
        applyInsets()

        settingsTitle = findViewById(R.id.settingsTitle)
        settingsContent = findViewById(R.id.settingsContent)
        settingsContentScroll = findViewById(R.id.settingsContentScroll)
        settingsCategoryPage = findViewById(R.id.settingsCategoryPage)
        applyTabletContentWidthWhenReady()
        applyTabletCategoryGridIfNeeded()
        initializeSettingsPages()

        scheduledSwitch = findViewById(R.id.scheduledSignSwitch)
        timeRow = findViewById(R.id.timeRow)
        timeValue = findViewById(R.id.timeValue)
        autoExchangeRow = findViewById(R.id.autoExchangeRow)
        autoExchangeSwitch = findViewById(R.id.autoExchangeSwitch)
        autoExchangeDivider = findViewById(R.id.autoExchangeDivider)
        autoExchangeResourceRow = findViewById(R.id.autoExchangeResourceRow)
        autoExchangeResourceDivider = findViewById(R.id.autoExchangeResourceDivider)
        autoExchangeReserveRow = findViewById(R.id.autoExchangeReserveRow)
        autoExchangeReserveDivider = findViewById(R.id.autoExchangeReserveDivider)
        autoExchangeReserveValue = findViewById(R.id.autoExchangeReserveValue)
        cacheSizeValue = findViewById(R.id.cacheSizeValue)
        storageSpaceValue = findViewById(R.id.storageSpaceValue)
        storageSpaceProgress = findViewById(R.id.storageSpaceProgress)
        preloadScreensRow = findViewById(R.id.preloadScreensRow)
        preloadScreensValue = findViewById(R.id.preloadScreensValue)
        darkModeValue = findViewById(R.id.darkModeValue)
        accentColorRow = findViewById(R.id.accentColorRow)
        accentColorSwatch = findViewById(R.id.accentColorSwatch)
        accentColorName = findViewById(R.id.accentColorName)
        accentColorValue = findViewById(R.id.accentColorValue)
        accentCustomContainer = findViewById(R.id.accentCustomContainer)
        accentColorWheel = findViewById(R.id.accentColorWheel)
        accentHexContainer = findViewById(R.id.accentHexContainer)
        accentHexInput = findViewById(R.id.accentHexInput)
        accentHexConfirm = findViewById(R.id.accentHexConfirm)
        accentRgbContainer = findViewById(R.id.accentRgbContainer)
        accentRgbConfirm = findViewById(R.id.accentRgbConfirm)
        accentRedSeek = findViewById(R.id.accentRedSeek)
        accentGreenSeek = findViewById(R.id.accentGreenSeek)
        accentBlueSeek = findViewById(R.id.accentBlueSeek)
        accentRedInput = findViewById(R.id.accentRedInput)
        accentGreenInput = findViewById(R.id.accentGreenInput)
        accentBlueInput = findViewById(R.id.accentBlueInput)
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
        startupUpdateCheckSwitch = findViewById(R.id.startupUpdateCheckSwitch)
        startupUpdateCheckFrequencyRow = findViewById(R.id.startupUpdateCheckFrequencyRow)
        startupUpdateCheckFrequencyValue = findViewById(R.id.startupUpdateCheckFrequencyValue)
        updateDownloadModeValue = findViewById(R.id.updateDownloadModeValue)

        findViewById<View>(R.id.settingsBack).setOnClickListener { handleSettingsBack() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleSettingsBack()
        })

        bindSettingsCategoryNavigation()
        findViewById<View>(R.id.privacyRow).setOnClickListener { openOfficialPath("/m/priSet") }
        findViewById<View>(R.id.feedbackRow).setOnClickListener { openOfficialPath("/m/feedback") }
        findViewById<View>(R.id.logoutRow).setOnClickListener {
            openOfficialPath("/m/set", allowOfficialSettings = true)
        }
        findViewById<View>(R.id.aboutGithubRepoRow).setOnClickListener {
            openExternalUrl(ELMOSPACE_GITHUB_REPO_URL)
        }
        findViewById<View>(R.id.aboutGithubReleasesRow).setOnClickListener {
            openExternalUrl(ELMOSPACE_GITHUB_RELEASES_URL)
        }
        findViewById<View>(R.id.aboutGithubIssuesRow).setOnClickListener {
            openExternalUrl(ELMOSPACE_GITHUB_ISSUES_URL)
        }
        findViewById<View>(R.id.aboutAuthorBilibiliRow).setOnClickListener {
            openExternalUrl(AUTHOR_BILIBILI_URL)
        }
        findViewById<View>(R.id.officialStoreRow).setOnClickListener {
            openOfficialStore()
        }
        findViewById<View>(R.id.officialGf2WebsiteRow).setOnClickListener {
            openExternalUrl(OFFICIAL_GF2_WEBSITE_URL)
        }
        findViewById<View>(R.id.officialSteamGfRow).setOnClickListener {
            openSteamGame(STEAM_GIRLS_FRONTLINE_APP_ID)
        }
        findViewById<View>(R.id.officialSteamGf2Row).setOnClickListener {
            openSteamGame(STEAM_GIRLS_FRONTLINE_2_APP_ID)
        }
        findViewById<View>(R.id.officialBilibiliGfRow).setOnClickListener {
            openBilibiliUser(OFFICIAL_BILIBILI_GF_UID)
        }
        findViewById<View>(R.id.officialBilibiliGf2Row).setOnClickListener {
            openBilibiliUser(OFFICIAL_BILIBILI_GF2_UID)
        }
        findViewById<View>(R.id.officialWeiboGfRow).setOnClickListener {
            openWeiboUser(OFFICIAL_WEIBO_GF_UID)
        }
        findViewById<View>(R.id.officialWeiboGf2Row).setOnClickListener {
            openWeiboUser(OFFICIAL_WEIBO_GF2_UID)
        }
        findViewById<View>(R.id.officialWechatGfRow).setOnClickListener {
            openWechatOfficialAccount(WECHAT_OFFICIAL_ACCOUNT_GF_URL)
        }
        findViewById<View>(R.id.officialWechatGf2Row).setOnClickListener {
            openWechatOfficialAccount(WECHAT_OFFICIAL_ACCOUNT_GF2_URL)
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
            updateAutoExchangeVisibility()
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
        autoExchangeSwitch.isChecked = AppPreferences.isAutoExchangeEnabled(this)
        autoExchangeSwitch.setOnCheckedChangeListener { _, checked ->
            AppPreferences.setAutoExchangeEnabled(this, checked)
            updateAutoExchangeVisibility()
        }
        autoExchangeRow.setOnClickListener {
            autoExchangeSwitch.isChecked = !autoExchangeSwitch.isChecked
        }
        autoExchangeResourceRow.setOnClickListener { openAutoExchangeResources() }
        autoExchangeReserveRow.setOnClickListener { showAutoExchangeReservePicker() }
        findViewById<View>(R.id.testScheduledSignRow).setOnClickListener {
            testScheduledSignIn()
        }

        val useSystemProxySwitch = findViewById<Switch>(R.id.useSystemProxySwitch)
        useSystemProxySwitch.isChecked = AppPreferences.isUseSystemProxyEnabled(this)
        useSystemProxySwitch.setOnCheckedChangeListener { _, checked ->
            AppPreferences.setUseSystemProxyEnabled(this, checked)
            AppNetworkProxy.applyWebViewProxyPolicy(this)
            if (!AppNetworkProxy.isWebViewProxyOverrideSupported()) {
                Toast.makeText(
                    this,
                    R.string.use_system_proxy_legacy_notice,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        findViewById<View>(R.id.useSystemProxyRow).setOnClickListener {
            useSystemProxySwitch.isChecked = !useSystemProxySwitch.isChecked
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
        bindAccentColorSettings()
        val splashAnimationSwitch = findViewById<Switch>(R.id.splashAnimationSwitch)
        splashAnimationSwitch.isChecked = AppPreferences.isSplashAnimationEnabled(this)
        splashAnimationSwitch.setOnCheckedChangeListener { _, checked ->
            AppPreferences.setSplashAnimationEnabled(this, checked)
        }
        findViewById<View>(R.id.splashAnimationRow).setOnClickListener {
            splashAnimationSwitch.isChecked = !splashAnimationSwitch.isChecked
        }
        val parallelBrowsingSwitch = findViewById<Switch>(R.id.parallelBrowsingSwitch)
        val parallelBrowsingRow = findViewById<View>(R.id.parallelBrowsingRow)
        val parallelBrowsingSummary = findViewById<View>(R.id.parallelBrowsingSummary)
        val supportsParallelBrowsing = WindowLayout.isTabletDevice(this)
        parallelBrowsingSwitch.isChecked = AppPreferences.isParallelBrowsingEnabled(this)
        parallelBrowsingSwitch.isEnabled = supportsParallelBrowsing
        parallelBrowsingSwitch.isClickable = supportsParallelBrowsing
        parallelBrowsingSwitch.setOnCheckedChangeListener { _, checked ->
            if (supportsParallelBrowsing) {
                AppPreferences.setParallelBrowsingEnabled(this, checked)
            }
        }
        parallelBrowsingRow.alpha = if (supportsParallelBrowsing) 1f else 0.45f
        parallelBrowsingSummary.alpha = if (supportsParallelBrowsing) 1f else 0.45f
        parallelBrowsingRow.setOnClickListener {
            if (supportsParallelBrowsing) {
                parallelBrowsingSwitch.isChecked = !parallelBrowsingSwitch.isChecked
            } else {
                showParallelBrowsingUnavailableDialog()
            }
        }
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
        startupUpdateCheckSwitch.isChecked = AppPreferences.isStartupUpdateCheckEnabled(this)
        startupUpdateCheckSwitch.setOnCheckedChangeListener { _, checked ->
            AppPreferences.setStartupUpdateCheckEnabled(this, checked)
            updateStartupUpdateCheckUi()
        }
        findViewById<View>(R.id.startupUpdateCheckRow).setOnClickListener {
            startupUpdateCheckSwitch.isChecked = !startupUpdateCheckSwitch.isChecked
        }
        startupUpdateCheckFrequencyRow.setOnClickListener {
            showStartupUpdateCheckFrequencyPicker()
        }
        findViewById<View>(R.id.updateDownloadModeRow).setOnClickListener {
            showUpdateDownloadModePicker()
        }
        findViewById<View>(R.id.checkUpdateRow).setOnClickListener {
            checkUpdateManually()
        }
        findViewById<View>(R.id.changelogRow).setOnClickListener { openChangelog() }

        findViewById<View>(R.id.clearCacheRow).setOnClickListener { clearWebCache() }
        findViewById<View>(R.id.exportBrowseHistoryRow).setOnClickListener {
            startExportBrowseHistory()
        }
        findViewById<View>(R.id.importBrowseHistoryRow).setOnClickListener {
            importBrowseHistoryLauncher.launch(
                arrayOf("application/zip", "application/octet-stream", "*/*")
            )
        }
        findViewById<View>(R.id.clearBrowseHistoryRow).setOnClickListener {
            confirmClearBrowseHistory()
        }
        updateDisplayedTime()
        updateTimeEnabledState()
        updateAutoExchangeReserveValue()
        updateAutoExchangeVisibility()
        updateDisplayedPreloadScreens()
        updatePreloadEnabledState(preloadSwitch.isChecked)
        updateDarkModeValue()
        updateAccentColorUi()
        updateLikeEffectValue()
        updateLikeEffectDurationValue()
        updateLikeEffectSizeValue()
        updateLikeEffectVisibility(enhancedLikeSwitch.isChecked)
        updateStartupUpdateCheckUi()
        updateUpdateDownloadModeValue()
        updatePermissionStatuses()
        updateCacheSize()
        updateStorageSpace()
        val restoredPage = savedInstanceState
            ?.getString(KEY_SETTINGS_PAGE)
            ?.let { runCatching { SettingsPage.valueOf(it) }.getOrNull() }
            ?: SettingsPage.CATEGORIES
        showSettingsPageImmediately(restoredPage)
    }

    override fun onStart() {
        super.onStart()
        BrowsingHistoryDataTaskManager.addListener(browseDataTaskListener)
    }

    override fun onStop() {
        BrowsingHistoryDataTaskManager.removeListener(browseDataTaskListener)
        browseDataProgressDialog?.dismiss()
        browseDataProgressDialog = null
        browseDataProgressText = null
        browseDataProgressBar = null
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_SETTINGS_PAGE, currentSettingsPage.name)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        AppUpdateUi.resumePendingInstallIfAllowed(this)
        if (AppPreferences.isScheduledSignInEnabled(this)) {
            SignInScheduler.scheduleNext(this)
        }
        updatePermissionStatuses()
        updateLikeEffectValue()
        if (::autoExchangeSwitch.isInitialized) {
            autoExchangeSwitch.isChecked = AppPreferences.isAutoExchangeEnabled(this)
            updateAutoExchangeReserveValue()
            updateAutoExchangeVisibility()
        }
    }

    private val exportBrowseHistoryLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { BrowsingHistoryDataTaskManager.startExport(this, it) }
    }

    private val importBrowseHistoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { BrowsingHistoryDataTaskManager.startPrepareImport(this, it) }
    }

    override fun onPause() {
        super.onPause()
        stopLikeEffectPreview()
        settingsContent.animate().cancel()
        settingsContent.translationX = 0f
        settingsContent.alpha = 1f
        settingsPageTransitionRunning = false
    }

    private fun applyInsets() {
        val root = findViewById<View>(R.id.settingsRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun applyTabletContentWidthWhenReady() {
        if (!WindowLayout.isTabletLayout(this)) return
        settingsContentScroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyTabletContentWidth()
        }
        settingsContentScroll.post { applyTabletContentWidth() }
    }

    private fun applyTabletContentWidth() {
        if (!WindowLayout.isTabletLayout(this)) return
        val maxWidth = resources.getDimensionPixelSize(R.dimen.settings_content_max_width)
        val availableWidth = settingsContentScroll.width -
            settingsContentScroll.paddingStart -
            settingsContentScroll.paddingEnd
        if (maxWidth <= 0 || availableWidth <= 0) return
        val targetWidth = minOf(maxWidth, availableWidth)
        val params = settingsContent.layoutParams as? FrameLayout.LayoutParams ?: return
        if (params.width == targetWidth && params.gravity == android.view.Gravity.CENTER_HORIZONTAL) {
            return
        }
        params.width = targetWidth
        params.gravity = android.view.Gravity.CENTER_HORIZONTAL
        settingsContent.layoutParams = params
    }

    private fun applyTabletCategoryGridIfNeeded() {
        if (!WindowLayout.isTabletLayout(this)) return
        val tiles = settingsCategoryDefinitions().map { findViewById<View>(it.rowId) }
        tiles.forEach { tile ->
            (tile.parent as? ViewGroup)?.removeView(tile)
        }
        settingsCategoryPage.removeAllViews()
        tiles.chunked(TABLET_SETTINGS_GRID_COLUMNS).forEach { rowTiles ->
            val row = LinearLayout(this).apply {
                setBaselineAligned(false)
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            rowTiles.forEach { tile ->
                val sourceParams = tile.layoutParams as? LinearLayout.LayoutParams
                tile.layoutParams = LinearLayout.LayoutParams(
                    0,
                    sourceParams?.height ?: LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    leftMargin = sourceParams?.leftMargin ?: 0
                    topMargin = sourceParams?.topMargin ?: 0
                    rightMargin = sourceParams?.rightMargin ?: 0
                    bottomMargin = sourceParams?.bottomMargin ?: 0
                }
                row.addView(tile)
            }
            settingsCategoryPage.addView(row)
        }
    }

    private fun initializeSettingsPages() {
        SettingsPage.values().forEach { page ->
            settingsPages[page] = findViewById(page.viewId)
        }
    }

    private fun bindSettingsCategoryNavigation() {
        settingsCategoryDefinitions().forEach { category ->
            findViewById<View>(category.rowId).setOnClickListener {
                showSettingsPage(category.page)
            }
        }
    }

    private fun settingsCategoryDefinitions() = listOf(
        SettingsCategoryDefinition(R.id.officialSettingsCategoryRow, SettingsPage.OFFICIAL),
        SettingsCategoryDefinition(R.id.securityCategoryRow, SettingsPage.SECURITY),
        SettingsCategoryDefinition(R.id.signCategoryRow, SettingsPage.SIGN),
        SettingsCategoryDefinition(R.id.displayCategoryRow, SettingsPage.DISPLAY),
        SettingsCategoryDefinition(R.id.interactionCategoryRow, SettingsPage.INTERACTION),
        SettingsCategoryDefinition(R.id.networkCategoryRow, SettingsPage.NETWORK),
        SettingsCategoryDefinition(R.id.permissionCategoryRow, SettingsPage.PERMISSION),
        SettingsCategoryDefinition(R.id.powerCategoryRow, SettingsPage.POWER),
        SettingsCategoryDefinition(R.id.storageCategoryRow, SettingsPage.STORAGE),
        SettingsCategoryDefinition(R.id.officialStoreCategoryRow, SettingsPage.OFFICIAL_LINKS),
        SettingsCategoryDefinition(R.id.versionCategoryRow, SettingsPage.VERSION),
        SettingsCategoryDefinition(R.id.aboutCategoryRow, SettingsPage.ABOUT)
    )

    private fun handleSettingsBack() {
        if (settingsPageTransitionRunning) return
        if (currentSettingsPage == SettingsPage.CATEGORIES) {
            finishWithTransition()
        } else {
            showSettingsPage(SettingsPage.CATEGORIES, forward = false)
        }
    }

    private fun showSettingsPage(page: SettingsPage, forward: Boolean = true) {
        if (settingsPageTransitionRunning || page == currentSettingsPage) return
        settingsPageTransitionRunning = true
        if (currentSettingsPage == SettingsPage.INTERACTION && page != SettingsPage.INTERACTION) {
            stopLikeEffectPreview()
        }

        val distance = (settingsContent.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels) * SETTINGS_PAGE_TRANSLATION_RATIO
        val outX = if (forward) -distance else distance
        val inX = if (forward) distance else -distance

        settingsContent.animate()
            .translationX(outX)
            .alpha(SETTINGS_PAGE_FADED_ALPHA)
            .setDuration(SETTINGS_PAGE_OUT_DURATION_MS)
            .setInterpolator(LinearInterpolator())
            .withEndAction {
                showSettingsPageImmediately(page, resetTransition = false)
                settingsContent.translationX = inX
                settingsContent.alpha = SETTINGS_PAGE_FADED_ALPHA
                settingsContent.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(SETTINGS_PAGE_IN_DURATION_MS)
                    .setInterpolator(LinearInterpolator())
                    .withEndAction {
                        settingsPageTransitionRunning = false
                    }
                    .start()
            }
            .start()
    }

    private fun showSettingsPageImmediately(page: SettingsPage, resetTransition: Boolean = true) {
        if (resetTransition) {
            settingsContent.animate().cancel()
            settingsPageTransitionRunning = false
        }
        currentSettingsPage = page
        settingsTitle.setText(page.titleRes)
        settingsPages.forEach { (settingsPage, view) ->
            view.visibility = if (settingsPage == page) View.VISIBLE else View.GONE
            view.translationX = 0f
            view.alpha = 1f
        }
        settingsContent.translationX = 0f
        settingsContent.alpha = 1f
        settingsContentScroll.post { settingsContentScroll.scrollTo(0, 0) }
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

    private fun updateAutoExchangeVisibility() {
        val scheduledEnabled = scheduledSwitch.isChecked
        val exchangeEnabled = scheduledEnabled && autoExchangeSwitch.isChecked
        autoExchangeDivider.visibility = if (scheduledEnabled) View.VISIBLE else View.GONE
        autoExchangeRow.visibility = if (scheduledEnabled) View.VISIBLE else View.GONE
        autoExchangeResourceDivider.visibility = if (exchangeEnabled) View.VISIBLE else View.GONE
        autoExchangeResourceRow.visibility = if (exchangeEnabled) View.VISIBLE else View.GONE
        autoExchangeReserveDivider.visibility = if (exchangeEnabled) View.VISIBLE else View.GONE
        autoExchangeReserveRow.visibility = if (exchangeEnabled) View.VISIBLE else View.GONE
    }

    private fun showAutoExchangeReservePicker() {
        val values = (0..20).map { it * 10 }
        val labels = values.map {
            getString(R.string.auto_exchange_reserve_score_value, it)
        }.toTypedArray()
        val selectedIndex = values.indexOf(AppPreferences.getAutoExchangeReserveScore(this))
            .takeIf { it >= 0 } ?: 0
        showModernNumberPicker(
            titleRes = R.string.auto_exchange_reserve_score,
            minValue = 0,
            maxValue = labels.lastIndex,
            selectedValue = selectedIndex,
            displayedValues = labels
        ) { index ->
            AppPreferences.setAutoExchangeReserveScore(this, values[index])
            updateAutoExchangeReserveValue()
        }
    }

    private fun updateAutoExchangeReserveValue() {
        autoExchangeReserveValue.text = getString(
            R.string.auto_exchange_reserve_score_value,
            AppPreferences.getAutoExchangeReserveScore(this)
        )
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

    private fun showStartupUpdateCheckFrequencyPicker() {
        if (!startupUpdateCheckFrequencyRow.isEnabled) return
        val labels = Array(3) { index ->
            getString(R.string.startup_update_check_frequency_value, index + 1)
        }
        showModernNumberPicker(
            titleRes = R.string.startup_update_check_frequency,
            minValue = 1,
            maxValue = 3,
            selectedValue = AppPreferences.getStartupUpdateChecksPerDay(this),
            displayedValues = labels
        ) { value ->
            AppPreferences.setStartupUpdateChecksPerDay(this, value)
            updateStartupUpdateCheckUi()
        }
    }

    private fun updateStartupUpdateCheckUi() {
        startupUpdateCheckFrequencyValue.text = getString(
            R.string.startup_update_check_frequency_value,
            AppPreferences.getStartupUpdateChecksPerDay(this)
        )
        val enabled = startupUpdateCheckSwitch.isChecked
        startupUpdateCheckFrequencyRow.isEnabled = enabled
        startupUpdateCheckFrequencyRow.alpha = if (enabled) 1f else 0.45f
    }

    private fun showUpdateDownloadModePicker() {
        val modes = intArrayOf(
            AppPreferences.UPDATE_DOWNLOAD_MODE_BUILT_IN,
            AppPreferences.UPDATE_DOWNLOAD_MODE_BROWSER
        )
        val labels = arrayOf(
            getString(R.string.update_download_mode_builtin),
            getString(R.string.update_download_mode_browser)
        )
        val selectedMode = AppPreferences.getUpdateDownloadMode(this)
        val selectedIndex = modes.indexOf(selectedMode).coerceAtLeast(0)
        showModernOptionPicker(
            titleRes = R.string.update_download_mode,
            labels = labels,
            selectedIndex = selectedIndex
        ) { index ->
            AppPreferences.setUpdateDownloadMode(this, modes[index])
            updateUpdateDownloadModeValue()
        }
    }

    private fun updateUpdateDownloadModeValue() {
        updateDownloadModeValue.setText(
            when (AppPreferences.getUpdateDownloadMode(this)) {
                AppPreferences.UPDATE_DOWNLOAD_MODE_BROWSER ->
                    R.string.update_download_mode_browser_short
                else -> R.string.update_download_mode_builtin_short
            }
        )
        updateDownloadModeValue.setTextColor(AppAccentColor.color(this))
    }

    private fun checkUpdateManually() {
        AppUpdateUi.checkForUpdate(this, manual = true)
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

    private fun bindAccentColorSettings() {
        accentColorRow.setOnClickListener { showAccentColorPicker() }
        accentColorWheel.onColorChanged = { color ->
            if (AppPreferences.getAccentColorMode(this) == AppPreferences.ACCENT_COLOR_WHEEL) {
                AppPreferences.setCustomAccentColor(this, AppPreferences.ACCENT_COLOR_WHEEL, color)
                updateAccentColorUi(syncEditorControls = false)
            }
        }
        accentHexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (updatingAccentControls) return
                if (AppPreferences.getAccentColorMode(this@SettingsActivity) != AppPreferences.ACCENT_COLOR_HEX) return
                AppAccentColor.parseHex(s?.toString().orEmpty())?.let { color ->
                    AppPreferences.setCustomAccentColor(this@SettingsActivity, AppPreferences.ACCENT_COLOR_HEX, color)
                    updateAccentColorUi(syncEditorControls = false)
                }
            }
        })
        accentHexInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateHexAccentOrReset()
        }
        accentHexConfirm.setOnClickListener { validateHexAccentOrReset() }
        bindRgbSeekBar(accentRedSeek, accentRedInput)
        bindRgbSeekBar(accentGreenSeek, accentGreenInput)
        bindRgbSeekBar(accentBlueSeek, accentBlueInput)
        bindRgbInput(accentRedInput, accentRedSeek)
        bindRgbInput(accentGreenInput, accentGreenSeek)
        bindRgbInput(accentBlueInput, accentBlueSeek)
        accentRgbConfirm.setOnClickListener { validateRgbAccentOrReset() }
    }

    private fun showAccentColorPicker() {
        val options = listOf(
            AppPreferences.ACCENT_COLOR_DEFAULT to getString(R.string.accent_color_default),
            AppPreferences.ACCENT_COLOR_WHEEL to getString(R.string.accent_color_wheel),
            AppPreferences.ACCENT_COLOR_HEX to getString(R.string.accent_color_hex),
            AppPreferences.ACCENT_COLOR_RGB to getString(R.string.accent_color_rgb)
        )
        val checkedMode = AppPreferences.getAccentColorMode(this)
        val accent = AppAccentColor.color(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp(), 0, 8.dp())
        }
        options.forEach { (mode, label) ->
            val row = LinearLayout(this).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                setPadding(24.dp(), 12.dp(), 24.dp(), 12.dp())
                isClickable = true
                isFocusable = true
            }
            val dot = View(this).apply {
                background = AppAccentColor.circleDrawable(
                    if (mode == checkedMode) accent else Color.TRANSPARENT,
                    accent
                )
            }
            row.addView(dot, LinearLayout.LayoutParams(18.dp(), 18.dp()).apply {
                marginEnd = 14.dp()
            })
            row.addView(TextView(this).apply {
                text = label
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.error_text))
                textSize = 15f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.setOnClickListener {
                if (mode == AppPreferences.ACCENT_COLOR_DEFAULT) {
                    AppPreferences.setDefaultAccentColor(this)
                } else {
                    AppPreferences.setCustomAccentColor(this, mode, AppAccentColor.color(this))
                }
                accentPickerDialog?.dismiss()
                updateAccentColorUi()
            }
            container.addView(row)
        }
        accentPickerDialog = AlertDialog.Builder(this)
            .setTitle(R.string.accent_color_title)
            .setView(container)
            .setNegativeButton(R.string.permission_cancel, null)
            .show()
            .also { tintDialogButtons(it) }
    }

    private var accentPickerDialog: AlertDialog? = null

    private fun updateAccentColorUi(syncEditorControls: Boolean = true) {
        val color = AppAccentColor.color(this)
        val mode = AppPreferences.getAccentColorMode(this)
        accentColorSwatch.background = AppAccentColor.circleDrawable(
            color,
            ContextCompat.getColor(this, R.color.nav_divider)
        )
        accentColorName.text = AppAccentColor.displayName(this)
        accentColorValue.text = "${AppAccentColor.hex(color)}  ${AppAccentColor.rgbText(color)}"
        accentCustomContainer.visibility =
            if (mode == AppPreferences.ACCENT_COLOR_DEFAULT) View.GONE else View.VISIBLE
        accentColorWheel.visibility =
            if (mode == AppPreferences.ACCENT_COLOR_WHEEL) View.VISIBLE else View.GONE
        accentHexContainer.visibility =
            if (mode == AppPreferences.ACCENT_COLOR_HEX) View.VISIBLE else View.GONE
        accentRgbContainer.visibility =
            if (mode == AppPreferences.ACCENT_COLOR_RGB) View.VISIBLE else View.GONE

        if (syncEditorControls) {
            updatingAccentControls = true
            accentColorWheel.setColor(color)
            accentHexInput.setText(AppAccentColor.hex(color))
            setRgbControls(color)
            updatingAccentControls = false
        }
        applyDynamicAccentToSettingsUi()
    }

    private fun bindRgbSeekBar(seekBar: SeekBar, input: EditText) {
        seekBar.max = 255
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || updatingAccentControls) return
                if (AppPreferences.getAccentColorMode(this@SettingsActivity) != AppPreferences.ACCENT_COLOR_RGB) return
                updatingAccentControls = true
                input.setText(progress.toString())
                input.setSelection(input.text.length)
                updatingAccentControls = false
                applyRgbAccentFromControls()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun bindRgbInput(input: EditText, seekBar: SeekBar) {
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (updatingAccentControls) return
                if (AppPreferences.getAccentColorMode(this@SettingsActivity) != AppPreferences.ACCENT_COLOR_RGB) return
                val value = s?.toString()?.toIntOrNull() ?: return
                if (value !in 0..255) return
                updatingAccentControls = true
                seekBar.progress = value
                updatingAccentControls = false
                applyRgbAccentFromControls()
            }
        })
        input.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateRgbAccentOrReset()
        }
    }

    private fun validateHexAccentOrReset() {
        if (AppPreferences.getAccentColorMode(this) != AppPreferences.ACCENT_COLOR_HEX) return
        val color = AppAccentColor.parseHex(accentHexInput.text?.toString().orEmpty())
        if (color == null) {
            resetAccentColorAfterFormatError()
        } else {
            AppPreferences.setCustomAccentColor(this, AppPreferences.ACCENT_COLOR_HEX, color)
            updateAccentColorUi()
        }
    }

    private fun validateRgbAccentOrReset() {
        if (AppPreferences.getAccentColorMode(this) != AppPreferences.ACCENT_COLOR_RGB) return
        val color = currentRgbColorOrNull()
        if (color == null) {
            resetAccentColorAfterFormatError()
        } else {
            AppPreferences.setCustomAccentColor(this, AppPreferences.ACCENT_COLOR_RGB, color)
            updateAccentColorUi()
        }
    }

    private fun applyRgbAccentFromControls() {
        if (AppPreferences.getAccentColorMode(this) != AppPreferences.ACCENT_COLOR_RGB) return
        currentRgbColorOrNull()?.let { color ->
            AppPreferences.setCustomAccentColor(this, AppPreferences.ACCENT_COLOR_RGB, color)
            updateAccentColorUi(syncEditorControls = false)
        }
    }

    private fun currentRgbColorOrNull(): Int? {
        val red = accentRedInput.text?.toString()?.toIntOrNull()
        val green = accentGreenInput.text?.toString()?.toIntOrNull()
        val blue = accentBlueInput.text?.toString()?.toIntOrNull()
        if (red == null || green == null || blue == null) return null
        if (red !in 0..255 || green !in 0..255 || blue !in 0..255) return null
        return Color.rgb(red, green, blue)
    }

    private fun setRgbControls(color: Int) {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        accentRedSeek.progress = red
        accentGreenSeek.progress = green
        accentBlueSeek.progress = blue
        accentRedInput.setText(red.toString())
        accentGreenInput.setText(green.toString())
        accentBlueInput.setText(blue.toString())
    }

    private fun resetAccentColorAfterFormatError() {
        AppPreferences.setDefaultAccentColor(this)
        updateAccentColorUi()
        AlertDialog.Builder(this)
            .setTitle(R.string.accent_color_format_error_title)
            .setMessage(R.string.accent_color_format_error_message)
            .setPositiveButton(R.string.permission_confirm, null)
            .show()
            .also { tintDialogButtons(it) }
    }

    private fun tintDialogButtons(dialog: AlertDialog) {
        val accent = AppAccentColor.color(this)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(accent)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(accent)
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(accent)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(accent)
    }

    private fun applyDynamicAccentToSettingsUi() {
        val accent = AppAccentColor.color(this)
        val defaultAccent = ContextCompat.getColor(this, R.color.nav_selected)
        val previousAccent = lastAppliedAccentColor
        applyDynamicAccentRecursively(window.decorView, accent, defaultAccent, previousAccent)
        lastAppliedAccentColor = accent
    }

    private fun applyDynamicAccentRecursively(
        view: View,
        accent: Int,
        defaultAccent: Int,
        previousAccent: Int?
    ) {
        when (view) {
            is Switch -> AppAccentColor.tintSwitch(view, this)
            is SeekBar -> {
                val list = ColorStateList.valueOf(accent)
                view.progressTintList = list
                view.thumbTintList = list
            }
            is ProgressBar -> AppAccentColor.tintProgress(view, this)
            is ImageView -> {
                val current = view.imageTintList?.defaultColor
                if (current != null && (current == defaultAccent || (previousAccent != null && current == previousAccent))) {
                    view.imageTintList = ColorStateList.valueOf(accent)
                }
            }
            is TextView -> {
                val current = view.textColors.defaultColor
                if (current == defaultAccent || current == previousAccent) {
                    view.setTextColor(accent)
                }
            }
        }
        applyDynamicAccentOutlineIfNeeded(view, accent, defaultAccent, previousAccent)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyDynamicAccentRecursively(view.getChildAt(index), accent, defaultAccent, previousAccent)
            }
        }
    }

    private fun applyDynamicAccentOutlineIfNeeded(
        view: View,
        accent: Int,
        defaultAccent: Int,
        previousAccent: Int?
    ) {
        if (view.getTag(R.id.accentOutlinedButtonTag) == true) {
            AppAccentColor.tintOutlinedButton(view, this)
        }
    }

    private fun Int.dp() = (this * resources.displayMetrics.density + 0.5f).toInt()

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

    private fun showParallelBrowsingUnavailableDialog() {
        AlertDialog.Builder(this)
            .setMessage(R.string.parallel_browsing_unavailable_message)
            .setPositiveButton(R.string.permission_confirm, null)
            .show()
            .also { tintDialogButtons(it) }
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

    private fun showModernOptionPicker(
        @StringRes titleRes: Int,
        labels: Array<String>,
        selectedIndex: Int,
        onConfirm: (Int) -> Unit
    ) {
        showModernNumberPicker(
            titleRes = titleRes,
            minValue = 0,
            maxValue = labels.lastIndex,
            selectedValue = selectedIndex.coerceIn(0, labels.lastIndex),
            displayedValues = labels,
            onConfirm = onConfirm
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
            BrowsingHistoryDataTaskManager.cleanupOldImportCaches(applicationContext)
            val size = WebCacheUtils.getCacheSize(applicationContext)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    cacheSizeValue.text = WebCacheUtils.formatSize(size)
                }
            }
        }.start()
    }

    private fun updateStorageSpace() {
        storageSpaceValue.text = getString(R.string.storage_space_calculating)
        storageSpaceProgress.progress = 0
        Thread {
            val storageSpace = runCatching { DeviceStorageUtils.internalStorageSpace() }.getOrNull()
            runOnUiThread {
                if (!isFinishing && !isDestroyed && storageSpace != null) {
                    storageSpaceProgress.progress = storageSpace.usedPercent
                    storageSpaceValue.text = getString(
                        R.string.storage_space_available_format,
                        WebCacheUtils.formatSize(storageSpace.availableBytes),
                        WebCacheUtils.formatSize(storageSpace.totalBytes)
                    )
                }
            }
        }.start()
    }

    private fun startExportBrowseHistory() {
        val startMillis = System.currentTimeMillis()
        exportBrowseHistoryLauncher.launch(
            BrowsingHistoryDataTaskManager.defaultExportFileName(startMillis)
        )
    }

    private fun confirmClearBrowseHistory() {
        AlertDialog.Builder(this)
            .setMessage(R.string.browse_history_clear_confirm_message)
            .setPositiveButton(R.string.permission_confirm) { _, _ ->
                BrowsingHistoryDataTaskManager.clearHistory(this)
            }
            .setNegativeButton(R.string.permission_cancel, null)
            .show()
            .also { tintDialogButtons(it) }
    }

    private fun handleBrowseDataTaskState(state: BrowsingHistoryDataTaskManager.State) {
        when (state) {
            BrowsingHistoryDataTaskManager.State.Idle -> {
                setBrowseDataActionsEnabled(true)
                hideBrowseDataProgressDialog()
            }
            is BrowsingHistoryDataTaskManager.State.Progress -> {
                setBrowseDataActionsEnabled(false)
                showBrowseDataProgressDialog(state)
            }
            is BrowsingHistoryDataTaskManager.State.ExportSuccess -> {
                if (!consumeBrowseDataEventOnce(state.eventId)) return
                setBrowseDataActionsEnabled(true)
                hideBrowseDataProgressDialog()
                showExportSuccessDialog(state.uri)
                updateCacheSize()
                updateStorageSpace()
                BrowsingHistoryDataTaskManager.consumeOneShotState(state.eventId)
            }
            is BrowsingHistoryDataTaskManager.State.ImportPreview -> {
                setBrowseDataActionsEnabled(true)
                hideBrowseDataProgressDialog()
                showImportPreviewDialog(state.stats)
            }
            is BrowsingHistoryDataTaskManager.State.ImportSuccess -> {
                if (!consumeBrowseDataEventOnce(state.eventId)) return
                setBrowseDataActionsEnabled(true)
                hideBrowseDataProgressDialog()
                showImportSuccessDialog(state.result)
                updateCacheSize()
                updateStorageSpace()
                BrowsingHistoryDataTaskManager.consumeOneShotState(state.eventId)
            }
            is BrowsingHistoryDataTaskManager.State.ClearSuccess -> {
                if (!consumeBrowseDataEventOnce(state.eventId)) return
                setBrowseDataActionsEnabled(true)
                hideBrowseDataProgressDialog()
                updateCacheSize()
                Toast.makeText(this, R.string.browse_history_cleared, Toast.LENGTH_SHORT).show()
                BrowsingHistoryDataTaskManager.consumeOneShotState(state.eventId)
            }
            is BrowsingHistoryDataTaskManager.State.Error -> {
                if (!consumeBrowseDataEventOnce(state.eventId)) return
                setBrowseDataActionsEnabled(true)
                hideBrowseDataProgressDialog()
                AlertDialog.Builder(this)
                    .setMessage(state.message)
                    .setPositiveButton(R.string.permission_confirm, null)
                    .show()
                    .also { tintDialogButtons(it) }
                BrowsingHistoryDataTaskManager.consumeOneShotState(state.eventId)
            }
        }
    }

    private fun consumeBrowseDataEventOnce(eventId: Long): Boolean {
        if (consumedBrowseDataEventIds.contains(eventId)) return false
        consumedBrowseDataEventIds += eventId
        if (consumedBrowseDataEventIds.size > 32) {
            consumedBrowseDataEventIds.remove(consumedBrowseDataEventIds.first())
        }
        return true
    }

    private fun showBrowseDataProgressDialog(
        state: BrowsingHistoryDataTaskManager.State.Progress
    ) {
        val dialog = browseDataProgressDialog ?: createBrowseDataProgressDialog().also {
            browseDataProgressDialog = it
            it.show()
        }
        dialog.setTitle(state.title)
        browseDataProgressText?.text = state.message
        browseDataProgressBar?.apply {
            isIndeterminate = state.indeterminate
            progress = state.percent
        }
    }

    private fun createBrowseDataProgressDialog(): AlertDialog {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 12.dp(), 24.dp(), 8.dp())
        }
        val message = TextView(this).apply {
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.error_text))
            textSize = 14f
        }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
            AppAccentColor.tintProgress(this, this@SettingsActivity)
        }
        content.addView(message)
        content.addView(progress, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 14.dp() })
        browseDataProgressText = message
        browseDataProgressBar = progress
        return AlertDialog.Builder(this)
            .setView(content)
            .create()
            .apply {
                setCanceledOnTouchOutside(false)
                setOnKeyListener { _, keyCode, _ ->
                    keyCode == android.view.KeyEvent.KEYCODE_BACK
                }
            }
    }

    private fun hideBrowseDataProgressDialog() {
        browseDataProgressDialog?.dismiss()
        browseDataProgressDialog = null
        browseDataProgressText = null
        browseDataProgressBar = null
    }

    private fun showExportSuccessDialog(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.browse_history_export_done_title)
            .setPositiveButton(R.string.open_export_directory) { _, _ ->
                openExportDirectory(uri)
            }
            .setNegativeButton(R.string.permission_confirm, null)
            .show()
            .also { tintDialogButtons(it) }
    }

    private fun openExportDirectory(uri: Uri) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(
                    this,
                    R.string.cannot_open_export_directory,
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showImportPreviewDialog(stats: BrowsingHistoryDataTaskManager.ImportPreviewStats) {
        val message = getString(
            R.string.browse_history_import_preview,
            stats.backupRecords,
            stats.localRecords,
            stats.newRecords,
            stats.duplicates,
            stats.differences
        )
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 8.dp(), 24.dp(), 8.dp())
        }
        content.addView(TextView(this).apply {
            text = message
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.error_text))
            textSize = 14f
            setLineSpacing(2.dp().toFloat(), 1f)
        })
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.browse_history_import_preview_title)
            .setView(content)
            .create()
        fun addAction(label: String, action: () -> Unit) {
            content.addView(TextView(this).apply {
                text = label
                setTextColor(AppAccentColor.color(this@SettingsActivity))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 14.dp(), 0, 14.dp())
                setOnClickListener {
                    dialog.dismiss()
                    action()
                }
            })
        }
        addAction(getString(R.string.browse_history_import_replace)) { confirmReplaceImport() }
        addAction(getString(R.string.browse_history_import_add)) {
            BrowsingHistoryDataTaskManager.applyPreparedImport(
                this,
                BrowsingHistoryDataTaskManager.ImportMode.ADD
            )
        }
        addAction(getString(R.string.browse_history_import_merge)) {
            BrowsingHistoryDataTaskManager.applyPreparedImport(
                this,
                BrowsingHistoryDataTaskManager.ImportMode.MERGE
            )
        }
        addAction(getString(R.string.permission_cancel)) {
            BrowsingHistoryDataTaskManager.discardPreparedImport()
        }
        dialog.setOnCancelListener { BrowsingHistoryDataTaskManager.discardPreparedImport() }
        dialog.show()
    }

    private fun confirmReplaceImport() {
        AlertDialog.Builder(this)
            .setTitle(R.string.browse_history_replace_confirm_title)
            .setMessage(R.string.browse_history_replace_confirm_message)
            .setPositiveButton(R.string.permission_confirm) { _, _ ->
                BrowsingHistoryDataTaskManager.applyPreparedImport(
                    this,
                    BrowsingHistoryDataTaskManager.ImportMode.REPLACE
                )
            }
            .setNegativeButton(R.string.permission_cancel) { _, _ ->
                BrowsingHistoryDataTaskManager.discardPreparedImport()
            }
            .show()
            .also {
                it.setOnCancelListener { BrowsingHistoryDataTaskManager.discardPreparedImport() }
                tintDialogButtons(it)
            }
    }

    private fun showImportSuccessDialog(result: BrowsingHistoryRepository.ImportApplyResult) {
        AlertDialog.Builder(this)
            .setTitle(R.string.browse_history_import_done_title)
            .setMessage(
                getString(
                    R.string.browse_history_import_done_message,
                    result.added,
                    result.updated,
                    result.duplicateSkipped,
                    result.diffSkipped,
                    result.failed
                )
            )
            .setPositiveButton(R.string.permission_confirm, null)
            .show()
            .also { tintDialogButtons(it) }
    }

    private fun setBrowseDataActionsEnabled(enabled: Boolean) {
        listOf(
            R.id.exportBrowseHistoryRow,
            R.id.importBrowseHistoryRow,
            R.id.clearBrowseHistoryRow,
            R.id.clearCacheRow
        ).forEach { id ->
            findViewById<View>(id).apply {
                isEnabled = enabled
                alpha = if (enabled) 1f else 0.45f
            }
        }
    }

    private fun clearWebCache() {
        if (BrowsingHistoryDataTaskManager.isTaskRunning()) {
            Toast.makeText(this, R.string.browse_history_task_running, Toast.LENGTH_SHORT).show()
            return
        }
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
                    updateStorageSpace()
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
            if (enabled) AppAccentColor.color(this)
            else ContextCompat.getColor(this, R.color.nav_unselected)
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

    private fun openAutoExchangeResources() {
        val options = ActivityOptions.makeCustomAnimation(
            this,
            R.anim.settings_enter,
            R.anim.activity_hold
        )
        startActivity(Intent(this, AutoExchangeActivity::class.java), options.toBundle())
    }

    private fun openExternalUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, R.string.cannot_open_link, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openOfficialStore() {
        if (tryOpenPackage(OFFICIAL_STORE_TAOBAO_DEEP_LINK, TAOBAO_PACKAGE)) return
        if (tryOpenPackage(OFFICIAL_STORE_URL, TMALL_PACKAGE)) return
        openExternalUrl(OFFICIAL_STORE_URL)
    }

    private fun openSteamGame(appId: Long) {
        val deepLink = "steam://store/$appId"
        val webUrl = "https://store.steampowered.com/app/$appId/"
        if (tryOpenPackage(deepLink, STEAM_PACKAGE)) return
        if (tryOpenPackage(webUrl, STEAM_PACKAGE)) return
        openExternalUrl(webUrl)
    }

    private fun openBilibiliUser(uid: Long) {
        val deepLink = "bilibili://space/$uid"
        val webUrl = "https://space.bilibili.com/$uid"
        if (tryOpenPackage(deepLink, BILIBILI_PACKAGE)) return
        if (tryOpenPackage(webUrl, BILIBILI_PACKAGE)) return
        openExternalUrl(webUrl)
    }

    private fun openWeiboUser(uid: Long) {
        val deepLink = "sinaweibo://userinfo?uid=$uid"
        val webUrl = "https://weibo.com/u/$uid"
        if (tryOpenPackage(deepLink, WEIBO_PACKAGE)) return
        if (tryOpenPackage(webUrl, WEIBO_PACKAGE)) return
        openExternalUrl(webUrl)
    }

    private fun openWechatOfficialAccount(accountUrl: String) {
        if (tryOpenPackage(accountUrl, WECHAT_PACKAGE)) return
        if (tryOpenPackage(WECHAT_OFFICIAL_ACCOUNTS_DEEP_LINK, WECHAT_PACKAGE)) return
        openExternalUrl(accountUrl)
    }

    private fun tryOpenPackage(url: String, packageName: String): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(packageName))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun finishWithTransition() {
        finish()
        overridePendingTransition(R.anim.activity_hold, R.anim.settings_exit)
    }

    private enum class SettingsPage(@StringRes val titleRes: Int, val viewId: Int) {
        CATEGORIES(R.string.settings_title, R.id.settingsCategoryPage),
        OFFICIAL_LINKS(R.string.settings_official_links, R.id.officialLinksPage),
        OFFICIAL(R.string.settings_official_section, R.id.officialSettingsPage),
        SECURITY(R.string.settings_security_section, R.id.securitySettingsPage),
        SIGN(R.string.settings_app_section, R.id.signSettingsPage),
        DISPLAY(R.string.settings_display_section, R.id.displaySettingsPage),
        INTERACTION(R.string.settings_interaction_section, R.id.interactionSettingsPage),
        NETWORK(R.string.settings_preload_section, R.id.networkSettingsPage),
        PERMISSION(R.string.settings_permission_section, R.id.permissionSettingsPage),
        POWER(R.string.settings_power_section, R.id.powerSettingsPage),
        STORAGE(R.string.settings_storage_section, R.id.storageSettingsPage),
        ABOUT(R.string.about_title, R.id.aboutSettingsPage),
        VERSION(R.string.version_history_section, R.id.versionSettingsPage)
    }

    private companion object {
        private const val KEY_SETTINGS_PAGE = "settings_page"
        private const val SETTINGS_PAGE_TRANSLATION_RATIO = 0.04f
        private const val SETTINGS_PAGE_FADED_ALPHA = 0.72f
        private const val SETTINGS_PAGE_OUT_DURATION_MS = 90L
        private const val SETTINGS_PAGE_IN_DURATION_MS = 150L
        private const val TABLET_SETTINGS_GRID_COLUMNS = 4
        private const val LIKE_EFFECT_PREVIEW_INTERVAL_MS = 1000L
        private const val LIKE_EFFECT_BASE_SCALE = 2.5f
        private const val LIKE_EFFECT_DETAIL_SIZE_RATIO = 0.0533333333f
        private const val LIKE_EFFECT_PREVIEW_SIZE_CALIBRATION = 0.75f
        private const val ELMOSPACE_GITHUB_REPO_URL = "https://github.com/FaKeOcEaNcAt/ELMOSpace"
        private const val ELMOSPACE_GITHUB_RELEASES_URL = "https://github.com/FaKeOcEaNcAt/ELMOSpace/releases"
        private const val ELMOSPACE_GITHUB_ISSUES_URL = "https://github.com/FaKeOcEaNcAt/ELMOSpace/issues"
        private const val AUTHOR_BILIBILI_URL = "https://space.bilibili.com/323603999"
        private const val OFFICIAL_STORE_URL = "https://girlsfrontline.tmall.com/"
        private const val OFFICIAL_STORE_TAOBAO_DEEP_LINK =
            "tbopen://m.taobao.com/tbopen/index.html?action=ali.open.nav&module=h5&h5Url=https%3A%2F%2Fgirlsfrontline.tmall.com%2F"
        private const val TAOBAO_PACKAGE = "com.taobao.taobao"
        private const val TMALL_PACKAGE = "com.tmall.wireless"
        private const val OFFICIAL_GF2_WEBSITE_URL = "https://gf2.sunborngame.com/main"
        private const val STEAM_PACKAGE = "com.valvesoftware.android.steam.community"
        private const val STEAM_GIRLS_FRONTLINE_APP_ID = 3347970L
        private const val STEAM_GIRLS_FRONTLINE_2_APP_ID = 3308670L
        private const val BILIBILI_PACKAGE = "tv.danmaku.bili"
        private const val OFFICIAL_BILIBILI_GF_UID = 32472953L
        private const val OFFICIAL_BILIBILI_GF2_UID = 697654195L
        private const val WEIBO_PACKAGE = "com.sina.weibo"
        private const val OFFICIAL_WEIBO_GF_UID = 5611537367L
        private const val OFFICIAL_WEIBO_GF2_UID = 7367502517L
        private const val WECHAT_PACKAGE = "com.tencent.mm"
        private const val WECHAT_OFFICIAL_ACCOUNT_GF_URL =
            "https://mp.weixin.qq.com/mp/profile_ext?action=home&__biz=MzI4NjI3OTIyOA%3D%3D&scene=124#wechat_redirect"
        private const val WECHAT_OFFICIAL_ACCOUNT_GF2_URL =
            "https://mp.weixin.qq.com/mp/profile_ext?action=home&__biz=Mzg4NTM0NTI2MA%3D%3D&scene=124#wechat_redirect"
        private const val WECHAT_OFFICIAL_ACCOUNTS_DEEP_LINK =
            "weixin://dl/officialaccounts"
    }
}
