package com.profans.elmospace

import android.content.Context
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.util.concurrent.Executor

object AppNetworkProxy {
    fun applyWebViewProxyPolicy(context: Context) {
        if (!isWebViewProxyOverrideSupported()) return

        val executor = Executor { command -> command.run() }
        val listener = Runnable {}
        if (AppPreferences.isUseSystemProxyEnabled(context)) {
            ProxyController.getInstance().clearProxyOverride(executor, listener)
        } else {
            val config = ProxyConfig.Builder()
                .addDirect()
                .build()
            ProxyController.getInstance().setProxyOverride(config, executor, listener)
        }
    }

    fun openHttpConnection(context: Context, url: String): HttpURLConnection =
        openHttpConnection(context, URL(url))

    fun openHttpConnection(context: Context, url: URL): HttpURLConnection {
        val connection = if (AppPreferences.isUseSystemProxyEnabled(context)) {
            url.openConnection()
        } else {
            url.openConnection(Proxy.NO_PROXY)
        }
        return connection as HttpURLConnection
    }

    fun isWebViewProxyOverrideSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
}
