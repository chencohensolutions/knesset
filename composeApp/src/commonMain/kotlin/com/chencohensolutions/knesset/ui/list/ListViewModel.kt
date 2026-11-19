package com.chencohensolutions.knesset.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chencohensolutions.knesset.core.odata.F
import com.chencohensolutions.knesset.core.odata.IsraelTime
import com.chencohensolutions.knesset.core.odata.KnessetError
import com.chencohensolutions.knesset.core.odata.ODataQuery
import com.chencohensolutions.knesset.core.paging.PageResult
import com.chencohensolutions.knesset.core.paging.Pager
import com.chencohensolutions.knesset.core.paging.PagerState
import com.chencohensolutions.knesset.data.repo.Api
import com.chencohensolutions.knesset.data.repo.FetchMode
import com.chencohensolutions.knesset.data.repo.KnessetContext
import com.chencohensolutions.knesset.data.repo.LookupRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class DateRangeValue(val from: LocalDate?, val to: LocalDate?)

data class ListUiState(
    val query: String = "",
    val choices: Map<String, Set<String>> = emptyMap(),
    val toggles: Set<String> = emptySet(),
    val dates: Map<String, DateRangeValue> = emptyMap(),
    val sortIndex: Int = 0,
    val options: Map<String, List<Option>> = emptyMap(),
) {
    val hasActiveFilters get() = choices.values.any { it.isNotEmpty() } || toggles.isNotEmpty() || dates.isNotEmpty()
}

/**
 * Drives every list screen: search text (debounced 400 ms, at least 2 characters), filters, sort,
 * the Knesset selector, the pager and the result count (plan 3.2, 3.3).
 */
class ListViewModel<R>(
    val spec: ListSpec<R>,
    private val api: Api,
    private val knesset: KnessetContext,
    private val lookups: LookupRepo,
    initialQuery: String = "",
    initialChoices: Map<String, Set<String>> = emptyMap(),
) : ViewModel() {
    private val _ui = MutableStateFlow(ListUiState(query = initialQuery, choices = initialChoices))
    val ui: StateFlow<ListUiState> = _ui.asStateFlow()

    private var rowContext = RowContext(emptyMap())

    private val pager = Pager<ListRow>(viewModelScope, pageSize = PAGE_SIZE, distinctBy = if (spec.distinctRows) { r -> r.id } else null) { skip, top, withCount, force ->
        if (spec.needsStatuses && rowContext.statuses.isEmpty()) {
            rowContext = RowContext(runCatching { lookups.statuses() }.getOrDefault(emptyMap()))
        }
        val k = knesset.selection.value.selected
        val kFragment = if (k != null && spec.knessetFilter != null) spec.knessetFilter.invoke(k, lookups) else null
        val q = buildQuery(kFragment).copy { this.top = top; this.skip = skip; count = withCount }
        val page = api.page(q, spec.serializer, knesset.selection.value.ttl(spec.ttl), if (force) FetchMode.FORCE else FetchMode.NORMAL)
        PageResult(page.rows.map { spec.toRow(it, rowContext) }, page.count, page.offline)
    }
    val pagerState: StateFlow<PagerState<ListRow>> = pager.state

    init {
        pager.refresh()
        viewModelScope.launch {
            _ui.map { it.query.trim().let { q -> if (q.length < MIN_QUERY) "" else q } }
                .distinctUntilChanged().drop(1).debounce(400).collect { pager.refresh() }
        }
        viewModelScope.launch {
            knesset.selection.map { it.selected }.distinctUntilChanged().drop(1).collect {
                _ui.update { s -> s.copy(options = emptyMap()) }
                pager.refresh()
            }
        }
    }

    fun buildQuery(knessetFragment: String? = null): ODataQuery {
        val s = _ui.value
        return ODataQuery(spec.entitySet).apply {
            spec.baseFilters.forEach { filter(it) }
            filter(knessetFragment)
            val k = knesset.selection.value.selected
            if (spec.knessetField != null && k != null) filter(F.eq(spec.knessetField, k))
            val text = s.query.trim()
            if (text.length >= MIN_QUERY) filter(spec.search?.invoke(text))
            spec.filters.forEach { def ->
                when (def) {
                    is FilterDef.Toggle -> if (def.id in s.toggles) filter(def.fragment)
                    is FilterDef.Choice -> s.choices[def.id]?.takeIf { it.isNotEmpty() }?.let { filter(def.fragment(it)) }
                    is FilterDef.DateRange -> s.dates[def.id]?.let { r ->
                        r.from?.let { filter(F.ge(def.field, IsraelTime.startOfDay(it))) }
                        r.to?.let { filter("${def.field} lt ${F.dateLiteral(IsraelTime.startOfDay(it.plus(DatePeriod(days = 1))))}") }
                    }
                }
            }
            val sort = spec.sorts.getOrNull(s.sortIndex) ?: spec.sorts.first()
            orderBy = if (sort.orderBy.contains("Id ")) sort.orderBy else sort.orderBy + ",Id desc"
            select = spec.select
            expand = spec.expand
        }
    }

    fun setQuery(text: String) = _ui.update { it.copy(query = text) }

    fun toggle(id: String) {
        _ui.update { it.copy(toggles = if (id in it.toggles) it.toggles - id else it.toggles + id) }
        pager.refresh()
    }

    fun setChoice(id: String, values: Set<String>) {
        _ui.update { it.copy(choices = it.choices + (id to values)) }
        pager.refresh()
    }

    fun setDates(id: String, value: DateRangeValue?) {
        _ui.update { it.copy(dates = if (value == null) it.dates - id else it.dates + (id to value)) }
        pager.refresh()
    }

    fun setSort(index: Int) {
        _ui.update { it.copy(sortIndex = index) }
        pager.refresh()
    }

    fun clearFilters() {
        _ui.update { it.copy(choices = emptyMap(), toggles = emptySet(), dates = emptyMap()) }
        pager.refresh()
    }

    /** Loads a choice filter's options once per Knesset. */
    fun loadOptions(def: FilterDef.Choice) {
        if (_ui.value.options.containsKey(def.id)) return
        viewModelScope.launch {
            val options = try {
                def.options(FilterContext(lookups, knesset.selection.value.selected))
            } catch (e: KnessetError) {
                emptyList()
            }
            _ui.update { it.copy(options = it.options + (def.id to options)) }
        }
    }

    fun refresh() = pager.refresh(force = true)
    fun loadMore() = pager.loadMore()
    fun retry() = pager.retry()

    companion object {
        const val PAGE_SIZE = 50
        const val MIN_QUERY = 2
    }
}
