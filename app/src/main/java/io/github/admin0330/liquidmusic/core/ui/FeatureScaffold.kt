package io.github.admin0330.liquidmusic.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidSpacing

@Composable
fun FeatureScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = LiquidSpacing.screen,
            top = LiquidSpacing.lg,
            end = LiquidSpacing.screen,
            bottom = bottomContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(LiquidSpacing.md),
    ) {
        item(key = "feature-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(LiquidSpacing.sm),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                            modifier = Modifier.padding(top = LiquidSpacing.xxs),
                        )
                    }
                }
                actions()
            }
        }
        content()
    }
}
