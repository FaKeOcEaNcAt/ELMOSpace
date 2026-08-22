package com.profans.elmospace

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat

internal class TabletParallelBrowserController(
    private val context: Context,
    private val contentFrame: FrameLayout,
    private val masterWebView: WebView,
    private val progressBar: View,
    private val errorOverlay: View,
    private val bridgeName: String,
    private val isActive: () -> Boolean,
    private val configureWebViewSettings: (WebView) -> Unit,
    private val createDetailWebViewClient: () -> WebViewClient,
    private val createDetailWebChromeClient: () -> WebChromeClient,
    private val createBridge: () -> Any
) {
    private var detailPane: LinearLayout? = null
    var detailWebView: WebView? = null
        private set

    private val layoutChangeListener =
        View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyContentWidth() }

    fun attachLayoutListener() {
        if (!WindowLayout.isTabletLayout(context)) return
        contentFrame.addOnLayoutChangeListener(layoutChangeListener)
        contentFrame.post { applyContentWidth() }
    }

    fun setupIfNeeded() {
        if (!isActive() || detailPane != null) return

        val pane = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setBackgroundColor(ContextCompat.getColor(context, R.color.app_background))
        }
        val divider = View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.nav_divider))
        }
        val detailWeb = WebView(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.app_background))
        }

        configureWebViewSettings(detailWeb)
        CookieManager.getInstance().setAcceptThirdPartyCookies(detailWeb, true)
        detailWeb.webViewClient = createDetailWebViewClient()
        detailWeb.webChromeClient = createDetailWebChromeClient()
        detailWeb.addJavascriptInterface(createBridge(), bridgeName)

        pane.addView(
            divider,
            LinearLayout.LayoutParams(1.dp(), ViewGroup.LayoutParams.MATCH_PARENT)
        )
        pane.addView(
            detailWeb,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        )
        contentFrame.addView(
            pane,
            FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END)
        )

        detailPane = pane
        detailWebView = detailWeb
        contentFrame.post { applyContentWidth() }
    }

    fun hasDetailWebView(): Boolean = detailWebView != null

    fun isDetailVisible(): Boolean =
        detailPane?.visibility == View.VISIBLE && detailWebView != null

    fun open(url: String) {
        setupIfNeeded()
        val pane = detailPane ?: return
        val detailWeb = detailWebView ?: return

        masterWebView.animate().cancel()
        masterWebView.alpha = 1f
        masterWebView.translationX = 0f

        if (pane.visibility != View.VISIBLE) {
            pane.visibility = View.VISIBLE
            pane.alpha = 0f
            pane.translationX = DETAIL_ENTER_OFFSET_DP.dp().toFloat()
            applyContentWidth()
            pane.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(DETAIL_ENTER_DURATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            applyContentWidth()
        }
        detailWeb.loadUrl(url)
    }

    fun close(clearContent: Boolean = true) {
        val pane = detailPane ?: return
        val detailWeb = detailWebView ?: return
        if (pane.visibility != View.VISIBLE) return
        pane.animate().cancel()
        pane.visibility = View.GONE
        if (clearContent) {
            detailWeb.stopLoading()
            detailWeb.loadUrl("about:blank")
        }
        applyContentWidth()
    }

    fun closeIfReturnedToMaster(url: String?, isMasterUrl: (String?) -> Boolean): Boolean {
        if (!isDetailVisible()) return false
        if (url == "about:blank" || isMasterUrl(url)) {
            close()
            return true
        }
        return false
    }

    fun handleBack(shouldCloseForUri: (android.net.Uri?) -> Boolean): Boolean {
        if (!isDetailVisible()) return false
        val detailWeb = detailWebView ?: return false
        val detailUri = runCatching {
            android.net.Uri.parse(detailWeb.url ?: "")
        }.getOrNull()
        if (shouldCloseForUri(detailUri)) {
            close()
        } else if (detailWeb.canGoBack()) {
            detailWeb.goBack()
        } else {
            close()
        }
        return true
    }

    fun closeAndDestroy() {
        val pane = detailPane
        val detailWeb = detailWebView
        if (pane != null) {
            pane.animate().cancel()
            contentFrame.removeView(pane)
        }
        detailWeb?.let {
            it.stopLoading()
            it.removeJavascriptInterface(bridgeName)
            it.webChromeClient = null
            it.webViewClient = WebViewClient()
            it.destroy()
        }
        detailPane = null
        detailWebView = null
        applyContentWidth()
    }

    fun destroy() {
        contentFrame.removeOnLayoutChangeListener(layoutChangeListener)
        closeAndDestroy()
    }

    fun applyContentWidth() {
        if (!WindowLayout.isTabletLayout(context)) return
        val availableWidth = contentFrame.width -
            contentFrame.paddingStart -
            contentFrame.paddingEnd
        if (availableWidth <= 0) return

        val detailVisible = isActive() && isDetailVisible()
        val masterWidthRatio =
            context.resources.getInteger(R.integer.tablet_parallel_master_width_percent) / 100f
        val masterMinWidth =
            context.resources.getDimensionPixelSize(R.dimen.tablet_parallel_master_min_width)
        val detailMinWidth =
            context.resources.getDimensionPixelSize(R.dimen.tablet_parallel_detail_min_width)
        val mainWidth = if (detailVisible) {
            val maxMainWidth = (availableWidth - detailMinWidth).coerceAtLeast(masterMinWidth)
            (availableWidth * masterWidthRatio).toInt()
                .coerceIn(masterMinWidth, maxMainWidth)
        } else {
            ViewGroup.LayoutParams.MATCH_PARENT
        }
        val detailWidth = if (detailVisible) {
            (availableWidth - mainWidth).coerceAtLeast(0)
        } else {
            0
        }

        listOf(masterWebView, progressBar, errorOverlay).forEach { view ->
            val params = view.layoutParams as? FrameLayout.LayoutParams ?: return@forEach
            if (params.width == mainWidth && params.gravity == Gravity.START) {
                return@forEach
            }
            params.width = mainWidth
            params.gravity = Gravity.START
            view.layoutParams = params
        }
        detailPane?.let { pane ->
            val params = pane.layoutParams as? FrameLayout.LayoutParams ?: return@let
            if (params.width != detailWidth || params.gravity != Gravity.END) {
                params.width = detailWidth
                params.gravity = Gravity.END
                pane.layoutParams = params
            }
        }
    }

    private fun Int.dp() = (this * context.resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        private const val DETAIL_ENTER_OFFSET_DP = 24
        private const val DETAIL_ENTER_DURATION_MS = 240L
    }
}
