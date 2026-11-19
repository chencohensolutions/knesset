package com.chencohensolutions.knesset.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chencohensolutions.knesset.core.odata.IsraelTime
import com.chencohensolutions.knesset.resources.Res
import com.chencohensolutions.knesset.resources.cancel
import com.chencohensolutions.knesset.resources.clear_all
import com.chencohensolutions.knesset.resources.date_custom
import com.chencohensolutions.knesset.resources.date_last_month
import com.chencohensolutions.knesset.resources.date_last_week
import com.chencohensolutions.knesset.resources.date_last_year
import com.chencohensolutions.knesset.resources.ok
import com.chencohensolutions.knesset.resources.results_count
import com.chencohensolutions.knesset.resources.search
import com.chencohensolutions.knesset.resources.sort
import com.chencohensolutions.knesset.ui.components.EmptyState
import com.chencohensolutions.knesset.ui.components.ErrorState
import com.chencohensolutions.knesset.ui.components.ItemRow
import com.chencohensolutions.knesset.ui.components.LoadingState
import com.chencohensolutions.knesset.ui.components.OfflineBanner
import com.chencohensolutions.knesset.ui.components.VoteBar
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

/** The shared list pattern (plan 2.3): search, filter chips, count, sort, paged rows. */
@Composable
fun ListContent(vm: ListViewModel<*>, modifier: Modifier = Modifier, header: (@Composable () -> Unit)? = null) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val state by vm.pagerState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 10
        }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) vm.loadMore() }

    Column(modifier.fillMaxSize()) {
        if (vm.spec.search != null) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(Res.string.search)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (ui.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Clear, stringResource(Res.string.clear_all)) }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
            )
        }
        FilterRow(vm, ui)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.count?.let { stringResource(Res.string.results_count, it) } ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            SortMenu(vm, ui)
        }
        if (state.offline) OfflineBanner()
        PullToRefreshBox(isRefreshing = false, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            when {
                state.loadingFirst -> LoadingState()
                state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, vm::retry)
                state.isEmpty -> EmptyState()
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    if (header != null) item { header() }
                    itemsIndexed(state.items, key = { _, it -> it.id.toString() + (it.route?.toString() ?: "") }) { index, row ->
                        if (row.group != null && row.group != state.items.getOrNull(index - 1)?.group) {
                            Text(
                                row.group,
                                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 6.dp).semantics { heading() },
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        ItemRow(
                            title = row.title, subtitle = row.subtitle, status = row.status, date = row.date,
                            initials = row.initials, route = row.route, url = row.url,
                            below = row.bar?.let { b -> { VoteBar(b.first, b.second, b.third) } },
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                    if (state.loadingMore) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    if (state.error != null) item { ErrorState(state.error!!, vm::retry, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

@Composable
private fun SortMenu(vm: ListViewModel<*>, ui: ListUiState) {
    if (vm.spec.sorts.size < 2) return
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, stringResource(Res.string.sort))
            Text(" " + stringResource(vm.spec.sorts[ui.sortIndex].label))
        }
        DropdownMenu(open, { open = false }) {
            vm.spec.sorts.forEachIndexed { i, s ->
                DropdownMenuItem(
                    text = { Text(stringResource(s.label)) },
                    onClick = { vm.setSort(i); open = false },
                    leadingIcon = { if (i == ui.sortIndex) Icon(Icons.Filled.Check, null) },
                )
            }
        }
    }
}

@Composable
private fun FilterRow(vm: ListViewModel<*>, ui: ListUiState) {
    val filters = vm.spec.filters
    if (filters.isEmpty()) return
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (ui.hasActiveFilters) item {
            InputChip(selected = false, onClick = vm::clearFilters, label = { Text(stringResource(Res.string.clear_all)) }, trailingIcon = { Icon(Icons.Filled.Clear, null) })
        }
        items(filters, key = { it.id }) { def ->
            when (def) {
                is FilterDef.Toggle -> FilterChip(def.id in ui.toggles, { vm.toggle(def.id) }, { Text(stringResource(def.label)) })
                is FilterDef.Choice -> ChoiceChip(vm, def, ui)
                is FilterDef.DateRange -> DateChip(vm, def, ui)
            }
        }
    }
}

@Composable
private fun ChoiceChip(vm: ListViewModel<*>, def: FilterDef.Choice, ui: ListUiState) {
    var open by remember { mutableStateOf(false) }
    val selected = ui.choices[def.id].orEmpty()
    val options = ui.options[def.id]
    val label = stringResource(def.label) + when (selected.size) {
        0 -> ""
        1 -> ": " + (options?.firstOrNull { it.value in selected }?.label ?: "1")
        else -> " (${selected.size})"
    }
    Box {
        FilterChip(
            selected = selected.isNotEmpty(),
            onClick = { vm.loadOptions(def); open = true },
            label = { Text(label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
        )
        DropdownMenu(open, { open = false }) {
            when {
                options == null -> DropdownMenuItem(text = { CircularProgressIndicator() }, onClick = {})
                options.isEmpty() -> DropdownMenuItem(text = { EmptyState(Modifier) }, onClick = { open = false })
                else -> options.forEach { o ->
                    DropdownMenuItem(
                        text = { Text(o.label, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)) },
                        leadingIcon = { if (o.value in selected) Icon(Icons.Filled.Check, null) },
                        onClick = {
                            val next = when {
                                !def.multi -> if (o.value in selected) emptySet() else setOf(o.value)
                                o.value in selected -> selected - o.value
                                else -> selected + o.value
                            }
                            vm.setChoice(def.id, next)
                            if (!def.multi) open = false
                        },
                    )
                }
            }
        }
    }
}

private fun today(): LocalDate = Clock.System.now().toLocalDateTime(IsraelTime.zone).date

@Composable
private fun DateChip(vm: ListViewModel<*>, def: FilterDef.DateRange, ui: ListUiState) {
    var open by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    val value = ui.dates[def.id]
    val label = stringResource(def.label) + (value?.let { ": " + (it.from?.let(IsraelTime::date) ?: "…") + "–" + (it.to?.let(IsraelTime::date) ?: "…") } ?: "")
    Box {
        FilterChip(value != null, { open = true }, { Text(label, maxLines = 1) }, trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) })
        DropdownMenu(open, { open = false }) {
            listOf(Res.string.date_last_week to 7, Res.string.date_last_month to 31, Res.string.date_last_year to 365).forEach { (l, days) ->
                DropdownMenuItem(text = { Text(stringResource(l)) }, onClick = {
                    vm.setDates(def.id, DateRangeValue(today().minus(DatePeriod(days = days)), null)); open = false
                })
            }
            DropdownMenuItem(text = { Text(stringResource(Res.string.date_custom)) }, onClick = { open = false; picker = true })
            if (value != null) DropdownMenuItem(text = { Text(stringResource(Res.string.clear_all)) }, onClick = { vm.setDates(def.id, null); open = false })
        }
    }
    if (picker) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = {
                TextButton(onClick = {
                    fun day(ms: Long?) = ms?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date }
                    val from = day(state.selectedStartDateMillis)
                    val to = day(state.selectedEndDateMillis)
                    vm.setDates(def.id, if (from == null && to == null) null else DateRangeValue(from, to))
                    picker = false
                }) { Text(stringResource(Res.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picker = false }) { Text(stringResource(Res.string.cancel)) } },
        ) {
            DateRangePicker(state, Modifier.weight(1f))
        }
    }
}
