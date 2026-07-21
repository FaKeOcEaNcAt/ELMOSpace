package com.profans.elmospace

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONObject
import com.profans.elmospace.WebConstants.HOME_URL
import com.profans.elmospace.WebConstants.HOME_URL_PREFIX
import com.profans.elmospace.WebConstants.JS_BRIDGE_NAME
import com.profans.elmospace.WebConstants.RELEASE_URL
import com.profans.elmospace.WebConstants.TARGET_HOST
import com.profans.elmospace.WebRouteRules.isInternalAppUri
import com.profans.elmospace.WebRouteRules.isInternalMobileUri
import com.profans.elmospace.WebRouteRules.isRootUrl
import com.profans.elmospace.WebRouteRules.isThreadInfoUri
import com.profans.elmospace.WebRouteRules.normalizeInternalNavigationUri

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var bottomNavigation: LinearLayout
    private lateinit var bottomDivider: View
    private lateinit var errorOverlay: View
    private lateinit var progressBar: ProgressBar
    private lateinit var mobileDataWarningToast: TextView
    private lateinit var navItems: List<View>

    private var selectedTab = TAB_HOME
    private var pendingRootTab: Int? = null
    private var pageHasMainFrameError = false
    private var imagePreviewVisible = false
    private var lastFailedUrl = HOME_URL
    private var autoSignInInFlight = false
    private var autoSignInResolvedForLaunch = false
    private var autoSignInLoggedOutNoticeShown = false
    private var signRefreshNoticePending = false
    private var signRefreshNoticeShowing = false
    private var signRefreshNoticeDialog: AlertDialog? = null
    private var openingNativeSettings = false
    private var openingBrowsingHistory = false
    private var officialSettingsVisible = false
    private var returnToNativeSettingsPending = false
    private var officialFeatureReached = false
    private var officialFeatureTargetPath: String? = null
    private var officialFeatureOriginTab = TAB_MINE
    private var officialFeatureTransitionPending = false
    private var threadForwardTransitionPending = false
    private var threadForwardTransitionGeneration = 0
    private var appliedDarkMode = false
    private var cachedLikeEffectId: String? = null
    private var cachedLikeEffectDataUrl: String? = null
    private val cachedLikeEffectDataUrls = mutableMapOf<String, String>()
    private var hideMobileDataWarningRunnable: Runnable? = null
    private var mainFrameLoadGeneration = 0
    private var mainFrameAutomaticRetryCount = 0
    private var automaticRetryTargetUrl: String? = null
    private var mainFrameLoadWatchdog: Runnable? = null
    private val historyExecutor = Executors.newSingleThreadExecutor()

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingAcceptTypes: Array<String> = emptyArray()
    private var pendingAllowMultiple = false
    private var cameraImageUri: Uri? = null

    private var geolocationOrigin: String? = null
    private var geolocationCallback: GeolocationPermissions.Callback? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> handleFileChooserResult(result.resultCode, result.data) }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { launchFileChooser() }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { finishGeolocationRequest(hasLocationPermission()) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppTheme.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appliedDarkMode = AppTheme.isDarkMode(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        bindViews()
        applySystemBarInsets()
        configureWebView()
        configureBottomNavigation()
        configureBackNavigation()
        SignInScheduler.scheduleNext(this)
        if (savedInstanceState == null) {
            showMobileDataWarningIfNeeded()
            checkDeviceSecurityEnvironment()
        }

        selectedTab = savedInstanceState?.getInt(STATE_SELECTED_TAB, TAB_HOME) ?: TAB_HOME
        selectNativeTab(selectedTab)

        findViewById<View>(R.id.retryButton).setOnClickListener {
            errorOverlay.visibility = View.GONE
            mainFrameAutomaticRetryCount = 0
            automaticRetryTargetUrl = null
            resetWebViewVisualState()
            webView.loadUrl(lastFailedUrl)
        }

        val restored = savedInstanceState != null && webView.restoreState(savedInstanceState) != null
        if (!restored) {
            val initialTargetUrl = getTrustedIntentTarget(intent)
            if (initialTargetUrl != null) {
                prepareIntentNavigation(intent, initialTargetUrl)
                webView.loadUrl(initialTargetUrl)
            } else {
                webView.loadUrl(HOME_URL)
            }
        } else if (selectedTab in setOf(TAB_FOLLOW, TAB_MESSAGES, TAB_MINE)) {
            // Vue 的根页内部 Tab 不属于 WebView 历史，Activity 重建后需要主动恢复。
            pendingRootTab = selectedTab
            webView.post { clickWebNavItem(selectedTab, CLICK_RETRY_COUNT) }
        }

        if (restored) {
            webView.post {
                val restoredUrl = webView.url
                if (restoredUrl.isNullOrBlank() || restoredUrl == "about:blank") {
                    webView.loadUrl(HOME_URL)
                } else {
                    scheduleMainFrameLoadWatchdog(restoredUrl)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val targetUrl = getTrustedIntentTarget(intent) ?: return
        prepareIntentNavigation(intent, targetUrl)
        webView.loadUrl(targetUrl)
    }

    private fun prepareIntentNavigation(intent: Intent, targetUrl: String) {
        officialSettingsVisible =
            intent.getBooleanExtra(EXTRA_ALLOW_OFFICIAL_SETTINGS, false) && isSettingsUrl(targetUrl)
        returnToNativeSettingsPending =
            intent.getBooleanExtra(EXTRA_RETURN_TO_NATIVE_SETTINGS, false)
        if (returnToNativeSettingsPending) {
            officialFeatureOriginTab = selectedTab.takeIf { it != TAB_PUBLISH } ?: TAB_MINE
            prepareOfficialFeatureTransition()
        }
        officialFeatureTargetPath = Uri.parse(targetUrl).path
        officialFeatureReached = false
    }

    private fun getTrustedIntentTarget(intent: Intent): String? {
        val rawUrl = intent.getStringExtra(EXTRA_OPEN_URL) ?: return null
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return null
        if (!isInternalAppUri(uri)) return null
        return uri.toString()
    }

    override fun onResume() {
        super.onResume()
        if (appliedDarkMode != AppTheme.isDarkMode(applicationContext)) {
            recreate()
            return
        }
        openingNativeSettings = false
        openingBrowsingHistory = false
        if (::webView.isInitialized) {
            injectDarkModeStyles(webView.url)
            injectEnhancedLikeInteraction(webView.url)
            if (isRootUrl(webView.url)) {
                injectFeedImagePreloader()
                injectHomeSliderPaginationFix()
            }
        }
    }

    private fun bindViews() {
        webView = findViewById(R.id.webView)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        bottomDivider = findViewById(R.id.bottomDivider)
        errorOverlay = findViewById(R.id.errorOverlay)
        progressBar = findViewById(R.id.progressBar)
        mobileDataWarningToast = findViewById(R.id.mobileDataWarningToast)
        navItems = listOf(
            findViewById(R.id.navHome),
            findViewById(R.id.navFollow),
            findViewById(R.id.navPublish),
            findViewById(R.id.navMessages),
            findViewById(R.id.navMine)
        )
    }

    private fun showMobileDataWarningIfNeeded() {
        if (!AppPreferences.isMobileDataWarningEnabled(this)) return
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return
        val activeNetwork = connectivityManager.activeNetwork ?: return
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            showMobileDataWarningOverlay()
        }
    }

    private fun checkDeviceSecurityEnvironment() {
        if (!AppPreferences.isDeviceSecurityCheckEnabled(this)) return
        Thread {
            val mayBeCompromised = DeviceSecurityEnvironment.mayBeCompromised()
            if (!mayBeCompromised) return@Thread
            runOnUiThread {
                if (isFinishing || isDestroyed ||
                    !AppPreferences.isDeviceSecurityCheckEnabled(this)
                ) {
                    return@runOnUiThread
                }
                AlertDialog.Builder(this)
                    .setTitle(R.string.device_security_warning_title)
                    .setMessage(R.string.device_security_warning_message)
                    .setPositiveButton(R.string.permission_confirm, null)
                    .setNegativeButton(R.string.device_security_do_not_remind) { _, _ ->
                        AppPreferences.setDeviceSecurityCheckEnabled(this, false)
                    }
                    .show()
            }
        }.start()
    }

    private fun showMobileDataWarningOverlay() {
        val yOffset = resources.displayMetrics.heightPixels * -0.16f
        hideMobileDataWarningRunnable?.let { mobileDataWarningToast.removeCallbacks(it) }

        mobileDataWarningToast.apply {
            text = getString(R.string.mobile_data_warning_toast)
            visibility = View.VISIBLE
            alpha = 0f
            translationY = yOffset + 18f
            bringToFront()
            animate()
                .alpha(1f)
                .translationY(yOffset)
                .setDuration(180L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        val hideRunnable = Runnable {
            mobileDataWarningToast.animate()
                .alpha(0f)
                .translationY(yOffset + 18f)
                .setDuration(220L)
                .withEndAction { mobileDataWarningToast.visibility = View.GONE }
                .start()
        }
        hideMobileDataWarningRunnable = hideRunnable
        mobileDataWarningToast.postDelayed(hideRunnable, MOBILE_DATA_WARNING_VISIBLE_MS)
    }

    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.rootContainer)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            builtInZoomControls = false
            displayZoomControls = false
        }

        webView.webViewClient = createWebViewClient()
        webView.webChromeClient = createWebChromeClient()
        webView.addJavascriptInterface(NativeUiBridge(), JS_BRIDGE_NAME)
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.app_background))
    }

    private fun configureBottomNavigation() {
        navItems[TAB_HOME].setOnClickListener { openRootTab(TAB_HOME) }
        navItems[TAB_FOLLOW].setOnClickListener { openRootTab(TAB_FOLLOW) }
        navItems[TAB_PUBLISH].setOnClickListener {
            selectedTab = TAB_PUBLISH
            selectNativeTab(TAB_PUBLISH)
            pendingRootTab = null
            webView.loadUrl(RELEASE_URL)
        }
        navItems[TAB_MESSAGES].setOnClickListener { openRootTab(TAB_MESSAGES) }
        navItems[TAB_MINE].setOnClickListener { openRootTab(TAB_MINE) }
    }

    private fun openRootTab(tab: Int) {
        selectedTab = tab
        selectNativeTab(tab)

        if (isRootUrl(webView.url)) {
            pendingRootTab = tab
            clickWebNavItem(tab, CLICK_RETRY_COUNT)
        } else {
            pendingRootTab = tab
            webView.loadUrl(HOME_URL)
        }
    }

    private fun selectNativeTab(tab: Int) {
        navItems.forEachIndexed { index, item -> setSelectedRecursively(item, index == tab) }
    }

    private fun setSelectedRecursively(view: View, selected: Boolean) {
        view.isSelected = selected
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) {
                setSelectedRecursively(view.getChildAt(index), selected)
            }
        }
    }

    private fun createWebViewClient() = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            return handleMainFrameNavigation(request.url)
        }

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            return handleMainFrameNavigation(Uri.parse(url))
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (url != automaticRetryTargetUrl) {
                mainFrameAutomaticRetryCount = 0
                automaticRetryTargetUrl = null
            }
            pageHasMainFrameError = false
            imagePreviewVisible = false
            errorOverlay.visibility = View.GONE
            scheduleMainFrameLoadWatchdog(url)
            markOfficialFeatureReached(url)
            updateNativeChrome(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            verifyMainFrameHasVisibleContent(url)
            updateNativeChrome(url)
            injectImagePreviewObserver()
            injectDarkModeStyles(url)
            injectEnhancedLikeInteraction(url)
            injectBrowsingHistoryCollector(url)
            syncSignAuthToken()
            finishOfficialFeatureTransitionIfNeeded(url)
            finishThreadForwardTransitionIfNeeded(url)

            if (!pageHasMainFrameError) {
                errorOverlay.visibility = View.GONE
            }

            if (isRootUrl(url)) {
                injectHideWebNavCss()
                injectNativeSettingsShortcut()
                injectFeedImagePreloader()
                injectHomeSliderPaginationFix()
                pendingRootTab?.let { clickWebNavItem(it, CLICK_RETRY_COUNT) }
                showSignRefreshNoticeIfPending()
                attemptAutoSignIn()
            } else {
                removeHideWebNavCss()
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            injectDarkModeStyles(url)
            injectEnhancedLikeInteraction(url)
            injectBrowsingHistoryCollector(url)
            markOfficialFeatureReached(url)
            if (shouldReturnToNativeSettings(url)) {
                returnToNativeSettings(resetWebViewToHome = false)
                return
            }
            if (isSettingsUrl(url) && !officialSettingsVisible) {
                openNativeSettings()
                if (view.canGoBack()) view.goBack() else view.loadUrl(HOME_URL)
                return
            }
            if (!isSettingsUrl(url)) officialSettingsVisible = false
            updateNativeChrome(url)
            if (isRootUrl(url)) {
                injectHideWebNavCss()
                injectNativeSettingsShortcut()
                injectFeedImagePreloader()
                injectHomeSliderPaginationFix()
            } else {
                removeHideWebNavCss()
            }
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            injectDarkModeStyles(url)
            finishOfficialFeatureTransitionIfNeeded(url)
            finishThreadForwardTransitionIfNeeded(url)
            view.postDelayed(
                { verifyMainFrameHasVisibleContent(url) },
                MAIN_FRAME_RENDER_CHECK_DELAY_MS
            )
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (request.isForMainFrame) showLoadError(request.url.toString())
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                showLoadError(request.url.toString())
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
            // 证书异常必须中止，不能为方便而绕过 HTTPS 校验。
            handler.cancel()
            showLoadError(error.url ?: view.url ?: HOME_URL)
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail
        ): Boolean {
            cancelMainFrameLoadWatchdog()
            if (!isFinishing && !isDestroyed) {
                view.post { recreate() }
            }
            return true
        }
    }

    private fun createWebChromeClient() = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progressBar.progress = newProgress
            progressBar.visibility = if (newProgress in 0..99) View.VISIBLE else View.GONE
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            this@MainActivity.filePathCallback?.onReceiveValue(null)
            this@MainActivity.filePathCallback = filePathCallback
            pendingAcceptTypes = fileChooserParams.acceptTypes
                .filter { it.isNotBlank() }
                .toTypedArray()
            pendingAllowMultiple = fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE

            if (acceptsImages() && !hasPermission(Manifest.permission.CAMERA)) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                launchFileChooser()
            }
            return true
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: GeolocationPermissions.Callback
        ) {
            if (!isTrustedOrigin(origin)) {
                callback.invoke(origin, false, false)
                return
            }

            geolocationCallback?.invoke(geolocationOrigin, false, false)
            geolocationOrigin = origin
            geolocationCallback = callback

            if (hasLocationPermission()) {
                finishGeolocationRequest(true)
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        override fun onGeolocationPermissionsHidePrompt() {
            finishGeolocationRequest(false)
        }

    }

    private fun handleMainFrameNavigation(uri: Uri): Boolean {
        val normalizedInternalUri = normalizeInternalNavigationUri(uri)
        if (normalizedInternalUri != null && normalizedInternalUri != uri) {
            webView.loadUrl(normalizedInternalUri.toString())
            return true
        }
        if (shouldReturnToNativeSettings(uri.toString())) {
            returnToNativeSettings(resetWebViewToHome = true)
            return true
        }
        if (isSettingsUrl(uri.toString())) {
            if (officialSettingsVisible) return false
            openNativeSettings()
            return true
        }
        officialSettingsVisible = false
        if (isRootUrl(webView.url) && isThreadInfoUri(uri)) {
            startThreadForwardTransition(uri.toString())
            return true
        }
        if (isInternalAppUri(uri) || normalizedInternalUri != null) return false
        openExternalUrl(uri)
        return true
    }

    private fun isSettingsUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return uri.scheme == "https" &&
            uri.host.equals(TARGET_HOST, ignoreCase = true) &&
            (uri.path == "/m/set" || uri.path == "/m/set/")
    }

    private fun openNativeSettings() {
        if (openingNativeSettings) return
        openingNativeSettings = true
        val options = ActivityOptions.makeCustomAnimation(
            this,
            R.anim.settings_enter,
            R.anim.activity_hold
        )
        startActivity(Intent(this, SettingsActivity::class.java), options.toBundle())
    }

    private fun openBrowsingHistory() {
        if (openingBrowsingHistory) return
        openingBrowsingHistory = true
        val options = ActivityOptions.makeCustomAnimation(
            this,
            R.anim.settings_enter,
            R.anim.activity_hold
        )
        startActivity(Intent(this, BrowsingHistoryActivity::class.java), options.toBundle())
    }

    private fun markOfficialFeatureReached(url: String?) {
        if (!returnToNativeSettingsPending || officialFeatureReached || url.isNullOrBlank()) return
        val path = runCatching { Uri.parse(url).path }.getOrNull()
        if (path == officialFeatureTargetPath) officialFeatureReached = true
    }

    private fun shouldReturnToNativeSettings(url: String?): Boolean {
        return returnToNativeSettingsPending && officialFeatureReached && isRootUrl(url)
    }

    private fun returnToNativeSettings(resetWebViewToHome: Boolean) {
        returnToNativeSettingsPending = false
        officialFeatureReached = false
        officialFeatureTargetPath = null
        officialSettingsVisible = false
        restoreRootTabBehindSettings(officialFeatureOriginTab)
        if (resetWebViewToHome && !isRootUrl(webView.url)) {
            webView.loadUrl(HOME_URL)
        } else if (isRootUrl(webView.url) && pendingRootTab != null) {
            webView.post {
                injectHideWebNavCss()
                clickWebNavItem(officialFeatureOriginTab, CLICK_RETRY_COUNT)
            }
        }
        openNativeSettings()
    }

    private fun restoreRootTabBehindSettings(tab: Int) {
        val rootTab = tab.takeIf { it in setOf(TAB_HOME, TAB_FOLLOW, TAB_MESSAGES, TAB_MINE) }
            ?: TAB_MINE
        selectedTab = rootTab
        selectNativeTab(rootTab)
        pendingRootTab = rootTab.takeUnless { it == TAB_HOME }
    }

    private fun prepareOfficialFeatureTransition() {
        officialFeatureTransitionPending = true
        webView.animate().cancel()
        webView.alpha = 0f
        webView.translationX = resources.displayMetrics.widthPixels * WEB_TRANSITION_DISTANCE_RATIO
        bottomNavigation.visibility = View.GONE
        bottomDivider.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
    }

    private fun finishOfficialFeatureTransitionIfNeeded(url: String?) {
        if (!officialFeatureTransitionPending || url.isNullOrBlank()) return
        val path = runCatching { Uri.parse(url).path }.getOrNull()
        if (path != officialFeatureTargetPath) return
        officialFeatureTransitionPending = false
        webView.animate().cancel()
        webView.alpha = WEB_TRANSITION_DIM_ALPHA
        webView.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(SETTINGS_TRANSITION_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun startThreadForwardTransition(targetUrl: String) {
        val generation = ++threadForwardTransitionGeneration
        threadForwardTransitionPending = true
        webView.animate().cancel()
        webView.translationX = 0f
        webView.animate()
            .alpha(THREAD_TRANSITION_DIM_ALPHA)
            .translationX(0f)
            .setDuration(THREAD_TRANSITION_FADE_OUT_MS)
            .setInterpolator(LinearInterpolator())
            .withEndAction {
                if (
                    threadForwardTransitionPending &&
                    generation == threadForwardTransitionGeneration
                ) {
                    webView.postDelayed(
                        {
                            if (
                                threadForwardTransitionPending &&
                                generation == threadForwardTransitionGeneration
                            ) {
                                webView.loadUrl(targetUrl)
                            }
                        },
                        THREAD_TRANSITION_LOAD_DELAY_MS
                    )
                }
            }
            .start()
    }

    private fun finishThreadForwardTransitionIfNeeded(url: String?) {
        if (!threadForwardTransitionPending || url.isNullOrBlank()) return
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        if (!isThreadInfoUri(uri)) return
        threadForwardTransitionPending = false
        webView.animate().cancel()
        webView.translationX = 0f
        webView.alpha = THREAD_TRANSITION_DIM_ALPHA
        webView.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(THREAD_TRANSITION_FADE_IN_MS)
            .setInterpolator(LinearInterpolator())
            .start()
    }

    private fun updateNativeChrome(url: String?) {
        syncPublishExitSelection(url)
        val visibility = if (isRootUrl(url) && !imagePreviewVisible) View.VISIBLE else View.GONE
        bottomNavigation.visibility = visibility
        bottomDivider.visibility = visibility
    }

    private fun syncPublishExitSelection(url: String?) {
        if (selectedTab != TAB_PUBLISH || pendingRootTab != null || !isRootUrl(url)) return
        selectedTab = TAB_HOME
        selectNativeTab(TAB_HOME)
    }

    private fun injectImagePreviewObserver() {
        if (!isInternalMobileUri(Uri.parse(webView.url ?: return))) return
        webView.evaluateJavascript(
            WebInjectionScripts.imagePreviewObserver(JS_BRIDGE_NAME),
            null
        )
    }

    private fun injectNativeSettingsShortcut() {
        if (!isRootUrl(webView.url)) return
        webView.evaluateJavascript(
            WebInjectionScripts.nativeSettingsShortcut(JS_BRIDGE_NAME),
            null
        )
    }

    private fun injectBrowsingHistoryCollector(url: String?) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        if (!isInternalMobileUri(uri)) return
        val topicId = uri.getQueryParameter("id")
            ?.takeIf { isThreadInfoUri(uri) && it.toLongOrNull()?.let { id -> id > 0L } == true }

        if (topicId == null) {
            webView.evaluateJavascript(
                """
                (function() {
                    window.clearTimeout(window.__androidHistoryRecordTimer);
                    window.__androidHistoryRouteKey = null;
                    window.__androidHistoryRecorded = false;
                    return true;
                })();
                """.trimIndent(),
                null
            )
            return
        }

        val bridgeName = JSONObject.quote(JS_BRIDGE_NAME)
        webView.evaluateJavascript(
            """
            (function() {
                const routeKey = ${JSONObject.quote(topicId)};
                if (window.__androidHistoryRouteKey === routeKey &&
                    window.__androidHistoryRecorded) return true;

                window.clearTimeout(window.__androidHistoryRecordTimer);
                if (window.__androidHistoryRouteKey !== routeKey) {
                    window.__androidHistoryRouteKey = routeKey;
                    window.__androidHistoryRecorded = false;
                }

                const readDetails = function() {
                    if (window.__androidHistoryRouteKey !== routeKey) return null;
                    const titleNode = document.querySelector(
                        '.scroll-area > .card_item .card_m1 > p'
                    );
                    const authorNode = document.querySelector(
                        '.scroll-area > .card_item .card_t .card_tm > div'
                    );
                    const viewNode = document.querySelector(
                        '.scroll-area > .card_item .card_t .card_tm > p'
                    );
                    if (!titleNode || !authorNode || !viewNode) return null;

                    const authorCopy = authorNode.cloneNode(true);
                    authorCopy.querySelectorAll('span, img').forEach(function(node) {
                        node.remove();
                    });
                    const title = (titleNode.textContent || '').replace(/\s+/g, ' ').trim();
                    const author = (authorCopy.textContent || '').replace(/\s+/g, ' ').trim();
                    const viewMatch = (viewNode.textContent || '').match(/(\d+)\s*阅读/);
                    if (!title || !author || !viewMatch) return null;
                    return { title: title, author: author, viewCount: viewMatch[1] };
                };

                const waitForDetails = function(attempt) {
                    const details = readDetails();
                    if (!details) {
                        if (attempt < 24 && window.__androidHistoryRouteKey === routeKey) {
                            window.__androidHistoryRecordTimer = window.setTimeout(function() {
                                waitForDetails(attempt + 1);
                            }, 250);
                        }
                        return;
                    }

                    const bridge = window[$bridgeName];
                    if (window.__androidHistoryRecorded ||
                        window.__androidHistoryRouteKey !== routeKey ||
                        !bridge) return;
                    window.__androidHistoryRecorded = true;
                    bridge.recordBrowsingHistory(
                        routeKey,
                        details.title,
                        details.author,
                        details.viewCount
                    );
                };

                waitForDetails(0);
                return true;
            })();
            """.trimIndent(),
            null
        )
    }

    private fun injectEnhancedLikeInteraction(url: String?) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        if (!isInternalMobileUri(uri)) return

        val enabled = AppPreferences.isEnhancedLikeInteractionEnabled(this)
        val effectId = AppPreferences.getLikeEffect(this)
        val durationMs = AppPreferences.getLikeEffectDurationSeconds(this) * 1000
        val sizeMultiplier = AppPreferences.getLikeEffectSizeMultiplier(this)
        val showOnUnlike = AppPreferences.isLikeEffectOnUnlikeEnabled(this)
        val randomEffect = effectId == LikeEffectAssets.RANDOM_ID
        val dataUrl = if (enabled && !randomEffect) getLikeEffectDataUrl(effectId) else ""
        val dataUrls = if (enabled && randomEffect) getLikeEffectDataUrlsJson() else "[]"
        val bridgeName = JSONObject.quote(JS_BRIDGE_NAME)

        webView.evaluateJavascript(
            """
            (function() {
                const androidBridgeName = $bridgeName;
                const getAndroidBridge = function() {
                    return window[androidBridgeName];
                };
                window.__androidLikeEffectConfig = {
                    enabled: $enabled,
                    assetId: ${JSONObject.quote(effectId)},
                    imageUrl: ${JSONObject.quote(dataUrl)},
                    imageUrls: $dataUrls,
                    durationMs: $durationMs,
                    sizeMultiplier: $sizeMultiplier,
                    showOnUnlike: $showOnUnlike
                };
                if (window.__androidLikeEffectInstalled) return true;
                window.__androidLikeEffectInstalled = true;

                const removeEffect = function(element) {
                    if (element && element.parentNode) element.parentNode.removeChild(element);
                };

                const readCardText = function(card) {
                    if (!card) return { title: '', author: '' };
                    const titleNode = card.querySelector('.card_tit p, .card_m1 > p');
                    const authorNode = card.querySelector('.card_t .card_tm > div');
                    const authorCopy = authorNode ? authorNode.cloneNode(true) : null;
                    if (authorCopy) {
                        authorCopy.querySelectorAll('span, img').forEach(function(node) {
                            node.remove();
                        });
                    }
                    return {
                        title: (
                            card.getAttribute('title') ||
                            (titleNode && titleNode.textContent) ||
                            ''
                        ).replace(/\s+/g, ' ').trim(),
                        author: authorCopy
                            ? (authorCopy.textContent || '').replace(/\s+/g, ' ').trim()
                            : ''
                    };
                };

                const collectTopicModels = function(value, results, visited, depth) {
                    if (!value || typeof value !== 'object' || depth > 5) return;
                    if (visited.indexOf(value) >= 0) return;
                    visited.push(value);
                    if (value.topic_id) results.push(value);
                    if (Array.isArray(value)) {
                        value.forEach(function(item) {
                            collectTopicModels(item, results, visited, depth + 1);
                        });
                        return;
                    }
                    Object.keys(value).slice(0, 80).forEach(function(key) {
                        collectTopicModels(value[key], results, visited, depth + 1);
                    });
                };

                const findPostModel = function(card) {
                    if (!card) return null;
                    const cardText = readCardText(card);
                    const title = cardText.title;
                    const author = cardText.author;

                    let node = card;
                    const visited = [];
                    while (node) {
                        let vm = node.__vue__;
                        let depth = 0;
                        while (vm && depth < 10) {
                            if (visited.indexOf(vm) >= 0) break;
                            visited.push(vm);
                            const candidates = [];
                            collectTopicModels(vm.${'$'}data || vm, candidates, [], 0);
                            const match = candidates.find(function(item) {
                                if (!item || !item.topic_id) return false;
                                if (title && String(item.title || '').trim() !== title) return false;
                                return !author ||
                                    String(item.user_nick_name || '').trim() === author;
                            }) || candidates.find(function(item) {
                                return item && item.topic_id && title &&
                                    String(item.title || '').trim() === title;
                            });
                            if (match) return match;
                            vm = vm.${'$'}parent;
                            depth++;
                        }
                        node = node.parentElement;
                    }
                    return null;
                };

                const isPostLiked = function(post) {
                    if (!post) return false;
                    return post.is_like === true ||
                        post.is_like === 1 ||
                        post.is_like === '1' ||
                        post.is_like === 'true';
                };

                const hasLikeField = function(value) {
                    return !!value &&
                        typeof value === 'object' &&
                        Object.prototype.hasOwnProperty.call(value, 'is_like');
                };

                const isDetailPostCandidate = function(value) {
                    return !!value &&
                        typeof value === 'object' &&
                        !!value.topic_id &&
                        hasLikeField(value);
                };

                const scanDetailPostCandidate = function(value, visited, depth) {
                    if (!value || typeof value !== 'object' || depth > 4) return null;
                    if (visited.indexOf(value) >= 0) return null;
                    visited.push(value);
                    if (isDetailPostCandidate(value)) return value;

                    if (Array.isArray(value)) {
                        for (let index = 0; index < value.length; index++) {
                            const match = scanDetailPostCandidate(value[index], visited, depth + 1);
                            if (match) return match;
                        }
                        return null;
                    }

                    const preferredKeys = ['obj', 'post', 'topic', 'detail', 'info', 'data', 'article'];
                    for (let index = 0; index < preferredKeys.length; index++) {
                        const match = scanDetailPostCandidate(value[preferredKeys[index]], visited, depth + 1);
                        if (match) return match;
                    }
                    return null;
                };

                const findDetailPostModel = function(element) {
                    let node = element;
                    const visited = [];
                    while (node) {
                        let vm = node.__vue__;
                        let depth = 0;
                        while (vm && depth < 10) {
                            if (visited.indexOf(vm) >= 0) break;
                            visited.push(vm);
                            const directMatch = scanDetailPostCandidate(vm, [], 0);
                            if (directMatch) return directMatch;
                            const dataMatch = scanDetailPostCandidate(vm.${'$'}data, [], 0);
                            if (dataMatch) return dataMatch;
                            vm = vm.${'$'}parent;
                            depth++;
                        }
                        node = node.parentElement;
                    }
                    return null;
                };

                const trackHomepageLike = function(card, initialPost) {
                    if (!card || card.__androidLikeHistoryPending) return;
                    const post = initialPost || findPostModel(card);
                    const wasLiked = post && post.topic_id ? isPostLiked(post) : null;
                    card.__androidLikeHistoryPending = true;

                    const checkResult = function(attempt) {
                        const currentPost = findPostModel(card) || post;
                        if (!currentPost || !currentPost.topic_id) {
                            if (attempt < 20) {
                                window.setTimeout(function() {
                                    checkResult(attempt + 1);
                                }, 200);
                            } else {
                                card.__androidLikeHistoryPending = false;
                            }
                            return;
                        }
                        const isLiked = isPostLiked(currentPost);
                        const shouldNotify = wasLiked === null ? isLiked : isLiked !== wasLiked;
                        if (shouldNotify) {
                            card.__androidLikeHistoryPending = false;
                            const bridge = getAndroidBridge();
                            if (bridge) {
                                bridge.onHomepageLikeChanged(
                                    String(currentPost.topic_id),
                                    String(currentPost.title || ''),
                                    String(currentPost.user_nick_name || ''),
                                    isLiked
                                );
                            }
                            return;
                        }
                        if (attempt < 20) {
                            window.setTimeout(function() {
                                checkResult(attempt + 1);
                            }, 200);
                        } else {
                            card.__androidLikeHistoryPending = false;
                        }
                    };
                    window.setTimeout(function() { checkResult(0); }, 200);
                };

                const trackLikeEffect = function(button, baseSizeRatio, post) {
                    const config = window.__androidLikeEffectConfig;
                    if (!config || !config.enabled) return;
                    if (config.showOnUnlike) {
                        spawnEffect(button, baseSizeRatio);
                        return;
                    }
                    if (!post || typeof post.is_like === 'undefined') return;
                    const wasLiked = isPostLiked(post);

                    const checkResult = function(attempt) {
                        const isLiked = isPostLiked(post);
                        if (isLiked !== wasLiked) {
                            if (isLiked) spawnEffect(button, baseSizeRatio);
                            return;
                        }
                        if (attempt < 20) {
                            window.setTimeout(function() {
                                checkResult(attempt + 1);
                            }, 200);
                        }
                    };
                    window.setTimeout(function() { checkResult(0); }, 200);
                };

                const spawnEffect = function(button, baseSizeRatio) {
                    const config = window.__androidLikeEffectConfig;
                    const imageUrl = config && config.assetId === 'random' && Array.isArray(config.imageUrls)
                        ? config.imageUrls[Math.floor(Math.random() * config.imageUrls.length)]
                        : config && config.imageUrl;
                    if (!config || !config.enabled || !imageUrl || !document.body) return;

                    const existing = Array.from(document.querySelectorAll('.android-like-effect'));
                    while (existing.length >= 8) removeEffect(existing.shift());

                    const rect = button.getBoundingClientRect();
                    const multiplier = Math.max(1, Math.min(5, config.sizeMultiplier || 1.5));
                    const duration = Math.max(1000, Math.min(10000, config.durationMs || 2000));
                    const size = Math.max(1, window.innerWidth * baseSizeRatio * multiplier);
                    const startX = rect.left + rect.width / 2 - size / 2;
                    const startY = rect.top - size * 0.9;
                    const endY = window.innerHeight + size * 1.25;
                    const leftTravel = Math.min(window.innerWidth * 0.28, size * 4.2);
                    const jumpHeight = size * 2.4;
                    const frames = [];

                    for (let step = 0; step <= 10; step++) {
                        const t = step / 10;
                        const x = startX - leftTravel * t;
                        const y = startY
                            - jumpHeight * 4 * t * (1 - t)
                            + (endY - startY) * t * t;
                        frames.push({
                            transform: `translate3d(${'$'}{x}px, ${'$'}{y}px, 0) rotate(${'$'}{-35 * t}deg)`,
                            opacity: t < 0.86 ? 1 : (1 - t) / 0.14,
                            offset: t
                        });
                    }

                    const image = document.createElement('img');
                    image.className = 'android-like-effect';
                    image.src = imageUrl;
                    image.alt = '';
                    image.setAttribute('aria-hidden', 'true');
                    Object.assign(image.style, {
                        position: 'fixed',
                        left: '0',
                        top: '0',
                        width: `${'$'}{size}px`,
                        height: `${'$'}{size}px`,
                        objectFit: 'contain',
                        pointerEvents: 'none',
                        userSelect: 'none',
                        zIndex: '2147483646',
                        willChange: 'transform, opacity'
                    });
                    document.body.appendChild(image);

                    const animation = image.animate(frames, {
                        duration: duration,
                        easing: 'linear',
                        fill: 'forwards'
                    });
                    animation.onfinish = function() { removeEffect(image); };
                    animation.oncancel = function() { removeEffect(image); };
                    window.setTimeout(function() { removeEffect(image); }, duration + 100);
                };

                document.addEventListener('click', function(event) {
                    const target = event.target;
                    if (!target || !target.closest) return;

                    let button = null;
                    let historyCard = null;
                    let postModel = null;
                    let sizeRatio = 0;
                    const detailPostBox = target.closest('.post_box');
                    const detailButton = detailPostBox && target.closest('.post_box .card_b_item');
                    const detailItems = detailPostBox
                        ? Array.from(detailPostBox.querySelectorAll('.card_b_item'))
                        : [];
                    if (
                        /\/m\/threadInfo\/?$/.test(window.location.pathname) &&
                        detailButton &&
                        detailPostBox &&
                        detailPostBox.contains(detailButton) &&
                        detailButton === detailItems[0]
                    ) {
                        button = detailButton;
                        postModel = findDetailPostModel(detailPostBox) || findDetailPostModel(detailButton);
                        sizeRatio = 0.0533333333;
                    } else {
                        const cardButton = target.closest('.card_b_item');
                        const cardBar = cardButton && cardButton.parentElement;
                        const card = cardButton && cardButton.closest('.card_item');
                        const cardItems = cardBar
                            ? Array.from(cardBar.children).filter(function(item) {
                                return item.classList && item.classList.contains('card_b_item');
                            })
                            : [];
                        if (
                            card &&
                            cardBar &&
                            cardBar.classList.contains('card_b') &&
                            card.contains(cardBar) &&
                            cardItems.length >= 2 &&
                            cardButton === cardItems[cardItems.length - 1]
                        ) {
                            button = cardButton;
                            historyCard = card;
                            postModel = findPostModel(card);
                            sizeRatio = 0.04;
                        }
                    }
                    if (historyCard) trackHomepageLike(historyCard, postModel);
                    if (button) trackLikeEffect(button, sizeRatio, postModel);
                }, true);
                return true;
            })();
            """.trimIndent(),
            null
        )
    }

    @SuppressLint("ResourceType")
    private fun getLikeEffectDataUrl(effectId: String): String {
        if (cachedLikeEffectId == effectId && cachedLikeEffectDataUrl != null) {
            return cachedLikeEffectDataUrl!!
        }
        cachedLikeEffectDataUrls[effectId]?.let {
            cachedLikeEffectId = effectId
            cachedLikeEffectDataUrl = it
            return it
        }
        val option = LikeEffectAssets.find(effectId)
        val encoded = resources.openRawResource(option.drawableRes).use { stream ->
            Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP)
        }
        return "data:image/png;base64,$encoded".also {
            cachedLikeEffectId = option.id
            cachedLikeEffectDataUrl = it
            cachedLikeEffectDataUrls[option.id] = it
        }
    }

    private fun getLikeEffectDataUrlsJson(): String {
        return LikeEffectAssets.options.joinToString(prefix = "[", postfix = "]") { option ->
            JSONObject.quote(getLikeEffectDataUrl(option.id))
        }
    }

    private fun injectDarkModeStyles(url: String?) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        if (!isInternalMobileUri(uri)) return
        val darkEnabled = AppTheme.isDarkMode(applicationContext)
        webView.evaluateJavascript(
            """
            (function() {
                const styleId = 'android-dark-mode-style';
                const existing = document.getElementById(styleId);
                if (!$darkEnabled) {
                    if (existing) existing.remove();
                    document.documentElement.style.colorScheme = '';
                    return false;
                }
                if (!document.head) return false;
                const style = existing || document.createElement('style');
                style.id = styleId;
                style.textContent = `
                    :root { color-scheme: dark !important; }
                    html, body, #app, .main, .home_main, .main_box, .content,
                    .index_con, [class$="_main"], [class$="_con"], [class$="_content"] {
                        background-color: #101214 !important;
                        color: #f3f1ed !important;
                    }
                    .head, .head_pc, .card_item, .van-popup, .van-dialog,
                    .van-action-sheet, .van-action-sheet__item, .van-action-sheet__cancel,
                    [class$="_box"], [class$="_item"] {
                        background-color: #1a1d20 !important;
                        border-color: rgba(255,255,255,.13) !important;
                    }
                    body p, body span, body h1, body h2, body h3, body h4,
                    body label, body li, body td, body th, body .van-cell,
                    body .van-field__control, body .van-search__content {
                        color: #f3f1ed !important;
                    }
                    body input, body textarea, body select, body .van-cell,
                    body .van-field, body .van-search__content {
                        background-color: #22262a !important;
                        border-color: rgba(255,255,255,.16) !important;
                    }
                    body input::placeholder, body textarea::placeholder {
                        color: #999da2 !important;
                    }
                    #div11, #div1,
                    #div11 .w-e-text-container, #div1 .w-e-text-container,
                    #div11 .w-e-text, #div1 .w-e-text {
                        background-color: #22262a !important;
                        border-color: rgba(255,255,255,.16) !important;
                        color: #f3f1ed !important;
                    }
                    #div11, #div1 {
                        box-shadow: 0 2px 8px rgba(0,0,0,.45) !important;
                    }
                    #div11 [contenteditable="true"], #div1 [contenteditable="true"] {
                        caret-color: #f3f1ed !important;
                    }
                    #div11 .w-e-text:empty::before, #div1 .w-e-text:empty::before,
                    #div11 [data-placeholder]::before, #div1 [data-placeholder]::before {
                        color: #999da2 !important;
                    }
                    #div11 + .btns > .theme_item,
                    #div1 + .btns > .theme_item,
                    #div11 + .btns > .theme_item > .sear_themesbox,
                    #div1 + .btns > .theme_item > .sear_themesbox {
                        box-sizing: border-box !important;
                        min-width: 0 !important;
                    }
                    #div11 + .btns > .theme_item > .sear_themesbox,
                    #div1 + .btns > .theme_item > .sear_themesbox {
                        position: relative !important;
                        max-width: 100% !important;
                    }
                    #div11 + .btns > .theme_item > .sear_themesbox > input,
                    #div1 + .btns > .theme_item > .sear_themesbox > input {
                        width: 100% !important;
                        max-width: 100% !important;
                        box-sizing: border-box !important;
                        background-color: #22262a !important;
                        border-color: rgba(255,255,255,.24) !important;
                        color: #f3f1ed !important;
                    }
                    #div11 + .btns > .theme_item > .sear_themesbox > ul,
                    #div1 + .btns > .theme_item > .sear_themesbox > ul {
                        left: 0 !important;
                        right: auto !important;
                        width: 100% !important;
                        max-width: 100% !important;
                        box-sizing: border-box !important;
                        overflow-x: hidden !important;
                        overflow-y: auto !important;
                        background-color: #22262a !important;
                        border-color: rgba(255,255,255,.24) !important;
                        color: #f3f1ed !important;
                        box-shadow: 0 4px 14px rgba(0,0,0,.55) !important;
                    }
                    #div11 + .btns > .theme_item > .sear_themesbox > ul > li,
                    #div1 + .btns > .theme_item > .sear_themesbox > ul > li {
                        box-sizing: border-box !important;
                        max-width: 100% !important;
                        overflow: hidden !important;
                        color: #f3f1ed !important;
                        background-color: transparent !important;
                        text-overflow: ellipsis !important;
                        white-space: nowrap !important;
                    }
                    #div11 + .btns > .theme_item > .sear_themesbox > ul > li[style*="color"],
                    #div1 + .btns > .theme_item > .sear_themesbox > ul > li[style*="color"] {
                        color: #f26c1c !important;
                    }
                    body a { color: #ff9279 !important; }
                    .conditions1, .conditions1 p, .conditons_right,
                    .tab_box .van-tab, .tab_box .van-tab__text,
                    .the_box .van-button, .the_box .van-button__text,
                    .the_box .theme_item, .the_box .theme_item span {
                        color: #f3f1ed !important;
                    }
                    .tab_box, .tab_box .van-tabs, .tab_box .van-tabs__wrap,
                    .tab_box .van-tabs__nav, .conditions1, .index_news {
                        background-color: #1a1d20 !important;
                        border-color: rgba(255,255,255,.13) !important;
                    }
                    .tab_box .van-tab, .tab_box .van-tab__text {
                        color: #c6c3be !important;
                    }
                    .tab_box .van-tab--active, .tab_box .van-tab--active .van-tab__text {
                        color: #ff765f !important;
                    }
                    .conditions1 p, .conditions1 .conditons_right {
                        color: #f3f1ed !important;
                    }
                    .the_box .van-button, .the_box .theme_item {
                        background-color: #272b2f !important;
                        border-color: #555b61 !important;
                        color: #f3f1ed !important;
                    }
                    .t_box, .t_box span {
                        color: #111111 !important;
                    }
                    .items .item .p_name {
                        color: #111111 !important;
                    }
                    .sign, .task, .sign .gift_box, .task .task_item {
                        background-color: #1a1d20 !important;
                        border-color: rgba(255,255,255,.13) !important;
                        box-shadow: 0 0 18px rgba(0,0,0,.38) !important;
                    }
                    .sign_title, .task_title,
                    .sign_title span, .gift_item p,
                    .task_item_l p, .task_item_l span {
                        color: #f3f1ed !important;
                    }
                    .card_t .van-button {
                        background-color: #1a1d20 !important;
                        border-color: #ff765f !important;
                        color: #f3f1ed !important;
                    }
                    .van-image__loading, .van-image__error,
                    .image-grid-container .skeleton {
                        background-color: #2a2e32 !important;
                        background-image: none !important;
                        color: #8f9499 !important;
                    }
                    .list_wrap, .list_wrap .list, .list_wrap .list > li {
                        background-color: #1a1d20 !important;
                        border-color: rgba(255,255,255,.13) !important;
                    }
                    .list_wrap > p, .list_wrap .list > li > p {
                        color: #f3f1ed !important;
                    }
                    .list_wrap .van-button {
                        background-color: #1a1d20 !important;
                        border-color: #ff765f !important;
                        color: #f3f1ed !important;
                    }
                    .list_wrap .van-button.flowed {
                        background-color: #272b2f !important;
                        border-color: #555b61 !important;
                        color: #c6c3be !important;
                    }
                    .van-button__text, .the_box .theme_item span,
                    .card_m_con, .card_con_text, .card_con_reply_con,
                    .message .me_con, .mine_box > .van-button span {
                        background-color: transparent !important;
                        background-image: none !important;
                    }
                    .van-list, .van-list__finished-text,
                    .van-list__loading, .van-list__error-text {
                        background-color: #101214 !important;
                        color: #a9adb1 !important;
                    }
                    .scroll-area, .scroll-area .comment_head,
                    .scroll-area .card_con, .scroll-area .comment_reply_box,
                    .scroll-area .card_con_reply {
                        background-color: #101214 !important;
                        border-color: rgba(255,255,255,.13) !important;
                    }
                    .scroll-area + .post_box {
                        background-color: #171a1d !important;
                        border-color: rgba(255,255,255,.16) !important;
                    }
                    .scroll-area + .post_box > span {
                        background-color: #262a2e !important;
                        border-color: rgba(255,255,255,.16) !important;
                        color: #a9adb1 !important;
                    }
                    .scroll-area + .post_box,
                    .post_box {
                        box-shadow: 0 -6px 16px rgba(0,0,0,.45) !important;
                    }
                    .scroll-area + .post_box > span,
                    .post_box > span {
                        background-image: none !important;
                        box-shadow: none !important;
                    }
                    .content > .van-field,
                    .box .van-field,
                    .content .van-search,
                    .content .van-search__content {
                        background-color: #22262a !important;
                        border-color: rgba(255,255,255,.16) !important;
                        color: #f3f1ed !important;
                        box-shadow: none !important;
                    }
                    .content .van-search,
                    .search_box, .search_top, .search_con,
                    .hot_box, .hot_words, .hot_word, .hot_list, .search_hot {
                        background-color: #101214 !important;
                        background-image: none !important;
                    }
                    .hot_box span, .hot_words span, .hot_word span,
                    .hot_list span, .search_hot span,
                    .hot_box div, .hot_words div, .hot_word div,
                    .hot_list div, .search_hot div {
                        background-color: #272b2f !important;
                        border-color: rgba(255,255,255,.16) !important;
                        color: #f3f1ed !important;
                    }
                    .index_news, .index_news > div,
                    .index_news .van-tabs, .index_news .van-tabs__wrap,
                    .index_news .van-tabs__nav, .index_news .van-list,
                    .index_news .van-cell, .index_news li,
                    .index_news .item, .index_news [class$="_item"] {
                        background-color: #1a1d20 !important;
                        border-color: rgba(255,255,255,.13) !important;
                        color: #f3f1ed !important;
                    }
                    .index_news .van-tab, .index_news .van-tab__text {
                        background-color: transparent !important;
                        color: #c6c3be !important;
                    }
                    .index_news .van-tab--active,
                    .index_news .van-tab--active .van-tab__text {
                        color: #ff765f !important;
                    }
                    .index_news [class*="time"],
                    .index_news [class*="status"],
                    .index_news [class*="type"] {
                        background-color: rgba(255,255,255,.08) !important;
                        color: #f3f1ed !important;
                    }
                    .t_box, .t_box > div {
                        background-color: #1a1d20 !important;
                        background-image: none !important;
                        border-color: rgba(255,255,255,.16) !important;
                        color: #f3f1ed !important;
                        box-shadow: 0 0 18px rgba(0,0,0,.38) !important;
                    }
                    .t_box span, .t_box p, .t_box div {
                        background-color: transparent !important;
                        color: #f3f1ed !important;
                    }
                    .content_rule {
                        background-color: #1a1d20 !important;
                        background-image: none !important;
                        border-color: rgba(255,255,255,.13) !important;
                        color: #f3f1ed !important;
                    }
                    .content_rule p, .content_rule b, .content_rule span,
                    .content_rule div {
                        background-color: transparent !important;
                        color: #f3f1ed !important;
                    }
                    .content_rule img {
                        background-color: #f5f5f5 !important;
                        filter: none !important;
                    }
                    .box {
                        background-color: #1a1d20 !important;
                        background-image: none !important;
                        border-color: rgba(255,255,255,.13) !important;
                        box-shadow: 0 0 18px rgba(0,0,0,.32) !important;
                    }
                    .box h1, .box h2, .box h3,
                    .box p, .box span, .box label {
                        background-color: transparent !important;
                        color: #f3f1ed !important;
                    }
                    .types .type,
                    .content .type,
                    .content .van-button--default {
                        background-color: #272b2f !important;
                        background-image: none !important;
                        border-color: rgba(255,255,255,.18) !important;
                        color: #f3f1ed !important;
                    }
                    .types .type.ac,
                    .content .type.ac,
                    .content .van-button--primary {
                        background-color: #f26c1c !important;
                        border-color: #f26c1c !important;
                        color: #ffffff !important;
                    }
                    .box .img_box .image_icon,
                    .image_icon {
                        background-color: #22262a !important;
                        background-image: none !important;
                        border-color: rgba(255,255,255,.18) !important;
                    }
                    .box .img_box .image_icon span,
                    .image_icon span {
                        background-color: transparent !important;
                        color: #a9adb1 !important;
                    }
                    #div11 + .btns, #div1 + .btns,
                    #div11 + .btns > .theme_item,
                    #div1 + .btns > .theme_item {
                        background-color: transparent !important;
                        background-image: none !important;
                    }
                    #div11 + .btns > .theme_item > span,
                    #div1 + .btns > .theme_item > span,
                    #div11 + .btns > .theme_item > .sear_themesbox > .van-icon,
                    #div1 + .btns > .theme_item > .sear_themesbox > .van-icon {
                        background-color: transparent !important;
                        color: #f3f1ed !important;
                    }
                    .post_box > .van-button,
                    .content > .van-button,
                    .box + .van-button {
                        background-color: #f26c1c !important;
                        background-image: none !important;
                        border-color: #f26c1c !important;
                        color: #ffffff !important;
                        box-shadow: none !important;
                    }
                    .post_box > .van-button *,
                    .content > .van-button *,
                    .box + .van-button *,
                    .card_t .van-button *,
                    .list_wrap .van-button *,
                    .mine_box > .van-button *,
                    .content .top_r *, .content .top_rt *,
                    .card_con_text *, .card_con_reply_con * {
                        background-color: transparent !important;
                        background-image: none !important;
                    }
                    .card_con_text, .card_con_text *,
                    .card_con_reply_con, .card_con_reply_con * {
                        background-color: transparent !important;
                        background-image: none !important;
                        color: #f3f1ed !important;
                    }
                    .content, .data_main, .data_page,
                    .content > .head1, .content > .doll_box,
                    .content > .head1 + .doll_box {
                        background-image: none !important;
                    }
                    .data_main, .data_page, .data_page_item,
                    .base_items, .base_item, .doll_box,
                    .doll, .stage_item, .stage_item1,
                    .the_items, .the_item, .sm_box {
                        background-color: #1a1d20 !important;
                        border-color: rgba(255,255,255,.13) !important;
                        color: #f3f1ed !important;
                    }
                    .data_main h1, .data_main h2, .data_main h3, .data_main h4,
                    .data_main p, .data_main span,
                    .doll_box p, .doll_box span {
                        background-color: transparent !important;
                        color: #f3f1ed !important;
                    }
                    .data_main .data_item_head > div[data-html2canvas-ignore] {
                        display: inline-flex !important;
                        align-items: center !important;
                        justify-content: center !important;
                        min-width: .72rem !important;
                        height: .28rem !important;
                        padding: 0 .12rem !important;
                        border-radius: 999px !important;
                        background-color: #33383d !important;
                        background-image: none !important;
                        border: 1px solid rgba(255,255,255,.18) !important;
                        box-sizing: border-box !important;
                        box-shadow: none !important;
                    }
                    .data_main .data_item_head > div[data-html2canvas-ignore]::before {
                        content: "已隐藏" !important;
                        display: block !important;
                        background-color: transparent !important;
                        color: #c6c3be !important;
                        font-size: .12rem !important;
                        font-weight: 600 !important;
                        line-height: 1 !important;
                        white-space: nowrap !important;
                    }
                    .data_main .data_item_head > div[data-html2canvas-ignore].ac {
                        background-color: #f26c1c !important;
                        border-color: #f26c1c !important;
                    }
                    .data_main .data_item_head > div[data-html2canvas-ignore].ac::before {
                        content: "公开中" !important;
                        color: #ffffff !important;
                    }
                    .data_main .data_item_head > div[data-html2canvas-ignore] > span {
                        display: none !important;
                    }
                    .content .swiper-button-next,
                    .content .swiper-button-prev,
                    .content [class*="arrow"] {
                        background-color: rgba(255,255,255,.08) !important;
                        color: #f3f1ed !important;
                    }
                    .searc_box {
                        background-color: #101214 !important;
                        background-image: none !important;
                    }
                    .searc_box .van-search {
                        background-color: transparent !important;
                    }
                    .searc_box .van-search__content {
                        background-color: #22262a !important;
                        border: 1px solid rgba(255,255,255,.16) !important;
                    }
                    .searc_box > span {
                        background-color: transparent !important;
                        background-image: none !important;
                    }
                    .fire_box, .fire_items {
                        background-color: #1a1d20 !important;
                        background-image: none !important;
                    }
                    .fire_items .van-button {
                        background-color: #272b2f !important;
                        background-image: none !important;
                        border-color: rgba(255,255,255,.18) !important;
                        border-radius: 999px !important;
                        color: #f3f1ed !important;
                        overflow: hidden !important;
                        box-shadow: none !important;
                    }
                    .fire_items .van-button *,
                    .fire_items .van-button__text {
                        background-color: transparent !important;
                        background-image: none !important;
                        color: #f3f1ed !important;
                    }
                    .btns .theme_item .sear_themesbox {
                        display: flex !important;
                        align-items: center !important;
                        background-color: #22262a !important;
                        background-image: none !important;
                        border: 1px solid rgba(255,255,255,.24) !important;
                        box-sizing: border-box !important;
                        overflow: hidden !important;
                    }
                    .btns .theme_item .sear_themesbox > input {
                        flex: 1 1 auto !important;
                        min-width: 0 !important;
                        border: 0 !important;
                        outline: 0 !important;
                        background-color: transparent !important;
                        background-image: none !important;
                        box-shadow: none !important;
                    }
                    .btns .theme_item .sear_themesbox > .van-icon {
                        flex: 0 0 .36rem !important;
                        display: flex !important;
                        align-items: center !important;
                        justify-content: center !important;
                        background-color: transparent !important;
                        background-image: none !important;
                        color: #c6c3be !important;
                    }
                    .btns .theme_item .sear_themesbox > .van-icon::before {
                        background-color: transparent !important;
                    }
                    .btns .theme_item .sear_themesbox > ul {
                        background-color: #22262a !important;
                        background-image: none !important;
                    }
                    .btns .theme_item .sear_themesbox > ul * {
                        background-color: transparent !important;
                        background-image: none !important;
                    }
                    .content_main, .content_m12,
                    .strage_item, .strage_details_grid,
                    .strage_item .van-tabs,
                    .strage_item .van-tabs__wrap,
                    .strage_item .van-tabs__content,
                    .strage_item .van-tab__pane {
                        background-color: #1a1d20 !important;
                        background-image: none !important;
                        color: #f3f1ed !important;
                    }
                    .strage_item .van-tabs__nav {
                        background-color: transparent !important;
                        background-image: none !important;
                        gap: .1rem !important;
                    }
                    .strage_item .van-tabs__line {
                        display: none !important;
                    }
                    .strage_item .van-tab {
                        background-color: #272b2f !important;
                        background-image: none !important;
                        border: 1px solid rgba(255,255,255,.18) !important;
                        border-radius: 999px !important;
                        color: #f3f1ed !important;
                        flex: 0 0 auto !important;
                    }
                    .strage_item .van-tab--active {
                        background-color: #f26c1c !important;
                        border-color: #f26c1c !important;
                        color: #ffffff !important;
                    }
                    .strage_item .van-tab *,
                    .strage_item .detail_item p {
                        background-color: transparent !important;
                        background-image: none !important;
                    }
                    .pagination-wrap,
                    .pagination-wrap .van-pagination {
                        background-color: #101214 !important;
                        background-image: none !important;
                    }
                    .pagination-wrap .van-pagination__item {
                        background-color: #272b2f !important;
                        background-image: none !important;
                        border-color: rgba(255,255,255,.16) !important;
                        color: #f3f1ed !important;
                    }
                    .pagination-wrap .van-pagination__item--disabled {
                        background-color: #202428 !important;
                        color: #777c82 !important;
                    }
                    .pagination-wrap .van-pagination__page-desc {
                        background-color: transparent !important;
                        color: #f3f1ed !important;
                    }
                    html body .content .post_box > span:first-child {
                        background-color: #262a2e !important;
                        background-image: none !important;
                        border: 1px solid rgba(255,255,255,.16) !important;
                        color: #a9adb1 !important;
                        box-shadow: none !important;
                    }
                    html body .content .post_box > span:first-child * {
                        background-color: transparent !important;
                        background-image: none !important;
                    }
                    .user_box, .user_item, .user_item1 {
                        background-color: #1a1d20 !important;
                        background-image: none !important;
                        color: #f3f1ed !important;
                    }
                    .user_item input,
                    .user_box input {
                        background-color: transparent !important;
                        background-image: none !important;
                        border: 0 !important;
                        box-shadow: none !important;
                        color: #f3f1ed !important;
                    }
                    .user_item input::selection,
                    .user_box input::selection {
                        background-color: rgba(242,108,28,.35) !important;
                    }
                    .user_box > .van-button,
                    .head_box > .van-button {
                        background-color: #f26c1c !important;
                        background-image: none !important;
                        border-color: #f26c1c !important;
                        color: #ffffff !important;
                        box-shadow: none !important;
                    }
                    .user_box > .van-button *,
                    .head_box > .van-button *,
                    .user_box > .van-button .van-button__text,
                    .head_box > .van-button .van-button__text {
                        background-color: transparent !important;
                        background-image: none !important;
                        color: #ffffff !important;
                    }
                    .card_item .the_box .van-button,
                    .card_item .the_box .van-button--primary,
                    .card_item .the_box .theme_item {
                        background-color: #272b2f !important;
                        background-image: none !important;
                        border-color: #555b61 !important;
                        color: #f3f1ed !important;
                        box-shadow: none !important;
                    }
                    .card_item .the_box .van-button *,
                    .card_item .the_box .van-button__text,
                    .card_item .the_box .theme_item *,
                    .card_item .the_box span {
                        background-color: transparent !important;
                        background-image: none !important;
                        color: #f3f1ed !important;
                    }
                    .card_item .the_box .theme_item {
                        border-color: rgba(255,255,255,.28) !important;
                    }
                    .tab_box > span {
                        background-color: transparent !important;
                        color: #c6c3be !important;
                    }
                    .tab_box > span.ac {
                        color: #ff765f !important;
                    }
                    .card_item .img_box img, .card_item [class*="image-grid"] img,
                    .card_item [class*="img-grid"] img {
                        filter: brightness(.68) !important;
                        transition: filter 120ms linear;
                    }
                    .vel-modal img, .vel-modal .vel-img, .vel-img {
                        filter: none !important;
                    }
                `;
                if (!existing) document.head.appendChild(style);
                if (style.parentNode === document.head) document.head.appendChild(style);
                [80, 300, 900].forEach(function(delay) {
                    setTimeout(function() {
                        const latest = document.getElementById(styleId);
                        if (latest && latest.parentNode === document.head) document.head.appendChild(latest);
                    }, delay);
                });
                document.documentElement.style.colorScheme = 'dark';
                return true;
            })();
            """.trimIndent(),
            null
        )
    }

    private inner class NativeUiBridge {
        @JavascriptInterface
        fun openNativeSettings() {
            runOnUiThread {
                if (isRootUrl(webView.url)) this@MainActivity.openNativeSettings()
            }
        }

        @JavascriptInterface
        fun openBrowsingHistory() {
            runOnUiThread {
                if (isRootUrl(webView.url) && selectedTab == TAB_MINE) {
                    this@MainActivity.openBrowsingHistory()
                }
            }
        }

        @JavascriptInterface
        fun recordBrowsingHistory(
            topicIdValue: String,
            titleValue: String,
            authorValue: String,
            viewCountValue: String
        ) {
            runOnUiThread {
                val currentUrl = webView.url ?: return@runOnUiThread
                val currentUri =
                    runCatching { Uri.parse(currentUrl) }.getOrNull() ?: return@runOnUiThread
                if (!isThreadInfoUri(currentUri)) return@runOnUiThread

                val topicId =
                    topicIdValue.toLongOrNull()?.takeIf { it > 0L } ?: return@runOnUiThread
                if (currentUri.getQueryParameter("id")?.toLongOrNull() != topicId) {
                    return@runOnUiThread
                }
                val title = HistoryTopicFetcher.sanitizeText(titleValue, 240)
                val author = HistoryTopicFetcher.sanitizeText(authorValue, 80)
                val viewCount =
                    viewCountValue.toLongOrNull()?.coerceAtLeast(0L) ?: return@runOnUiThread
                if (title.isBlank() || author.isBlank()) return@runOnUiThread

                historyExecutor.execute {
                    BrowsingHistoryRepository.record(
                        applicationContext,
                        topicId,
                        title,
                        author,
                        viewCount,
                        currentUrl
                    )
                }
            }
        }

        @JavascriptInterface
        fun onHomepageLikeChanged(
            topicIdValue: String,
            titleValue: String,
            authorValue: String,
            isLiked: Boolean
        ) {
            runOnUiThread {
                if (!isRootUrl(webView.url) || selectedTab != TAB_HOME) return@runOnUiThread
                val topicId =
                    topicIdValue.toLongOrNull()?.takeIf { it > 0L } ?: return@runOnUiThread
                val title = HistoryTopicFetcher.sanitizeText(titleValue, 240)
                val author = HistoryTopicFetcher.sanitizeText(authorValue, 80)

                historyExecutor.execute {
                    if (!isLiked) {
                        BrowsingHistoryRepository.setLiked(applicationContext, topicId, false)
                        return@execute
                    }

                    val details = HistoryTopicFetcher.fetchTopicDetails(topicId, title, author)
                    BrowsingHistoryRepository.record(
                        applicationContext,
                        topicId,
                        details.title,
                        details.author,
                        details.viewCount,
                        "$HOME_URL_PREFIX/threadInfo?id=$topicId&hash_flag=1",
                        isLiked = true
                    )
                }
            }
        }

        @JavascriptInterface
        fun setImagePreviewVisible(visible: Boolean) {
            runOnUiThread {
                imagePreviewVisible = visible
                updateNativeChrome(webView.url)
            }
        }

        @JavascriptInterface
        fun onSignAuthTokenDetected(token: String) {
            if (token.isNotBlank() && token.length <= MAX_SIGN_AUTH_TOKEN_LENGTH) {
                AppPreferences.setSignAuthToken(applicationContext, token)
                enableScheduledSignInAfterFirstLogin()
            }
        }

        @JavascriptInterface
        fun onSignAuthTokenMissing() {
            AppPreferences.clearSignAuthToken(applicationContext)
        }

        @JavascriptInterface
        fun onAutoSignInResult(result: String) {
            runOnUiThread {
                autoSignInInFlight = false
                when (result) {
                    AutoSignInScript.RESULT_SUCCESS -> {
                        autoSignInResolvedForLaunch = true
                        Toast.makeText(
                            this@MainActivity,
                            R.string.auto_sign_in_success,
                            Toast.LENGTH_SHORT
                        ).show()
                        // 重新加载一次，使网页自身的“已签到”状态与服务端保持一致。
                        if (AppPreferences.isRefreshHomeAfterSignInEnabled(this@MainActivity)) {
                            webView.postDelayed(
                                {
                                    if (isRootUrl(webView.url)) {
                                        signRefreshNoticePending =
                                            !AppPreferences.isSignRefreshNoticeAcknowledged(
                                                this@MainActivity
                                            )
                                        webView.reload()
                                    }
                                },
                                AUTO_SIGN_RELOAD_DELAY_MS
                            )
                        }
                    }
                    AutoSignInScript.RESULT_NOT_LOGGED_IN -> {
                        AppPreferences.clearSignAuthToken(this@MainActivity)
                        // 登录成功回到根页后再尝试，不在 Android 侧处理登录态。
                        autoSignInResolvedForLaunch = false
                        if (!autoSignInLoggedOutNoticeShown) {
                            autoSignInLoggedOutNoticeShown = true
                            Toast.makeText(
                                this@MainActivity,
                                R.string.auto_sign_in_login_required,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    else -> autoSignInResolvedForLaunch = true
                }
            }
        }
    }

    private fun enableScheduledSignInAfterFirstLogin() {
        if (AppPreferences.isScheduledSignInAutoEnabledOnce(this)) return
        AppPreferences.setScheduledSignInEnabled(this, true)
        AppPreferences.markScheduledSignInAutoEnabledOnce(this)
        SignInScheduler.scheduleNext(this)
    }

    private fun attemptAutoSignIn() {
        if (!AppPreferences.isAutoSignInEnabled(this)) {
            autoSignInResolvedForLaunch = true
            return
        }
        if (autoSignInInFlight || autoSignInResolvedForLaunch || !isRootUrl(webView.url)) return
        autoSignInInFlight = true

        webView.evaluateJavascript(
            AutoSignInScript.build(JS_BRIDGE_NAME, "onAutoSignInResult"),
            null
        )
    }

    private fun syncSignAuthToken() {
        webView.evaluateJavascript(
            """
            (function() {
                const bridge = window.$JS_BRIDGE_NAME;
                if (!bridge) return;
                const token = localStorage.getItem('key') || '';
                if (token) {
                    bridge.onSignAuthTokenDetected(token);
                } else {
                    bridge.onSignAuthTokenMissing();
                }
            })();
            """.trimIndent(),
            null
        )
    }

    private fun showSignRefreshNoticeIfPending() {
        if (!signRefreshNoticePending || signRefreshNoticeShowing ||
            AppPreferences.isSignRefreshNoticeAcknowledged(this)
        ) {
            return
        }
        signRefreshNoticeShowing = true
        val dialog = AlertDialog.Builder(this)
            .setMessage(R.string.sign_refresh_notice_message)
            .setPositiveButton(R.string.permission_confirm) { _, _ ->
                AppPreferences.acknowledgeSignRefreshNotice(this)
                signRefreshNoticePending = false
            }
            .create()
        dialog.setOnDismissListener {
            signRefreshNoticeShowing = false
            if (signRefreshNoticeDialog === dialog) signRefreshNoticeDialog = null
        }
        signRefreshNoticeDialog = dialog
        dialog.show()
    }

    private fun injectHideWebNavCss() {
        if (!isRootUrl(webView.url)) return
        webView.evaluateJavascript(
            WebInjectionScripts.hideWebNavCss(),
            null
        )
    }

    private fun removeHideWebNavCss() {
        webView.evaluateJavascript(
            WebInjectionScripts.removeHideWebNavCss(),
            null
        )
    }

    private fun injectFeedImagePreloader() {
        if (!isRootUrl(webView.url)) return
        val preloadEnabled = AppPreferences.isFeedPreloadEnabled(this)
        val preloadScreens = AppPreferences.getFeedPreloadScreens(this)
        webView.evaluateJavascript(
            WebInjectionScripts.feedImagePreloader(preloadEnabled, preloadScreens),
            null
        )
    }

    private fun injectHomeSliderPaginationFix() {
        if (!isRootUrl(webView.url)) return
        webView.evaluateJavascript(
            WebInjectionScripts.homeSliderPaginationFix(),
            null
        )
    }

    private fun clickWebNavItem(tab: Int, attemptsRemaining: Int) {
        if (!isRootUrl(webView.url) || pendingRootTab != tab) return
        val webIndex = tab + 1
        val script = WebInjectionScripts.clickWebNavItem(webIndex)

        webView.evaluateJavascript(script) { result ->
            if (result == "true") {
                pendingRootTab = null
            } else if (attemptsRemaining > 1 && pendingRootTab == tab) {
                webView.postDelayed(
                    { clickWebNavItem(tab, attemptsRemaining - 1) },
                    CLICK_RETRY_DELAY_MS
                )
            } else {
                pendingRootTab = null
            }
        }
    }

    private fun showLoadError(url: String) {
        cancelMainFrameLoadWatchdog()
        resetWebViewVisualState()
        pageHasMainFrameError = true
        lastFailedUrl = url.takeIf { it.startsWith("https://") } ?: HOME_URL
        progressBar.visibility = View.GONE
        errorOverlay.visibility = View.VISIBLE
    }

    private fun scheduleMainFrameLoadWatchdog(url: String) {
        cancelMainFrameLoadWatchdog()
        val generation = ++mainFrameLoadGeneration
        val watchdog = Runnable {
            if (generation != mainFrameLoadGeneration || pageHasMainFrameError) return@Runnable
            verifyMainFrameHasVisibleContent(url, recoverIfBlank = true)
        }
        mainFrameLoadWatchdog = watchdog
        webView.postDelayed(watchdog, MAIN_FRAME_LOAD_TIMEOUT_MS)
    }

    private fun verifyMainFrameHasVisibleContent(
        expectedUrl: String,
        recoverIfBlank: Boolean = false
    ) {
        if (pageHasMainFrameError || webView.url != expectedUrl) return
        val generation = mainFrameLoadGeneration
        webView.evaluateJavascript(
            """
            (function() {
                const body = document.body;
                if (!body) return false;
                const app = document.querySelector('#app');
                const target = app || body;
                const rect = target.getBoundingClientRect();
                const hasLayout = rect.width > 0 && rect.height > 20;
                const hasContent = target.children.length > 0 ||
                    (target.innerText || '').trim().length > 0;
                return hasLayout && hasContent;
            })();
            """.trimIndent()
        ) { result ->
            if (generation != mainFrameLoadGeneration || webView.url != expectedUrl) {
                return@evaluateJavascript
            }
            if (result == "true") {
                cancelMainFrameLoadWatchdog()
                mainFrameAutomaticRetryCount = 0
                automaticRetryTargetUrl = null
                progressBar.visibility = View.GONE
                if (!pageHasMainFrameError) errorOverlay.visibility = View.GONE
            } else if (recoverIfBlank) {
                recoverFromBlankMainFrame(expectedUrl)
            }
        }
    }

    private fun recoverFromBlankMainFrame(url: String) {
        // Android 的 INTERNET 标记不代表链路真的可用；未通过系统验证时直接给出重试入口。
        if (!hasValidatedInternetConnection()) {
            showLoadError(url)
            return
        }
        if (mainFrameAutomaticRetryCount >= MAIN_FRAME_AUTOMATIC_RETRY_LIMIT) {
            showLoadError(url)
            return
        }

        mainFrameAutomaticRetryCount++
        automaticRetryTargetUrl = url
        resetWebViewVisualState()
        webView.stopLoading()
        webView.loadUrl(url)
    }

    private fun cancelMainFrameLoadWatchdog() {
        mainFrameLoadWatchdog?.let { webView.removeCallbacks(it) }
        mainFrameLoadWatchdog = null
    }

    private fun resetWebViewVisualState() {
        webView.animate().cancel()
        webView.alpha = 1f
        webView.translationX = 0f
        officialFeatureTransitionPending = false
        threadForwardTransitionPending = false
        threadForwardTransitionGeneration++
    }

    private fun hasValidatedInternetConnection(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun openExternalUrl(uri: Uri) {
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme !in setOf("http", "https", "mailto", "tel")) {
            Toast.makeText(this, R.string.cannot_open_link, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.cannot_open_link, Toast.LENGTH_SHORT).show()
        }
    }

    private fun acceptsImages(): Boolean {
        if (pendingAcceptTypes.isEmpty()) return false
        return pendingAcceptTypes.any { it == "image/*" || it.startsWith("image/") }
    }

    private fun launchFileChooser() {
        val contentIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = when {
                pendingAcceptTypes.size == 1 -> pendingAcceptTypes.first()
                else -> "*/*"
            }
            if (pendingAcceptTypes.size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, pendingAcceptTypes)
            }
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, pendingAllowMultiple)
        }

        val initialIntents = mutableListOf<Intent>()
        if (acceptsImages() && hasPermission(Manifest.permission.CAMERA)) {
            createCameraIntent()?.let(initialIntents::add)
        }

        val chooser = Intent.createChooser(contentIntent, getString(R.string.choose_upload_source)).apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents.toTypedArray())
        }

        try {
            fileChooserLauncher.launch(chooser)
        } catch (_: ActivityNotFoundException) {
            filePathCallback?.onReceiveValue(null)
            clearFileChooserState()
            Toast.makeText(this, R.string.cannot_open_link, Toast.LENGTH_SHORT).show()
        }
    }

    private fun createCameraIntent(): Intent? {
        val uploadDirectory = File(cacheDir, "webview_uploads").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val photoFile = File.createTempFile("IMG_${timestamp}_", ".jpg", uploadDirectory)
        val outputUri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            photoFile
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
            clipData = ClipData.newRawUri("camera-output", outputUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        if (intent.resolveActivity(packageManager) == null) return null
        cameraImageUri = outputUri
        return intent
    }

    private fun handleFileChooserResult(resultCode: Int, data: Intent?) {
        val result = when {
            resultCode != RESULT_OK -> null
            data?.data != null || data?.clipData != null ->
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            cameraImageUri != null -> arrayOf(cameraImageUri!!)
            else -> null
        }
        filePathCallback?.onReceiveValue(result)
        clearFileChooserState()
    }

    private fun clearFileChooserState() {
        filePathCallback = null
        pendingAcceptTypes = emptyArray()
        pendingAllowMultiple = false
        cameraImageUri = null
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission() =
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun isTrustedOrigin(origin: String): Boolean {
        val uri = runCatching { Uri.parse(origin) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host.equals(TARGET_HOST, ignoreCase = true)
    }

    private fun finishGeolocationRequest(granted: Boolean) {
        geolocationCallback?.invoke(geolocationOrigin, granted, false)
        geolocationOrigin = null
        geolocationCallback = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        outState.putInt(STATE_SELECTED_TAB, selectedTab)
        super.onSaveInstanceState(outState)
    }

    private fun configureBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBackNavigation()
        })
    }

    private fun handleBackNavigation() {
        if (threadForwardTransitionPending) {
            resetWebViewVisualState()
        }
        if (returnToNativeSettingsPending && officialFeatureReached) {
            returnToNativeSettings(resetWebViewToHome = true)
            return
        }
        if (webView.canGoBack()) {
            errorOverlay.visibility = View.GONE
            webView.goBack()
        } else {
            finishAfterTransition()
        }
    }

    override fun onDestroy() {
        cancelMainFrameLoadWatchdog()
        hideMobileDataWarningRunnable?.let { mobileDataWarningToast.removeCallbacks(it) }
        signRefreshNoticeDialog?.dismiss()
        signRefreshNoticeDialog = null
        filePathCallback?.onReceiveValue(null)
        finishGeolocationRequest(false)
        historyExecutor.shutdownNow()
        webView.stopLoading()
        webView.removeJavascriptInterface(JS_BRIDGE_NAME)
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN_URL = "open_url"
        const val EXTRA_ALLOW_OFFICIAL_SETTINGS = "allow_official_settings"
        const val EXTRA_RETURN_TO_NATIVE_SETTINGS = "return_to_native_settings"

        private const val AUTO_SIGN_RELOAD_DELAY_MS = 500L

        private const val TAB_HOME = 0
        private const val TAB_FOLLOW = 1
        private const val TAB_PUBLISH = 2
        private const val TAB_MESSAGES = 3
        private const val TAB_MINE = 4

        private const val CLICK_RETRY_COUNT = 8
        private const val CLICK_RETRY_DELAY_MS = 250L
        private const val MAIN_FRAME_LOAD_TIMEOUT_MS = 12_000L
        private const val MAIN_FRAME_RENDER_CHECK_DELAY_MS = 400L
        private const val MAIN_FRAME_AUTOMATIC_RETRY_LIMIT = 1
        private const val WEB_TRANSITION_DISTANCE_RATIO = 0.04f
        private const val WEB_TRANSITION_DIM_ALPHA = 0.72f
        private const val SETTINGS_TRANSITION_DURATION_MS = 240L
        private const val THREAD_TRANSITION_DIM_ALPHA = 0.82f
        private const val THREAD_TRANSITION_FADE_OUT_MS = 110L
        private const val THREAD_TRANSITION_LOAD_DELAY_MS = 70L
        private const val THREAD_TRANSITION_FADE_IN_MS = 160L
        private const val MOBILE_DATA_WARNING_VISIBLE_MS = 3500L
        private const val MAX_SIGN_AUTH_TOKEN_LENGTH = 8_192
        private const val STATE_SELECTED_TAB = "selected_tab"
    }

}
