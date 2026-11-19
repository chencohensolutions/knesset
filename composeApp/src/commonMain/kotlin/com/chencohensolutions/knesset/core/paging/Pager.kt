package com.chencohensolutions.knesset.core.paging

import com.chencohensolutions.knesset.core.odata.KnessetError
import com.chencohensolutions.knesset.core.odata.ODataQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [offline] is true when the page came from a stale cache because the network failed. */
data class PageResult<T>(val rows: List<T>, val count: Long? = null, val offline: Boolean = false)

data class PagerState<T>(
    val items: List<T> = emptyList(),
    val count: Long? = null,
    val loadingFirst: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: KnessetError? = null,
    /** True when any shown page came from the cache after a network failure. */
    val offline: Boolean = false,
) {
    val isEmpty get() = !loadingFirst && error == null && items.isEmpty() && endReached
}

/**
 * Explicit `$top`/`$skip` paging (never `@odata.nextLink`). `$count` is requested on the first page only.
 * A page shorter than the effective page size ends the list.
 */
class Pager<T>(
    private val scope: CoroutineScope,
    private val pageSize: Int = 50,
    private val distinctBy: ((T) -> Any)? = null,
    private val load: suspend (skip: Int, top: Int, withCount: Boolean, force: Boolean) -> PageResult<T>,
) {
    private val _state = MutableStateFlow(PagerState<T>())
    val state: StateFlow<PagerState<T>> = _state.asStateFlow()

    private var job: Job? = null
    private var nextSkip = 0
    private val seen = mutableSetOf<Any>()

    /** Rows per request, respecting the server cap. */
    val effectivePageSize get() = minOf(pageSize, ODataQuery.SERVER_PAGE_CAP)

    fun refresh(force: Boolean = false) {
        job?.cancel()
        nextSkip = 0
        seen.clear()
        _state.value = PagerState(loadingFirst = true)
        job = scope.launch { loadPage(first = true, force = force) }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingFirst || s.loadingMore || s.endReached || s.error != null) return
        _state.update { it.copy(loadingMore = true) }
        job = scope.launch { loadPage(first = false, force = false) }
    }

    fun retry() {
        if (_state.value.items.isEmpty()) refresh() else {
            _state.update { it.copy(error = null) }
            loadMore()
        }
    }

    private suspend fun loadPage(first: Boolean, force: Boolean) {
        val top = effectivePageSize
        try {
            val result = load(nextSkip, top, first, force)
            nextSkip += result.rows.size
            val fresh = if (distinctBy == null) result.rows else result.rows.filter { seen.add(distinctBy.invoke(it)) }
            _state.update {
                it.copy(
                    items = if (first) fresh else it.items + fresh,
                    count = if (first) result.count else it.count,
                    loadingFirst = false,
                    loadingMore = false,
                    endReached = result.rows.size < top,
                    error = null,
                    offline = (if (first) false else it.offline) || result.offline,
                )
            }
        } catch (e: KnessetError) {
            _state.update { it.copy(loadingFirst = false, loadingMore = false, error = e) }
        }
    }
}
