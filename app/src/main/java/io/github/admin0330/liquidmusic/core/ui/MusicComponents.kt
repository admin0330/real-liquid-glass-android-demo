package io.github.admin0330.liquidmusic.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.admin0330.liquidmusic.core.designsystem.components.Artwork
import io.github.admin0330.liquidmusic.core.designsystem.components.liquidClickable
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidSpacing
import io.github.admin0330.liquidmusic.domain.model.Album
import io.github.admin0330.liquidmusic.domain.model.Track
import io.github.admin0330.liquidmusic.domain.model.Artist
import io.github.admin0330.liquidmusic.core.designsystem.glass.LiquidGlassSurface
import java.util.Locale

@Composable
fun MusicSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onAction != null) Modifier.liquidClickable(onClick = onAction) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        if (!actionLabel.isNullOrBlank()) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun TrackCarousel(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LiquidSpacing.sibling),
    ) {
        items(tracks, key = { it.id }) { track ->
            TrackArtworkCard(track = track, onClick = { onTrackClick(track) })
        }
    }
}

@Composable
fun TrackArtworkCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(154.dp)
            .aspectRatio(1f)
            .liquidClickable(onClick = onClick),
    ) {
        Artwork(
            artworkUri = track.artworkUri,
            contentDescription = track.album,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = 18.dp,
        )
        LiquidGlassSurface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(6.dp),
            blurRadius = 18.dp,
            opacity = 0.58f,
            cornerRadius = 13.dp,
            elevation = 2.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist.ifBlank { "未知艺人" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ArtistArtworkCard(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(144.dp).liquidClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LiquidSpacing.xs),
    ) {
        Artwork(
            artworkUri = artist.artworkUri,
            contentDescription = artist.name,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            cornerRadius = 72.dp,
        )
        Text(
            text = artist.name.ifBlank { "未知艺人" },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AlbumArtworkCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(170.dp)
            .liquidClickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(LiquidSpacing.xs),
    ) {
        Artwork(
            artworkUri = album.artworkUri,
            contentDescription = album.title,
            modifier = artworkModifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = 20.dp,
        )
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.artist.ifBlank { "未知艺人" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .liquidClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            artworkUri = track.artworkUri,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            cornerRadius = 10.dp,
        )
        Spacer(Modifier.width(LiquidSpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(track.artist.ifBlank { "未知艺人" })
                    if (track.mimeType?.contains("flac", ignoreCase = true) == true) append(" · 无损")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f),
        )
        if (onMore != null) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .liquidClickable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = "更多", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
            }
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0) / 1_000)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}
