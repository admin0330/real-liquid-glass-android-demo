package io.github.admin0330.liquidmusic.feature.home

import java.net.URI

internal object BlogNavigationPolicy {
    const val BLOG_URL = "https://ym3861.cn/blog"

    private val embeddedHosts = setOf("ym3861.cn", "www.ym3861.cn")

    fun shouldRenderInsideApp(rawUrl: String): Boolean {
        val uri = parse(rawUrl) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host?.lowercase() in embeddedHosts
    }

    fun canOpenExternally(rawUrl: String): Boolean {
        val scheme = parse(rawUrl)?.scheme ?: return false
        return scheme.equals("https", ignoreCase = true) || scheme.equals("http", ignoreCase = true)
    }

    private fun parse(rawUrl: String): URI? = runCatching { URI(rawUrl) }.getOrNull()
}
