package io.github.admin0330.liquidmusic.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.admin0330.liquidmusic.BuildConfig
import io.github.admin0330.liquidmusic.core.designsystem.components.liquidClickable
import io.github.admin0330.liquidmusic.core.designsystem.glass.LiquidGlassSurface
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidSpacing
import io.github.admin0330.liquidmusic.core.preferences.AppearanceMode
import io.github.admin0330.liquidmusic.core.preferences.DefaultRepeatMode
import io.github.admin0330.liquidmusic.update.model.UpdateState
import io.github.admin0330.liquidmusic.update.model.UpdateFailureCode

@Composable
fun SettingsScreen(
    bottomPadding: Dp,
    onBack: () -> Unit,
    onRescan: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val usbAudio by viewModel.usbAudioState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel, context) {
        viewModel.launchIntent.collect { intent -> context.startActivity(intent) }
    }
    LazyColumn(
        modifier = modifier.statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = LiquidSpacing.screen,
            end = LiquidSpacing.screen,
            top = LiquidSpacing.lg,
            bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(LiquidSpacing.md),
    ) {
        item(key = "settings-title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).liquidClickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "返回")
                }
                Text("设置", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = LiquidSpacing.xs))
            }
        }
        item(key = "appearance-heading") { SettingsHeading("外观") }
        item(key = "appearance-card") {
            LiquidGlassSurface(Modifier.fillMaxWidth(), opacity = 0.34f, elevation = 4.dp) {
                Column(Modifier.fillMaxWidth().padding(LiquidSpacing.sm)) {
                    AppearanceMode.entries.forEach { mode ->
                        ChoiceRow(
                            title = when (mode) { AppearanceMode.SYSTEM -> "跟随系统"; AppearanceMode.LIGHT -> "浅色"; AppearanceMode.DARK -> "深色" },
                            selected = preferences.appearance == mode,
                            onClick = { viewModel.setAppearance(mode) },
                        )
                    }
                }
            }
        }
        item(key = "playback-heading") { SettingsHeading("播放") }
        item(key = "playback-card") {
            LiquidGlassSurface(Modifier.fillMaxWidth(), opacity = 0.34f, elevation = 4.dp) {
                Column(Modifier.fillMaxWidth().padding(LiquidSpacing.sm)) {
                    ToggleRow("默认随机播放", preferences.defaultShuffle, viewModel::setShuffle)
                    Text("默认循环模式", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = LiquidSpacing.sm, vertical = LiquidSpacing.xs))
                    DefaultRepeatMode.entries.forEach { mode ->
                        ChoiceRow(
                            title = when (mode) { DefaultRepeatMode.OFF -> "关闭"; DefaultRepeatMode.ALL -> "全部循环"; DefaultRepeatMode.ONE -> "单曲循环" },
                            selected = preferences.defaultRepeat == mode,
                            onClick = { viewModel.setRepeat(mode) },
                        )
                    }
                }
            }
        }
        item(key = "library-heading") { SettingsHeading("资料库") }
        item(key = "rescan") {
            SettingsActionRow("重新扫描设备音乐", Icons.Rounded.Refresh, onRescan)
        }
        item(key = "usb-heading") { SettingsHeading("USB 音频") }
        item(key = "usb-card") {
            LiquidGlassSurface(Modifier.fillMaxWidth(), opacity = 0.34f, elevation = 4.dp) {
                Column(Modifier.fillMaxWidth().padding(LiquidSpacing.sm)) {
                    ToggleRow(
                        title = "bit-perfect 独占",
                        checked = usbAudio.requested,
                        onCheckedChange = viewModel::setUsbExclusive,
                    )
                    Text(
                        text = buildString {
                            append(usbAudio.deviceName ?: "USB DAC")
                            usbAudio.sampleRate?.let { append(" · ${it / 1_000} kHz") }
                            usbAudio.encoding?.let { append(" · $it") }
                            append("\n${usbAudio.message}")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                        modifier = Modifier.padding(horizontal = LiquidSpacing.sm, vertical = LiquidSpacing.xs),
                    )
                }
            }
        }
        item(key = "updates-heading") { SettingsHeading("应用更新") }
        item(key = "updates-card") {
            UpdateSettingsCard(
                state = updateState,
                onCheck = viewModel::checkForUpdate,
                onDownload = viewModel::downloadAndInstall,
                onInstall = viewModel::installCached,
                onCancel = viewModel::cancelUpdateDownload,
            )
        }
        item(key = "about-heading") { SettingsHeading("关于") }
        item(key = "licenses") {
            SettingsActionRow("开源许可证", Icons.Rounded.Description, onOpenLicenses)
        }
        item(key = "about") {
            LiquidGlassSurface(Modifier.fillMaxWidth(), opacity = 0.28f, elevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(LiquidSpacing.md), verticalArrangement = Arrangement.spacedBy(LiquidSpacing.xs)) {
                    Text("Liquid Music Android", style = MaterialTheme.typography.titleMedium)
                    Text("版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
                    Text("本应用只读取本地音乐、播放列表和本地歌词，不连接 Apple Music 或任何在线曲库。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
                }
            }
        }
    }
}

@Composable
private fun UpdateSettingsCard(
    state: UpdateState,
    onCheck: () -> Unit,
    onDownload: (io.github.admin0330.liquidmusic.update.model.UpdateManifest) -> Unit,
    onInstall: (io.github.admin0330.liquidmusic.update.model.UpdateManifest) -> Unit,
    onCancel: () -> Unit,
) {
    LiquidGlassSurface(Modifier.fillMaxWidth(), opacity = 0.34f, elevation = 4.dp) {
        Column(
            Modifier.fillMaxWidth().padding(LiquidSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LiquidSpacing.sm),
        ) {
            Text(
                text = when (state) {
                    UpdateState.Idle -> "从阿里云镜像检查新版"
                    UpdateState.Checking -> "正在检查更新…"
                    is UpdateState.Available -> "发现 ${state.manifest.versionName}"
                    is UpdateState.UpToDate -> "已是最新版本"
                    is UpdateState.Downloading -> "正在下载 ${state.manifest.versionName}"
                    is UpdateState.Verifying -> "正在校验安装包…"
                    is UpdateState.ReadyToInstall -> "安装包已校验，可以安装"
                    is UpdateState.Cancelled -> "下载已取消"
                    is UpdateState.Failed -> updateFailureMessage(state.failure.code)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (state is UpdateState.Available && state.manifest.changelog.isNotBlank()) {
                Text(state.manifest.changelog, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f))
            }
            if (state is UpdateState.Downloading) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${state.downloadedBytes / 1_048_576} / ${state.totalBytes / 1_048_576} MB", style = MaterialTheme.typography.labelMedium)
            }
            when (state) {
                UpdateState.Idle, is UpdateState.UpToDate, is UpdateState.Failed, is UpdateState.Cancelled ->
                    TextButton(onClick = onCheck) { Text("检查更新") }
                UpdateState.Checking, is UpdateState.Verifying -> Unit
                is UpdateState.Available -> TextButton(onClick = { onDownload(state.manifest) }) {
                    Text(if (state.cached) "安装已下载版本" else "下载并安装")
                }
                is UpdateState.Downloading -> TextButton(onClick = onCancel) { Text("取消下载") }
                is UpdateState.ReadyToInstall -> TextButton(onClick = { onInstall(state.manifest) }) { Text("安装") }
            }
        }
    }
}

private fun updateFailureMessage(code: UpdateFailureCode): String = when (code) {
    UpdateFailureCode.NETWORK, UpdateFailureCode.HTTP_STATUS -> "无法连接更新服务器"
    UpdateFailureCode.SHA256_MISMATCH, UpdateFailureCode.SIZE_MISMATCH,
    UpdateFailureCode.CONTENT_LENGTH_MISMATCH -> "安装包校验失败，已删除损坏文件"
    UpdateFailureCode.PACKAGE_MISMATCH, UpdateFailureCode.SIGNATURE_MISMATCH,
    UpdateFailureCode.VERSION_MISMATCH,
    UpdateFailureCode.INVALID_APK -> "安装包身份与当前应用不一致"
    UpdateFailureCode.INVALID_URL, UpdateFailureCode.INSECURE_REDIRECT,
    UpdateFailureCode.MANIFEST_TOO_LARGE, UpdateFailureCode.INVALID_MANIFEST -> "更新清单无效"
    UpdateFailureCode.STORAGE -> "无法保存安装包"
    UpdateFailureCode.INSTALL_INTENT -> "无法打开系统安装器"
}

@Composable
private fun SettingsHeading(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = LiquidSpacing.xs))
}

@Composable
private fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().liquidClickable(onClick = onClick).padding(horizontal = LiquidSpacing.sm, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(if (selected) "✓" else "", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = LiquidSpacing.sm, vertical = LiquidSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    LiquidGlassSurface(Modifier.fillMaxWidth().liquidClickable(onClick = onClick), opacity = 0.34f, elevation = 4.dp) {
        Row(Modifier.fillMaxWidth().padding(LiquidSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LiquidSpacing.sm)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
