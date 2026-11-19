package com.chencohensolutions.knesset.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chencohensolutions.knesset.core.odata.KnessetError
import com.chencohensolutions.knesset.data.repo.Api
import com.chencohensolutions.knesset.data.repo.FetchMode
import com.chencohensolutions.knesset.data.repo.Loader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DetailState {
    data object Loading : DetailState
    data class Ready(val content: DetailContent, val offline: Boolean, val refreshing: Boolean = false) : DetailState
    data class Failed(val error: KnessetError) : DetailState
}

/**
 * Loads a detail screen with stale-while-revalidate: whatever is cached is shown at once, then
 * anything stale is refetched and the screen updates.
 */
class DetailViewModel(
    private val api: Api,
    private val build: suspend Loader.() -> DetailContent,
) : ViewModel() {
    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        job = viewModelScope.launch {
            val fromCache = run(FetchMode.PREFER_CACHE)
            if (fromCache?.usedStale == true) run(FetchMode.NORMAL)
        }
    }

    fun refresh() {
        job?.cancel()
        _state.update { if (it is DetailState.Ready) it.copy(refreshing = true) else DetailState.Loading }
        job = viewModelScope.launch { run(FetchMode.FORCE) }
    }

    private suspend fun run(mode: FetchMode): Loader? {
        val loader = Loader(api, mode)
        return try {
            val content = loader.build()
            _state.value = DetailState.Ready(content, offline = loader.offline)
            loader
        } catch (e: KnessetError) {
            val current = _state.value
            _state.value = if (current is DetailState.Ready) current.copy(offline = true, refreshing = false) else DetailState.Failed(e)
            null
        }
    }
}
