package com.chencohensolutions.knesset.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chencohensolutions.knesset.core.odata.KnessetError
import com.chencohensolutions.knesset.data.repo.Api
import com.chencohensolutions.knesset.data.repo.KnessetContext
import com.chencohensolutions.knesset.data.repo.LookupRepo
import com.chencohensolutions.knesset.data.repo.RecentSearchRepo
import com.chencohensolutions.knesset.resources.Res
import com.chencohensolutions.knesset.resources.clear_all
import com.chencohensolutions.knesset.resources.recent_searches
import com.chencohensolutions.knesset.resources.search_hint
import com.chencohensolutions.knesset.resources.show_all_n
import com.chencohensolutions.knesset.ui.KindListRoute
import com.chencohensolutions.knesset.ui.Kind
import com.chencohensolutions.knesset.ui.LocalNav
import com.chencohensolutions.knesset.ui.components.DetailTopBar
import com.chencohensolutions.knesset.ui.components.ItemRow
import com.chencohensolutions.knesset.ui.components.errorText
import com.chencohensolutions.knesset.ui.components.kindLabel
import com.chencohensolutions.knesset.ui.list.ListRow
import com.chencohensolutions.knesset.ui.list.ListViewModel
import com.chencohensolutions.knesset.ui.list.runSpec
import com.chencohensolutions.knesset.ui.specFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

data class SearchGroup(val kind: Kind, val rows: List<ListRow> = emptyList(), val count: Long? = null, val error: KnessetError? = null)

data class SearchState(val query: String = "", val running: Boolean = false, val groups: List<SearchGroup> = emptyList())

/** Global search (plan 2.8): each kind in turn through the shared request limiter, groups fill as they arrive. */
class SearchViewModel(
    private val api: Api,
    private val lookups: LookupRepo,
    private val ctx: KnessetContext,
    private val recent: RecentSearchRepo,
    initial: String,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState(query = initial))
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var job: Job? = null

    init { if (initial.isNotBlank()) schedule(initial, 0) }

    fun setQuery(text: String) {
        _state.update { it.copy(query = text) }
        schedule(text, 400)
    }

    private fun schedule(text: String, debounceMs: Long) {
        job?.cancel()
        val q = text.trim()
        if (q.length < ListViewModel.MIN_QUERY) {
            _state.update { it.copy(running = false, groups = emptyList()) }
            return
        }
        job = viewModelScope.launch {
            delay(debounceMs)
            _state.update { it.copy(running = true, groups = emptyList()) }
            val knesset = ctx.selection.value.selected
            for (kind in KINDS) {
                val spec = specFor(kind) ?: continue
                // Members are searched across all people; the rest follow the Knesset selector.
                val group = try {
                    val page = runSpec(spec, api, lookups, if (kind == Kind.MEMBER) null else knesset, q, TOP)
                    SearchGroup(kind, page.rows, page.count)
                } catch (e: KnessetError) {
                    SearchGroup(kind, error = e)
                }
                _state.update { it.copy(groups = it.groups + group) }
            }
            _state.update { it.copy(running = false) }
            if (_state.value.groups.any { it.rows.isNotEmpty() }) recent.add(q)
        }
    }

    companion object {
        const val TOP = 5
        val KINDS = listOf(Kind.MEMBER, Kind.BILL, Kind.LAW, Kind.COMMITTEE, Kind.VOTE, Kind.MOTION, Kind.QUESTION, Kind.SECONDARY_LAW)
    }
}

@Composable
fun SearchScreen(initial: String) {
    val api = koinInject<Api>()
    val lookups = koinInject<LookupRepo>()
    val ctx = koinInject<KnessetContext>()
    val recentRepo = koinInject<RecentSearchRepo>()
    val vm = viewModel(key = "search") { SearchViewModel(api, lookups, ctx, recentRepo, initial) }
    val state by vm.state.collectAsStateWithLifecycle()
    val recent by remember { recentRepo.all() }.collectAsStateWithLifecycle(emptyList())
    val nav = LocalNav.current
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (initial.isBlank()) runCatching { focus.requestFocus() } }

    Scaffold(topBar = {
        DetailTopBar("") {
            TextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp).focusRequester(focus),
                placeholder = { Text(stringResource(Res.string.search_hint)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Clear, stringResource(Res.string.clear_all)) } },
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
            )
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (state.query.trim().length < ListViewModel.MIN_QUERY && recent.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(Res.string.recent_searches), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { scope.launch { recentRepo.clear() } }) { Text(stringResource(Res.string.clear_all)) }
                        }
                    }
                    items(recent) { text ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.setQuery(text) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.outline)
                            Text(text, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
                        }
                    }
                }
                state.groups.filter { it.rows.isNotEmpty() || it.error != null }.forEach { g ->
                    item(key = "h${g.kind}") {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(kindLabel(g.kind)), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            if ((g.count ?: 0) > g.rows.size) {
                                TextButton(onClick = { nav.navigate(KindListRoute(g.kind.name, state.query.trim())) }) {
                                    Text(stringResource(Res.string.show_all_n, g.count ?: 0))
                                }
                            }
                        }
                    }
                    if (g.error != null) item { Text(errorText(g.error), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
                    items(g.rows, key = { "${g.kind}${it.id}" }) { r ->
                        ItemRow(r.title, r.subtitle, r.status, r.date, r.initials, r.route, r.url)
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}
