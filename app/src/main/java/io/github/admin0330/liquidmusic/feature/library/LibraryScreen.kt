package io.github.admin0330.liquidmusic.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.admin0330.liquidmusic.app.LibraryScanUiState
import io.github.admin0330.liquidmusic.core.designsystem.components.Artwork
import io.github.admin0330.liquidmusic.core.designsystem.components.liquidClickable
import io.github.admin0330.liquidmusic.core.designsystem.glass.LiquidGlassSurface
import io.github.admin0330.liquidmusic.core.designsystem.tokens.LiquidSpacing
import io.github.admin0330.liquidmusic.core.ui.AlbumArtworkCard
import io.github.admin0330.liquidmusic.core.ui.FeatureScaffold
import io.github.admin0330.liquidmusic.core.ui.LibraryEmptyState
import io.github.admin0330.liquidmusic.core.ui.TrackRow
import io.github.admin0330.liquidmusic.core.ui.TrackArtworkCard
import io.github.admin0330.liquidmusic.core.ui.ArtistArtworkCard
import io.github.admin0330.liquidmusic.domain.model.Album
import io.github.admin0330.liquidmusic.domain.model.Artist
import io.github.admin0330.liquidmusic.domain.model.Playlist
import io.github.admin0330.liquidmusic.domain.model.Track
import io.github.admin0330.liquidmusic.domain.model.LibraryScanFailure

private enum class LibrarySection(val label: String) {
    Songs("歌曲"), Albums("专辑"), Artists("艺人"), Playlists("播放列表")
}

private enum class LibraryLayout { LIST, GRID }

private enum class LibrarySort(val label: String) {
    RECENTLY_ADDED("最近添加"),
    RECENTLY_PLAYED("最近播放"),
    NAME("名称"),
    ARTIST("艺人"),
}

@Composable
fun LibraryScreen(
    bottomPadding: Dp,
    tracks: List<Track>,
    albums: List<Album>,
    artists: List<Artist>,
    playlists: List<Playlist>,
    scanState: LibraryScanUiState,
    onRescan: () -> Unit,
    onRequestPermission: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayQueue: (Track, List<Track>) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    albumArtworkModifier: @Composable (String) -> Modifier = { Modifier },
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    var sectionName by rememberSaveable { mutableStateOf(LibrarySection.Songs.name) }
    val section = LibrarySection.valueOf(sectionName)
    var layoutName by rememberSaveable { mutableStateOf(LibraryLayout.LIST.name) }
    val layout = LibraryLayout.valueOf(layoutName)
    var sortMenu by remember { mutableStateOf(false) }
    var sortName by rememberSaveable { mutableStateOf(LibrarySort.NAME.name) }
    val sort = LibrarySort.valueOf(sortName)
    val sortedTracks = remember(tracks, sort) { tracks.sortedTracksForLibrary(sort) }
    val sortedAlbums = remember(albums, tracks, sort) { albums.sortedAlbumsForLibrary(tracks, sort) }
    val sortedArtists = remember(artists, tracks, sort) { artists.sortedArtistsForLibrary(tracks, sort) }
    val sortedPlaylists = remember(playlists, sort) { playlists.sortedPlaylistsForLibrary(sort) }
    var createDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var addTrack by remember { mutableStateOf<Track?>(null) }
    var playlistActions by remember { mutableStateOf<Playlist?>(null) }
    var renamePlaylist by remember { mutableStateOf<Playlist?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var deletePlaylist by remember { mutableStateOf<Playlist?>(null) }
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(actionState.message) {
        actionState.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(modifier.fillMaxWidth()) {
        FeatureScaffold(
            title = "资料库",
            subtitle = "${tracks.size} 首本地歌曲",
            bottomContentPadding = bottomPadding,
            actions = {
                HeaderIcon(Icons.Rounded.Refresh, "重新扫描", onRescan)
                HeaderIcon(
                    if (layout == LibraryLayout.LIST) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList,
                    "切换布局",
                ) { layoutName = if (layout == LibraryLayout.LIST) LibraryLayout.GRID.name else LibraryLayout.LIST.name }
                Box {
                    HeaderIcon(Icons.AutoMirrored.Rounded.Sort, "排序") { sortMenu = true }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        LibrarySort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                trailingIcon = {
                                    if (sort == option) Text("✓", color = MaterialTheme.colorScheme.primary)
                                },
                                onClick = {
                                    sortName = option.name
                                    sortMenu = false
                                },
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "library-categories") {
                LibraryCategoryBar(section) { sectionName = it.name }
            }

            val permissionNeeded = scanState is LibraryScanUiState.PermissionRequired ||
                scanState is LibraryScanUiState.PermissionDenied
            if (tracks.isEmpty() && section != LibrarySection.Playlists) {
                item(key = "empty-library") {
                    LibraryEmptyState(
                        title = when (scanState) {
                            LibraryScanUiState.Scanning -> "正在扫描音乐"
                            is LibraryScanUiState.Failed -> "扫描失败"
                            else -> "没有找到音乐"
                        },
                        message = when {
                            permissionNeeded -> "需要音乐访问权限才能读取 MediaStore。你的文件不会上传。"
                            scanState is LibraryScanUiState.Failed -> scanState.reason.toUserMessage()
                            else -> "MediaStore 中没有受支持的本地音乐。"
                        },
                        actionLabel = if (permissionNeeded) "允许访问音乐" else "重新扫描",
                        onAction = if (permissionNeeded) onRequestPermission else onRescan,
                    )
                }
            } else when (section) {
                LibrarySection.Songs -> {
                    val values = sortedTracks
                    if (layout == LibraryLayout.GRID) {
                        items(values.chunked(2), key = { row -> row.joinToString("|") { it.id } }) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LiquidSpacing.sibling)) {
                                row.forEach { track ->
                                    TrackArtworkCard(track, { onTrackClick(track) }, Modifier.weight(1f))
                                }
                                if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        items(values, key = { it.id }) { track ->
                            TrackRow(
                                track = track,
                                onClick = { onTrackClick(track) },
                                onMore = { addTrack = track },
                            )
                        }
                    }
                }
                LibrarySection.Albums -> {
                    val values = sortedAlbums
                    if (layout == LibraryLayout.GRID) {
                        items(values.chunked(2), key = { row -> row.joinToString("|") { it.id } }) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LiquidSpacing.sibling)) {
                                row.forEach { album ->
                                    AlbumArtworkCard(
                                        album,
                                        onClick = { onOpenAlbum(album.id) },
                                        modifier = Modifier.weight(1f),
                                        artworkModifier = albumArtworkModifier(album.id),
                                    )
                                }
                                if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        items(values, key = { it.id }) { album ->
                            AlbumListRow(album) { onOpenAlbum(album.id) }
                        }
                    }
                }
                LibrarySection.Artists -> {
                    val values = sortedArtists
                    if (layout == LibraryLayout.GRID) {
                        items(values.chunked(2), key = { row -> row.joinToString("|") { it.id } }) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LiquidSpacing.sibling)) {
                                row.forEach { artist ->
                                    ArtistArtworkCard(
                                        artist = artist,
                                        onClick = { playArtist(artist, tracks, onPlayQueue) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        items(values, key = { it.id }) { artist ->
                            ArtistListRow(artist) {
                                playArtist(artist, tracks, onPlayQueue)
                            }
                        }
                    }
                }
                LibrarySection.Playlists -> {
                    item(key = "create-playlist") {
                        LiquidGlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidClickable { createDialog = true },
                            opacity = 0.30f,
                            elevation = 3.dp,
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(LiquidSpacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(LiquidSpacing.sm),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("新建播放列表", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    val values = sortedPlaylists
                    if (layout == LibraryLayout.GRID) {
                        items(values.chunked(2), key = { row -> row.joinToString("|") { it.id } }) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LiquidSpacing.sibling)) {
                                row.forEach { playlist ->
                                    PlaylistGridCard(
                                        playlist = playlist,
                                        onClick = { onOpenPlaylist(playlist.id) },
                                        onMore = { playlistActions = playlist },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        items(values, key = { it.id }) { playlist ->
                            PlaylistListRow(
                                playlist = playlist,
                                onClick = { onOpenPlaylist(playlist.id) },
                                onMore = { playlistActions = playlist },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding))
    }

    if (createDialog) {
        NameDialog(
            title = "新建播放列表",
            value = playlistName,
            onValueChange = { playlistName = it },
            onDismiss = { createDialog = false },
            onConfirm = {
                viewModel.create(playlistName)
                playlistName = ""
                createDialog = false
            },
        )
    }
    addTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { addTrack = null },
            title = { Text("歌曲操作") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(LiquidSpacing.xs)) {
                    Text(
                        text = if (track.isFavorite) "取消收藏" else "添加到喜爱",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidClickable {
                                viewModel.toggleFavorite(track)
                                addTrack = null
                            }
                            .padding(vertical = LiquidSpacing.sm),
                    )
                    Text("添加到播放列表", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f))
                    if (playlists.isEmpty()) Text("请先创建播放列表")
                    playlists.forEach { playlist ->
                        Text(
                            text = playlist.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidClickable {
                                    viewModel.addTrack(playlist.id, track.id)
                                    addTrack = null
                                }
                                .padding(vertical = LiquidSpacing.sm),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addTrack = null }) { Text("关闭") } },
        )
    }
    playlistActions?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistActions = null },
            title = { Text(playlist.name) },
            text = {
                Column {
                    Text(
                        "重命名",
                        Modifier.fillMaxWidth().liquidClickable {
                            renamePlaylist = playlist
                            renameValue = playlist.name
                            playlistActions = null
                        }.padding(vertical = LiquidSpacing.sm),
                    )
                    Text(
                        "删除播放列表",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().liquidClickable {
                            deletePlaylist = playlist
                            playlistActions = null
                        }.padding(vertical = LiquidSpacing.sm),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { playlistActions = null }) { Text("关闭") } },
        )
    }
    renamePlaylist?.let { playlist ->
        NameDialog(
            title = "重命名播放列表",
            value = renameValue,
            onValueChange = { renameValue = it },
            onDismiss = { renamePlaylist = null },
            onConfirm = {
                viewModel.rename(playlist.id, renameValue, playlist.description)
                renamePlaylist = null
            },
        )
    }
    deletePlaylist?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deletePlaylist = null },
            title = { Text("删除“${playlist.name}”？") },
            text = { Text("播放列表会被删除，音乐文件不会受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(playlist.id)
                    deletePlaylist = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletePlaylist = null }) { Text("取消") } },
        )
    }
}

private fun LibraryScanFailure.toUserMessage(): String = when (this) {
    LibraryScanFailure.MEDIA_PROVIDER_UNAVAILABLE -> "系统媒体服务暂时不可用，请稍后重试。"
    LibraryScanFailure.QUERY_FAILED -> "读取 MediaStore 时失败，请确认权限后重新扫描。"
}

@Composable
private fun LibraryCategoryBar(selected: LibrarySection, onSelect: (LibrarySection) -> Unit) {
    LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), opacity = 0.32f, elevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LibrarySection.entries.forEach { item ->
                val active = selected == item
                Text(
                    text = item.label,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .weight(1f)
                        .liquidClickable { onSelect(item) }
                        .padding(vertical = LiquidSpacing.sm),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).liquidClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun AlbumListRow(album: Album, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().liquidClickable(onClick = onClick).padding(vertical = LiquidSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Artwork(album.artworkUri, null, Modifier.size(58.dp), 12.dp)
        Column(Modifier.weight(1f).padding(horizontal = LiquidSpacing.sm)) {
            Text(album.title.ifBlank { "未知专辑" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${album.artist.ifBlank { "未知艺人" }} · ${album.trackCount} 首", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f))
        }
    }
}

@Composable
private fun ArtistListRow(artist: Artist, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().liquidClickable(onClick = onClick).padding(vertical = LiquidSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Artwork(artist.artworkUri, null, Modifier.size(58.dp), 29.dp)
        Column(Modifier.weight(1f).padding(horizontal = LiquidSpacing.sm)) {
            Text(artist.name.ifBlank { "未知艺人" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${artist.albumCount} 张专辑 · ${artist.trackCount} 首", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f))
        }
    }
}

@Composable
private fun PlaylistListRow(playlist: Playlist, onClick: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().liquidClickable(onClick = onClick).padding(vertical = LiquidSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Artwork(playlist.artworkUri, null, Modifier.size(58.dp), 12.dp)
        Column(Modifier.weight(1f).padding(horizontal = LiquidSpacing.sm)) {
            Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${playlist.trackCount} 首歌曲", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f))
        }
        Box(Modifier.size(44.dp).liquidClickable(onClick = onMore), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MoreHoriz, contentDescription = "更多")
        }
    }
}

@Composable
private fun PlaylistGridCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.liquidClickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Artwork(playlist.artworkUri, playlist.name, Modifier.fillMaxWidth().aspectRatio(1f), 20.dp)
            LiquidGlassSurface(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(44.dp).liquidClickable(onClick = onMore),
                cornerRadius = 22.dp,
                opacity = 0.56f,
                elevation = 2.dp,
            ) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = "更多", modifier = Modifier.align(Alignment.Center))
            }
        }
        Text(
            text = playlist.name,
            modifier = Modifier.padding(top = LiquidSpacing.xs),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${playlist.trackCount} 首歌曲",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
        )
    }
}

private fun List<Track>.sortedTracksForLibrary(sort: LibrarySort): List<Track> = when (sort) {
    LibrarySort.RECENTLY_ADDED -> sortedByDescending(Track::dateAddedEpochSeconds)
    LibrarySort.RECENTLY_PLAYED -> sortedByDescending { it.lastPlayedAtMs ?: Long.MIN_VALUE }
    LibrarySort.NAME -> sortedBy { it.title.lowercase() }
    LibrarySort.ARTIST -> sortedWith(compareBy<Track> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
}

private fun List<Album>.sortedAlbumsForLibrary(tracks: List<Track>, sort: LibrarySort): List<Album> = when (sort) {
    LibrarySort.NAME -> sortedBy { it.title.lowercase() }
    LibrarySort.ARTIST -> sortedWith(compareBy<Album> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
    LibrarySort.RECENTLY_ADDED -> sortedByDescending { album ->
        tracksForAlbum(album, tracks).maxOfOrNull(Track::dateAddedEpochSeconds) ?: Long.MIN_VALUE
    }
    LibrarySort.RECENTLY_PLAYED -> sortedByDescending { album ->
        tracksForAlbum(album, tracks).mapNotNull(Track::lastPlayedAtMs).maxOrNull() ?: Long.MIN_VALUE
    }
}

private fun List<Artist>.sortedArtistsForLibrary(tracks: List<Track>, sort: LibrarySort): List<Artist> = when (sort) {
    LibrarySort.NAME, LibrarySort.ARTIST -> sortedBy { it.name.lowercase() }
    LibrarySort.RECENTLY_ADDED -> sortedByDescending { artist ->
        tracksForArtist(artist, tracks).maxOfOrNull(Track::dateAddedEpochSeconds) ?: Long.MIN_VALUE
    }
    LibrarySort.RECENTLY_PLAYED -> sortedByDescending { artist ->
        tracksForArtist(artist, tracks).mapNotNull(Track::lastPlayedAtMs).maxOrNull() ?: Long.MIN_VALUE
    }
}

private fun List<Playlist>.sortedPlaylistsForLibrary(sort: LibrarySort): List<Playlist> = when (sort) {
    LibrarySort.RECENTLY_ADDED -> sortedByDescending(Playlist::createdAtMs)
    LibrarySort.RECENTLY_PLAYED -> sortedByDescending(Playlist::updatedAtMs)
    LibrarySort.NAME, LibrarySort.ARTIST -> sortedBy { it.name.lowercase() }
}

private fun tracksForAlbum(album: Album, tracks: List<Track>): List<Track> = tracks.filter { track ->
    track.albumId?.let { album.id.endsWith(":album:$it") } == true ||
        (track.albumId == null && track.album.equals(album.title, ignoreCase = true))
}

private fun tracksForArtist(artist: Artist, tracks: List<Track>): List<Track> = tracks.filter { track ->
    track.artistId?.let { artist.id.endsWith(":artist:$it") } == true ||
        (track.artistId == null && track.artist.equals(artist.name, ignoreCase = true))
}

private fun playArtist(
    artist: Artist,
    tracks: List<Track>,
    onPlayQueue: (Track, List<Track>) -> Unit,
) {
    val queue = tracksForArtist(artist, tracks)
    queue.firstOrNull()?.let { onPlayQueue(it, queue) }
}

@Composable
private fun NameDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = onValueChange, singleLine = true, label = { Text("名称") }) },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = onConfirm) { Text("完成") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
