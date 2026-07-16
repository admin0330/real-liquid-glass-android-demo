package io.github.admin0330.liquidmusic.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlogNavigationPolicyTest {
    @Test
    fun `renders only the https blog hosts inside the app`() {
        assertTrue(BlogNavigationPolicy.shouldRenderInsideApp("https://ym3861.cn/blog"))
        assertTrue(BlogNavigationPolicy.shouldRenderInsideApp("https://www.ym3861.cn/posts/1"))
        assertFalse(BlogNavigationPolicy.shouldRenderInsideApp("http://ym3861.cn/blog"))
        assertFalse(BlogNavigationPolicy.shouldRenderInsideApp("https://ym3861.cn.example.com/blog"))
        assertFalse(BlogNavigationPolicy.shouldRenderInsideApp("https://github.com/admin0330"))
    }

    @Test
    fun `opens only ordinary web links externally`() {
        assertTrue(BlogNavigationPolicy.canOpenExternally("https://github.com/admin0330"))
        assertTrue(BlogNavigationPolicy.canOpenExternally("http://example.com"))
        assertFalse(BlogNavigationPolicy.canOpenExternally("intent://example"))
        assertFalse(BlogNavigationPolicy.canOpenExternally("file:///sdcard/secret"))
        assertFalse(BlogNavigationPolicy.canOpenExternally("javascript:alert(1)"))
    }
}
