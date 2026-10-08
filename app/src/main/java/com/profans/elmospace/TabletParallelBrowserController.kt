package com.profans.elmospace

import android.content.Context
import android.net.Uri
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
import android.widget.TextView
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
    private var detailErrorOverlay: View? = null
    private var entryTopicId: String? = null
    private var lastRequestedUrl: String? = null
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
        val detailContent = FrameLayout(context)
        val errorOverlay = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            isClickable = true
            setBackgroundColor(ContextCompat.getColor(context, R.color.app_background))
        }
        errorOverlay.addView(TextView(context).apply {
            setText(R.string.page_load_failed)
            setTextColor(ContextCompat.getColor(context, R.color.error_text))
            textSize = 22f
        })
        errorOverlay.addView(TextView(context).apply {
            setText(R.string.page_load_failed_detail)
            setTextColor(ContextCompat.getColor(context, R.color.nav_unselected))
            textSize = 14f
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 8.dp() })
        errorOverlay.addView(TextView(context).apply {
            setText(R.string.retry)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            setBackgroundResource(R.drawable.bg_retry_button)
            setPadding(24.dp(), 12.dp(), 24.dp(), 12.dp())
            setOnClickListener { lastRequestedUrl?.let(detailWeb::loadUrl) }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 24.dp() })
        detailContent.addView(detailWeb, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        detailContent.addView(errorOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

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
            detailContent,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        )
        contentFrame.addView(
            pane,
            FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END)
        )

        detailPane = pane
        detailWebView = detailWeb
        detailErrorOverlay = errorOverlay
        contentFrame.post { applyContentWidth() }
    }

    fun hasDetailWebView(): Boolean = detailWebView != null

    fun isDetailVisible(): Boolean =
        detailPane?.visibility == View.VISIBLE && detailWebView != null

    fun open(url: String) {
        setupIfNeeded()
        val pane = detailPane ?: return
        val detailWeb = detailWebView ?: return
        entryTopicId = Uri.parse(url).getQueryParameter("id")
        lastRequestedUrl = url
        detailErrorOverlay?.visibility = View.GONE

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

    fun close() {
        closeAndDestroy()
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
        val detailUri = runCatching { Uri.parse(detailWeb.url ?: "") }.getOrNull()
        if (shouldCloseForUri(detailUri) ||
            (detailUri?.path == "/m/threadInfo" &&
                detailUri.getQueryParameter("id") == entryTopicId)
        ) {
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
        detailPane = null
        detailWebView = null
        detailErrorOverlay = null
        entryTopicId = null
        lastRequestedUrl = null
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
        applyContentWidth()
    }

    fun hideLoadError() {
        detailErrorOverlay?.visibility = View.GONE
    }

    fun showLoadError(url: String?) {
        if (!isDetailVisible() || url != detailWebView?.url) return
        detailErrorOverlay?.visibility = View.VISIBLE
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
