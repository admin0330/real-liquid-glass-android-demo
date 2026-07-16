package io.github.admin0330.liquidmusic.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.admin0330.liquidmusic.core.designsystem.components.liquidClickable
import io.github.admin0330.liquidmusic.core.designsystem.glass.LiquidGlassSurface
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidSpacing
import io.github.admin0330.liquidmusic.core.ui.FeatureScaffold

private data class OpenSourceLibrary(
    val name: String,
    val owner: String,
    val license: String,
    val projectUrl: String,
)

private val openSourceLibraries = listOf(
    OpenSourceLibrary("AndroidX / Jetpack Compose / Media3", "The Android Open Source Project", "Apache License 2.0", "https://github.com/androidx/androidx"),
    OpenSourceLibrary("Kotlin and kotlinx.coroutines", "JetBrains and contributors", "Apache License 2.0", "https://github.com/JetBrains/kotlin"),
    OpenSourceLibrary("Dagger / Hilt", "Google and contributors", "Apache License 2.0", "https://github.com/google/dagger"),
    OpenSourceLibrary("Coil", "Coil contributors", "Apache License 2.0", "https://github.com/coil-kt/coil"),
    OpenSourceLibrary("Haze", "Chris Banes and contributors", "Apache License 2.0", "https://github.com/chrisbanes/haze"),
    OpenSourceLibrary("Gradle", "Gradle, Inc. and contributors", "Apache License 2.0", "https://github.com/gradle/gradle"),
    OpenSourceLibrary("JUnit 4", "JUnit contributors", "Eclipse Public License 1.0", "https://github.com/junit-team/junit4"),
)

@Composable
fun OpenSourceLicensesScreen(
    bottomPadding: Dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FeatureScaffold(
        title = "开源许可证",
        subtitle = "完整声明同时收录于仓库 THIRD_PARTY_NOTICES.md",
        bottomContentPadding = bottomPadding,
        modifier = modifier,
        actions = {
            Box(Modifier.size(44.dp).liquidClickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "返回")
            }
        },
    ) {
        openSourceLibraries.forEach { library ->
            item(key = library.name) {
                LiquidGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    opacity = 0.30f,
                    elevation = 3.dp,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(LiquidSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(library.name, style = MaterialTheme.typography.titleMedium)
                        Text(library.owner, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
                        Text(library.license, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(library.projectUrl, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f))
                    }
                }
            }
        }
    }
}
