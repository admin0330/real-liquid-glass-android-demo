package io.github.admin0330.liquidmusic.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.admin0330.liquidmusic.core.designsystem.glass.LiquidGlassSurface
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidSpacing
import io.github.admin0330.liquidmusic.core.designsystem.components.liquidClickable

@Composable
fun LibraryEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    LiquidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        opacity = 0.34f,
        elevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LiquidSpacing.lg, vertical = LiquidSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LiquidSpacing.sm),
        ) {
            Icon(
                imageVector = Icons.Rounded.AudioFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                textAlign = TextAlign.Center,
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Text(
                    text = actionLabel,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .padding(top = LiquidSpacing.xs)
                        .liquidClickable(onClick = onAction),
                )
            }
        }
    }
}
