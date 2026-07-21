package com.profans.elmospace

object WebConstants {
    const val TARGET_HOST = "gf2-bbs.exiliumgf.com"
    const val SITE_ORIGIN = "https://gf2-bbs.exiliumgf.com"
    const val API_ORIGIN = "https://gf2-bbs-api.exiliumgf.com"
    const val HOME_URL = "https://gf2-bbs.exiliumgf.com/m/"
    const val HOME_URL_PREFIX = "https://gf2-bbs.exiliumgf.com/m"
    const val RELEASE_URL = "https://gf2-bbs.exiliumgf.com/m/release"
    const val SIGN_STATUS_URL =
        "$API_ORIGIN/community/task/get_current_sign_in_status"
    const val SIGN_IN_URL = "$API_ORIGIN/community/task/sign_in"
    const val MEMBER_INFO_URL = "$API_ORIGIN/community/member/info"
    const val TOPIC_DETAIL_API_URL = "$API_ORIGIN/community/topic"
    const val JS_BRIDGE_NAME = "AndroidShell"

    fun siteUrl(path: String): String {
        val normalizedPath = path.takeIf { it.startsWith("/") } ?: "/$path"
        return SITE_ORIGIN + normalizedPath
    }
}
