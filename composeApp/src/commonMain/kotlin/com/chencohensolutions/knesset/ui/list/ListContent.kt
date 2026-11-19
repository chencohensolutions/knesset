package com.chencohensolutions.knesset.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.chencohensolutions.knesset.resources.filters
import com.chencohensolutions.knesset.resources.show_n_results
import com.chencohensolutions.knesset.ui.components.AppSheet
import com.chencohensolutions.knesset.ui.components.ChoicePill
import com.chencohensolutions.knesset.ui.components.DataText
import com.chencohensolutions.knesset.ui.components.SearchPill
import com.chencohensolutions.knesset.ui.theme.AppTheme
import com.chencohensolutions.knesset.ui.theme.Motion
import com.chencohensolutions.knesset.ui.theme.Radius
import com.chencohensolutions.knesset.ui.theme.Space
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

/** The shared list pattern (docs 4 "Lists"): search pill, filter bar, animated count, cards. */
@Composable
fun ListContent(vm: ListViewModel<*>, modifier: Modifier = Modifier, header: (@Composable () -> Unit)? = null) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val state by vm.pagerState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val c = AppTheme.colors

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 10
        }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) vm.loadMore() }

    Column(modifier.fillMaxSize()) {
        if (vm.spec.search != null) SearchPill(ui.query, vm::setQuery, stringResource(Res.string.search))
        Row(Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { FilterBar(vm, ui) }
            SortMenu(vm, ui)
        }
        AnimatedContent(state.count, transitionSpec = { fadeIn(tween(Motion.SHORT)) togetherWith fadeOut(tween(Motion.SHORT)) }, label = "count") { count ->
            Text(
                count?.let { stringResource(Res.string.results_count, it) } ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.xs),
            )
        }
        if (state.offline) OfflineBanner()
        val phase = when {
            state.loadingFirst -> 0
            state.error != null && state.items.isEmpty() -> 1
            state.isEmpty -> 2
            else -> 3
        }
        PullToRefreshBox(isRefreshing = false, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            // Skeletons cross-fade to content (docs 5.6).
            Crossfade(phase, animationSpec = tween(200), label = "list") { p ->
                when (p) {
                    0 -> LoadingState()
                    1 -> ErrorState(state.error ?: return@Crossfade, vm::retry)
                    2 -> EmptyState()
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = Space.s, bottom = Space.xl),
                        verticalArrangement = Arrangement.spacedBy(Space.m),
                    ) {
                        if (header != null) item { header() }
                        itemsIndexed(state.items, key = { _, it -> it.id.toString() + (it.route?.toString() ?: "") }) { index, row ->
                            Column(Modifier.animateItem().appearOnce(index)) {
                                if (row.group != null && row.group != state.items.getOrNull(index - 1)?.group) {
                                    Text(
                                        row.group,
                                        Modifier.padding(horizontal = Space.gutter, vertical = Space.xs).semantics { heading() },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = AppTheme.accent,
                                    )
                                }
                                ItemRow(
                                    title = row.title, subtitle = row.subtitle, status = row.status, date = row.date,
                                    initials = row.initials, route = row.route, url = row.url,
                                    below = row.bar?.let { b -> { VoteBar(b.first, b.second, b.third) } },
                                    kind = vm.spec.kind, card = true,
                                )
                            }
                        }
                        if (state.loadingMore) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppTheme.accent) } }
                        if (state.error != null) item { ErrorState(state.error!!, vm::retry, Modifier.fillMaxWidth()) }
                    }
                }
            }
        }
    }
}

/** The first rows of a fresh list fade and rise 12dp with a 30ms stagger; later pages appear without it (docs 5.5). */
@Composable
fun Modifier.appearOnce(index: Int): Modifier {
    if (index >= 8) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(Motion.MEDIUM, delayMillis = index * 30, easing = Motion.emphasized)) }
    val rise = with(LocalDensity.current) { 12.dp.toPx() }
    return this.graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * rise }
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

/** One "filters" button with a count, the active filters as removable chips (docs 3.6). */
@Composable
private fun FilterBar(vm: ListViewModel<*>, ui: ListUiState) {
    val filters = vm.spec.filters
    if (filters.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val active = ui.toggles.size + ui.choices.values.count { it.isNotEmpty() } + ui.dates.size
    val c = AppTheme.colors
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(Radius.pill).background(if (active > 0) AppTheme.container else c.surfaceMuted).clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Tune, null, Modifier.size(18.dp), tint = AppTheme.accent)
            Text(stringResource(Res.string.filters), style = MaterialTheme.typography.labelLarge)
            if (active > 0) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(AppTheme.accent), contentAlignment = Alignment.Center) {
                    Text(active.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        filters.forEach { def ->
            val label = activeLabel(def, ui) ?: return@forEach
            Row(
                Modifier.clip(Radius.pill).background(AppTheme.container).clickable { clear(vm, def) }.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Content), maxLines = 1)
                Icon(Icons.Filled.Clear, stringResource(Res.string.clear_all), Modifier.size(16.dp))
            }
        }
    }
    if (open) FilterSheet(vm, ui) { open = false }
}

@Composable
private fun activeLabel(def: FilterDef, ui: ListUiState): String? = when (def) {
    is FilterDef.Toggle -> if (def.id in ui.toggles) stringResource(def.label) else null
    is FilterDef.Choice -> ui.choices[def.id]?.takeIf { it.isNotEmpty() }?.let { sel ->
        stringResource(def.label) + ": " + if (sel.size == 1) (ui.options[def.id]?.firstOrNull { it.value in sel }?.label ?: sel.first()) else sel.size.toString()
    }
    is FilterDef.DateRange -> ui.dates[def.id]?.let { r ->
        stringResource(def.label) + ": " + listOf(r.from?.let(IsraelTime::date) ?: "…", r.to?.let(IsraelTime::date) ?: "…").joinToString("–")
    }
}

private fun clear(vm: ListViewModel<*>, def: FilterDef) = when (def) {
    is FilterDef.Toggle -> vm.toggle(def.id)
    is FilterDef.Choice -> vm.setChoice(def.id, emptySet())
    is FilterDef.DateRange -> vm.setDates(def.id, null)
}

private fun today(): LocalDate = Clock.System.now().toLocalDateTime(IsraelTime.zone).date

/** Every filter of the list in one bottom sheet; changes apply at once. */
@Composable
private fun FilterSheet(vm: ListViewModel<*>, ui: ListUiState, onClose: () -> Unit) {
    val state by vm.pagerState.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<FilterDef.DateRange?>(null) }
    AppSheet(stringResource(Res.string.filters), onClose) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.l)) {
            val toggles = vm.spec.filters.filterIsInstance<FilterDef.Toggle>()
            if (toggles.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                toggles.forEach { t -> ChoicePill(stringResource(t.label), t.id in ui.toggles, { vm.toggle(t.id) }) }
            }
            vm.spec.filters.forEach { def ->
                when (def) {
                    is FilterDef.Toggle -> Unit
                    is FilterDef.Choice -> {
                        LaunchedEffect(def.id) { vm.loadOptions(def) }
                        Text(stringResource(def.label), style = MaterialTheme.typography.titleMedium)
                        val options = ui.options[def.id]
                        val selected = ui.choices[def.id].orEmpty()
                        fun pick(v: String) = vm.setChoice(def.id, when {
                            !def.multi -> if (v in selected) emptySet() else setOf(v)
                            v in selected -> selected - v
                            else -> selected + v
                        })
                        when {
                            options == null -> CircularProgressIndicator(Modifier.size(24.dp))
                            options.size <= 8 -> FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                                options.forEach { o -> ChoicePill(o.label, o.value in selected, { pick(o.value) }) }
                            }
                            else -> SearchableChecklist(options, selected, ::pick)
                        }
                    }
                    is FilterDef.DateRange -> {
                        Text(stringResource(def.label), style = MaterialTheme.typography.titleMedium)
                        val current = ui.dates[def.id]
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                            listOf(Res.string.date_last_week to 7, Res.string.date_last_month to 31, Res.string.date_last_year to 365).forEach { (l, days) ->
                                val from = today().minus(DatePeriod(days = days))
                                ChoicePill(stringResource(l), current?.from == from && current.to == null, { vm.setDates(def.id, DateRangeValue(from, null)) })
                            }
                            ChoicePill(stringResource(Res.string.date_custom), current != null && current.to != null, { picker = def })
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            TextButton(onClick = vm::clearFilters) { Text(stringResource(Res.string.clear_all)) }
            Box(Modifier.weight(1f))
            androidx.compose.material3.Button(onClick = onClose) {
                Text(state.count?.let { stringResource(Res.string.show_n_results, it) } ?: stringResource(Res.string.ok))
            }
        }
    }
    picker?.let { def ->
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { picker = null },
            confirmButton = {
                TextButton(onClick = {
                    fun day(ms: Long?) = ms?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date }
                    val from = day(pickerState.selectedStartDateMillis)
                    val to = day(pickerState.selectedEndDateMillis)
                    vm.setDates(def.id, if (from == null && to == null) null else DateRangeValue(from, to))
                    picker = null
                }) { Text(stringResource(Res.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picker = null }) { Text(stringResource(Res.string.cancel)) } },
        ) { DateRangePicker(pickerState, Modifier.weight(1f)) }
    }
}

/** Long option lists (statuses, committees) as a searchable checklist. */
@Composable
private fun SearchableChecklist(options: List<Option>, selected: Set<String>, onPick: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    SearchPill(q, { q = it }, stringResource(Res.string.search), Modifier.padding(horizontal = 0.dp))
    val shown = options.filter { q.isBlank() || it.label.contains(q.trim()) }
    Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
        shown.forEach { o ->
            Row(
                Modifier.fillMaxWidth().clip(Radius.tile).clickable { onPick(o.value) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(o.value in selected, { onPick(o.value) }, colors = CheckboxDefaults.colors(checkedColor = AppTheme.accent))
                DataText(o.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
