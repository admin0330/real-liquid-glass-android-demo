package io.github.admin0330.liquidmusic.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.admin0330.liquidmusic.core.designsystem.components.liquidClickable
import io.github.admin0330.liquidmusic.core.designsystem.glass.LiquidGlassSurface
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidSpacing
import io.github.admin0330.liquidmusic.core.ui.FeatureScaffold
import io.github.admin0330.liquidmusic.core.ui.LibraryEmptyState
import io.github.admin0330.liquidmusic.core.ui.MusicSectionHeader
import io.github.admin0330.liquidmusic.core.ui.TrackRow
import io.github.admin0330.liquidmusic.domain.model.Track

@Composable
fun SearchScreen(
    bottomPadding: Dp,
    onTrackClick: (Track) -> Unit,
    allTracks: List<Track>,
    onPlayQueue: (Track, List<Track>) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    FeatureScaffold(
        title = "搜索",
        subtitle = "所有查询都只在设备上完成",
        bottomContentPadding = bottomPadding,
        modifier = modifier,
    ) {
        item(key = "search-field") {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), opacity = 0.40f, cornerRadius = 18.dp, elevation = 4.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LiquidSpacing.md, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f))
                    BasicTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = LiquidSpacing.sm),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = MaterialTheme.colorScheme.onSurface)),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { field ->
                            Box {
                                if (query.isEmpty()) Text("歌曲、专辑、艺人或播放列表", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f))
                                field()
                            }
                        },
                    )
                    if (query.isNotEmpty()) {
                        Box(Modifier.size(44.dp).liquidClickable { viewModel.setQuery("") }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, contentDescription = "清除搜索")
                        }
                    }
                }
            }
        }

        if (query.isBlank()) {
            item(key = "search-guidance") {
                LibraryEmptyState(
                    title = "搜索你的资料库",
                    message = "输入关键词即可即时筛选本地歌曲、专辑、艺人和播放列表。",
                )
            }
        } else if (results.isEmpty) {
            item(key = "search-empty") {
                LibraryEmptyState(title = "没有匹配结果", message = "尝试歌曲名、艺人、专辑或播放列表中的其他关键词。")
            }
        } else {
            if (results.tracks.isNotEmpty()) {
                item(key = "tracks-heading") { MusicSectionHeader("歌曲") }
                items(results.tracks, key = { "track:${it.id}" }) { track ->
                    TrackRow(track, { onTrackClick(track) }, modifier = Modifier.animateItem())
                }
            }
            if (results.albums.isNotEmpty()) {
                item(key = "albums-heading") { MusicSectionHeader("专辑") }
                items(results.albums, key = { "album:${it.id}" }) { album ->
                    Text(
                        "${album.title.ifBlank { "未知专辑" }}  ·  ${album.artist.ifBlank { "未知艺人" }}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .liquidClickable { onOpenAlbum(album.id) }
                            .padding(vertical = LiquidSpacing.sm),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (results.artists.isNotEmpty()) {
                item(key = "artists-heading") { MusicSectionHeader("艺人") }
                items(results.artists, key = { "artist:${it.id}" }) { artist ->
                    Text(
                        artist.name.ifBlank { "未知艺人" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .liquidClickable {
                                val queue = allTracks.filter { track ->
                                    track.artistId?.let { artist.id.endsWith(":artist:$it") } == true ||
                                        (track.artistId == null && track.artist.equals(artist.name, ignoreCase = true))
                                }
                                queue.firstOrNull()?.let { onPlayQueue(it, queue) }
                            }
                            .padding(vertical = LiquidSpacing.sm),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (results.playlists.isNotEmpty()) {
                item(key = "playlists-heading") { MusicSectionHeader("播放列表") }
                items(results.playlists, key = { "playlist:${it.id}" }) { playlist ->
                    Text(
                        playlist.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .liquidClickable { onOpenPlaylist(playlist.id) }
                            .padding(vertical = LiquidSpacing.sm),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
