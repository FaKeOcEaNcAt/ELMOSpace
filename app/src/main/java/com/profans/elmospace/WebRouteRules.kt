package com.profans.elmospace

import android.net.Uri
import com.profans.elmospace.WebConstants.TARGET_HOST

object WebRouteRules {
    private val mobileRoutePaths = setOf(
        "/category",
        "/collectionList",
        "/dolls",
        "/draft",
        "/edit",
        "/exchange",
        "/fans",
        "/favor",
        "/feedback",
        "/follows",
        "/introduction",
        "/loading",
        "/login",
        "/otherData",
        "/points",
        "/points_log",
        "/points_rule",
        "/priSet",
        "/release",
        "/search",
        "/set",
        "/share",
        "/themeInfo",
        "/threadInfo",
        "/topic_follows",
        "/user",
        "/userData",
        "/userInfo"
    )

    fun isInternalMobileUri(uri: Uri): Boolean {
        if (uri.scheme != "https" || !uri.host.equals(TARGET_HOST, ignoreCase = true)) return false
        val path = uri.path.orEmpty()
        return path == "/m" || path.startsWith("/m/")
    }

    fun isInternalAppUri(uri: Uri) = isInternalMobileUri(uri)

    fun normalizeInternalNavigationUri(uri: Uri): Uri? {
        if (uri.scheme !in setOf("http", "https") ||
            !uri.host.equals(TARGET_HOST, ignoreCase = true)
        ) {
            return null
        }

        val path = uri.path.orEmpty()
        return when {
            isInternalMobileUri(uri) -> uri.buildUpon().scheme("https").build()
            path in mobileRoutePaths -> uri.buildUpon()
                .scheme("https")
                .path("/m$path")
                .build()
            path == "/wikiMobile" || path.startsWith("/wikiMobile/") -> uri.buildUpon()
                .scheme("https")
                .path(if (path == "/wikiMobile") "/wikiMobile/" else path)
                .build()
            else -> null
        }
    }

    fun isRootUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return uri.scheme == "https" &&
            uri.host.equals(TARGET_HOST, ignoreCase = true) &&
            (uri.path == "/m" || uri.path == "/m/")
    }

    fun isThreadInfoUri(uri: Uri): Boolean =
        uri.scheme == "https" &&
            uri.host.equals(TARGET_HOST, ignoreCase = true) &&
            (uri.path == "/m/threadInfo" || uri.path == "/m/threadInfo/")

}
