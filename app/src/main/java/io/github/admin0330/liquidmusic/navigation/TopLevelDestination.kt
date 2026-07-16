package io.github.admin0330.liquidmusic.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    ListenNow("listen-now", "主页", Icons.Rounded.Home),
    Browse("browse", "新发现", Icons.Rounded.NewReleases),
    Library("library", "资料库", Icons.Rounded.LibraryMusic),
    Search("search", "搜索", Icons.Rounded.Search),
}
