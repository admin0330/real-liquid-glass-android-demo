package io.github.admin0330.liquidmusic.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.admin0330.liquidmusic.domain.model.LibrarySearchResult
import io.github.admin0330.liquidmusic.domain.usecase.SearchLibraryUseCase
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    searchLibrary: SearchLibraryUseCase,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    val results = _query
        .debounce(120)
        .distinctUntilChanged()
        .flatMapLatest { searchLibrary(it, 80) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibrarySearchResult.Empty)

    fun setQuery(value: String) {
        _query.value = value.take(160)
    }
}
