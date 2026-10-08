package com.profans.elmospace

import android.content.Intent
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TabletParallelBrowsingTest {
    @Test
    fun homePostsOpenInDetailAndCloseWithoutStaleInterception() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val activity = instrumentation.startActivitySync(intent) as MainActivity
        val previousSetting = AppPreferences.isParallelBrowsingEnabled(activity)
        val webView = activity.findViewById<WebView>(R.id.webView)
        val controllerField = MainActivity::class.java.getDeclaredField("tabletParallelBrowser")
            .apply { isAccessible = true }
        val controller = controllerField.get(activity) as TabletParallelBrowserController

        try {
            assertTrue(WindowLayout.isTabletLandscapeLayout(activity))
            val fixtureUrl = "https://gf2-bbs.exiliumgf.com/m/?tablet_fixture=1"
            val fixture = """
                <html><body>
                  <div class="index_news">
                    <div id="pinned" class="index_news_item">
                      <a href="/m/threadInfo?id=12346">置顶公告</a>
                    </div>
                  </div>
                  <div id="regular" class="card_item">
                    <div class="card_m">普通贴文</div>
                    <a href="/m/threadInfo?id=12345">详情链接</a>
                  </div>
                </body></html>
            """.trimIndent()
            instrumentation.runOnMainSync {
                AppPreferences.setParallelBrowsingEnabled(activity, true)
                webView.stopLoading()
                val originalClient = webView.webViewClient
                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        if (request.isForMainFrame && request.url.toString() == fixtureUrl) {
                            return WebResourceResponse(
                                "text/html", "UTF-8",
                                ByteArrayInputStream(fixture.toByteArray(Charsets.UTF_8))
                            )
                        }
                        return originalClient.shouldInterceptRequest(view, request)
                    }

                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        originalClient.onPageStarted(view, url, favicon)
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        originalClient.onPageFinished(view, url)
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                        originalClient.doUpdateVisitedHistory(view, url, isReload)
                    }
                }
                webView.loadUrl(fixtureUrl)
            }
            waitUntil { evaluate(webView, "!!document.querySelector('#regular')") == "true" }
            val currentUrl = onMain { webView.url }
            assertTrue(
                "Fixture URL was $currentUrl; location=${evaluate(webView, "location.href")}",
                WebRouteRules.isRootUrl(currentUrl)
            )
            assertTrue(WindowLayout.hasParallelBrowsingSpace(activity))
            waitUntil { evaluate(webView, "!!window.__androidTabletThreadSplitHandler") == "true" }

            evaluate(webView, "document.querySelector('#regular .card_m').click()")
            waitUntil { onMain { controller.isDetailVisible() } }
            instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
            waitUntil { onMain { !controller.hasDetailWebView() } }
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, onMain { webView.layoutParams.width })

            evaluate(webView, "document.querySelector('#pinned').click()")
            waitUntil { onMain { controller.isDetailVisible() } }
            instrumentation.runOnMainSync { activity.findViewById<android.view.View>(R.id.navFollow).performClick() }
            waitUntil { onMain { !controller.hasDetailWebView() } }
            waitUntil { evaluate(webView, "!!window.__androidTabletThreadSplitHandler") == "false" }
            assertFalse(onMain { controller.isDetailVisible() })
        } finally {
            instrumentation.runOnMainSync {
                AppPreferences.setParallelBrowsingEnabled(activity, previousSetting)
                activity.finish()
            }
        }
    }

    private fun evaluate(webView: WebView, script: String): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val latch = CountDownLatch(1)
        var result = ""
        instrumentation.runOnMainSync {
            webView.evaluateJavascript(script) {
                result = it
                latch.countDown()
            }
        }
        assertTrue("JavaScript callback timed out", latch.await(5, TimeUnit.SECONDS))
        return result
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        assertTrue("Timed out waiting for tablet UI state", condition())
    }
}
