package io.github.admin0330.liquidmusic.feature.home

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import io.github.admin0330.liquidmusic.BuildConfig
import io.github.admin0330.liquidmusic.core.designsystem.components.liquidClickable
import io.github.admin0330.liquidmusic.core.designsystem.glass.LiquidGlassSurface
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidMotion
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidSpacing

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HomeScreen(
    bottomPadding: Dp,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val savedWebViewState = rememberSaveable { Bundle() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by rememberSaveable { mutableStateOf(false) }
    var pageProgress by rememberSaveable { mutableIntStateOf(0) }
    var pageError by rememberSaveable { mutableStateOf<String?>(null) }

    val openExternal: (String) -> Unit = remember(context) {
        { rawUrl ->
            if (BlogNavigationPolicy.canOpenExternally(rawUrl)) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, rawUrl.toUri()))
                }
            }
        }
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(top = LiquidSpacing.xs),
    ) {
        BlogToolbar(
            canGoBack = canGoBack,
            onBack = { webView?.goBack() },
            onRefresh = {
                pageError = null
                webView?.reload()
            },
            onOpenSettings = onOpenSettings,
            modifier = Modifier.padding(horizontal = LiquidSpacing.screen),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = LiquidSpacing.xs, bottom = bottomPadding),
        ) {
            AndroidView(
                factory = { viewContext ->
                    WebView(viewContext).apply webView@{
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            safeBrowsingEnabled = true
                            mediaPlaybackRequiresUserGesture = true
                            javaScriptCanOpenWindowsAutomatically = false
                            setSupportMultipleWindows(false)
                            setSupportZoom(false)
                            builtInZoomControls = false
                            displayZoomControls = false
                            textZoom = 100
                        }
                        CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(this@webView, false)
                        }
                        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                pageError = null
                                pageProgress = 0
                                canGoBack = view.canGoBack()
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                pageProgress = 100
                                canGoBack = view.canGoBack()
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val rawUrl = request.url.toString()
                                if (BlogNavigationPolicy.shouldRenderInsideApp(rawUrl)) return false
                                openExternal(rawUrl)
                                return true
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (request.isForMainFrame) {
                                    pageError = error.description?.toString()?.takeIf(String::isNotBlank)
                                        ?: "博客暂时无法加载"
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView,
                                request: WebResourceRequest,
                                errorResponse: WebResourceResponse,
                            ) {
                                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                                    pageError = "博客返回了 ${errorResponse.statusCode}，请稍后重试"
                                }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageProgress = newProgress.coerceIn(0, 100)
                            }
                        }

                        val restored = if (savedWebViewState.isEmpty) {
                            null
                        } else {
                            restoreState(savedWebViewState)
                        }
                        if (restored == null) loadUrl(BlogNavigationPolicy.BLOG_URL)
                    }.also { webView = it }
                },
                update = { webView = it },
                onRelease = { releasedView ->
                    releasedView.saveState(savedWebViewState)
                    releasedView.stopLoading()
                    releasedView.webChromeClient = null
                    releasedView.webViewClient = WebViewClient()
                    releasedView.destroy()
                    if (webView === releasedView) webView = null
                },
                modifier = Modifier.fillMaxSize(),
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = pageProgress in 0..99 && pageError == null,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(LiquidMotion.quick)),
                exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(LiquidMotion.quick)),
            ) {
                LinearProgressIndicator(
                    progress = { pageProgress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = pageError != null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = LiquidSpacing.screen),
                enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(LiquidMotion.standard)),
                exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(LiquidMotion.quick)),
            ) {
                BlogErrorCard(
                    message = pageError ?: "博客暂时无法加载",
                    onRetry = {
                        pageError = null
                        webView?.loadUrl(BlogNavigationPolicy.BLOG_URL)
                    },
                )
            }
        }
    }
}

@Composable
private fun BlogToolbar(
    canGoBack: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassSurface(
        modifier = modifier.fillMaxWidth().height(56.dp),
        cornerRadius = 28.dp,
        blurRadius = 30.dp,
        opacity = 0.62f,
        elevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LiquidSpacing.xxs),
        ) {
            BlogToolbarButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                label = "博客后退",
                enabled = canGoBack,
                onClick = onBack,
            )
            Text(
                text = "Ym1r World",
                modifier = Modifier.padding(start = LiquidSpacing.xs),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            BlogToolbarButton(
                icon = Icons.Rounded.Refresh,
                label = "刷新博客",
                onClick = onRefresh,
            )
            BlogToolbarButton(
                icon = Icons.Rounded.Settings,
                label = "设置",
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun BlogToolbarButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .liquidClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.88f else 0.32f),
        )
    }
}

@Composable
private fun BlogErrorCard(message: String, onRetry: () -> Unit) {
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        blurRadius = 30.dp,
        opacity = 0.72f,
        elevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(LiquidSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LiquidSpacing.sm),
        ) {
            Text(
                text = "无法打开 Ym1r World",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(CircleShape)
                    .semantics {
                        role = Role.Button
                        contentDescription = "重新加载博客"
                    }
                    .liquidClickable(onClick = onRetry)
                    .padding(horizontal = LiquidSpacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "重新加载",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
