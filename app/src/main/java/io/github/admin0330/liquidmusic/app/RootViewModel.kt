package io.github.admin0330.liquidmusic.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.admin0330.liquidmusic.core.preferences.AppPreferences
import io.github.admin0330.liquidmusic.core.preferences.UserPreferences
import io.github.admin0330.liquidmusic.domain.model.LibraryScanFailure
import io.github.admin0330.liquidmusic.domain.model.LibraryScanResult
import io.github.admin0330.liquidmusic.domain.usecase.ObserveAlbumsUseCase
import io.github.admin0330.liquidmusic.domain.usecase.ObserveArtistsUseCase
import io.github.admin0330.liquidmusic.domain.usecase.ObserveFavoritesUseCase
import io.github.admin0330.liquidmusic.domain.usecase.ObservePlaylistsUseCase
import io.github.admin0330.liquidmusic.domain.usecase.ObserveRecentlyAddedUseCase
import io.github.admin0330.liquidmusic.domain.usecase.ObserveRecentlyPlayedUseCase
import io.github.admin0330.liquidmusic.domain.usecase.ObserveTracksUseCase
import io.github.admin0330.liquidmusic.domain.usecase.RefreshLibraryUseCase
import io.github.admin0330.liquidmusic.data.legacy.LegacyDataMigrator
import io.github.admin0330.liquidmusic.data.media.MediaStoreChangeObserver
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface LibraryScanUiState {
    data object Scanning : LibraryScanUiState
    data object Ready : LibraryScanUiState
    data object PermissionRequired : LibraryScanUiState
    data object PermissionDenied : LibraryScanUiState
    data class Failed(val reason: LibraryScanFailure) : LibraryScanUiState
}

@HiltViewModel
@OptIn(FlowPreview::class)
class RootViewModel @Inject constructor(
    preferences: AppPreferences,
    observeTracks: ObserveTracksUseCase,
    observeAlbums: ObserveAlbumsUseCase,
    observeArtists: ObserveArtistsUseCase,
    observeFavorites: ObserveFavoritesUseCase,
    observeRecentlyAdded: ObserveRecentlyAddedUseCase,
    observeRecentlyPlayed: ObserveRecentlyPlayedUseCase,
    observePlaylists: ObservePlaylistsUseCase,
    private val refreshLibrary: RefreshLibraryUseCase,
    private val legacyDataMigrator: LegacyDataMigrator,
    mediaStoreChanges: MediaStoreChangeObserver,
) : ViewModel() {
    val preferences = preferences.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences(),
    )

    val tracks = observeTracks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val albums = observeAlbums().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val artists = observeArtists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites = observeFavorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentlyAdded = observeRecentlyAdded(20).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentlyPlayed = observeRecentlyPlayed(20).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val playlists = observePlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _scanState = MutableStateFlow<LibraryScanUiState>(LibraryScanUiState.Scanning)
    val scanState = _scanState.asStateFlow()
    private val refreshMutex = Mutex()

    init {
        viewModelScope.launch {
            _scanState.value = LibraryScanUiState.Scanning
            runCatching { legacyDataMigrator.migrateIfNeeded() }
            performRefresh()
        }
        viewModelScope.launch {
            mediaStoreChanges.changes
                .debounce(MEDIA_STORE_DEBOUNCE_MS)
                .collect { performRefresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _scanState.value = LibraryScanUiState.Scanning
            performRefresh()
        }
    }

    private suspend fun performRefresh() {
        refreshMutex.withLock {
            _scanState.value = when (val result = refreshLibrary()) {
                is LibraryScanResult.Success -> LibraryScanUiState.Ready
                LibraryScanResult.PermissionRequired -> LibraryScanUiState.PermissionRequired
                is LibraryScanResult.Failed -> LibraryScanUiState.Failed(result.reason)
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) refresh() else _scanState.value = LibraryScanUiState.PermissionDenied
    }

    private companion object {
        const val MEDIA_STORE_DEBOUNCE_MS = 1_200L
    }
}
