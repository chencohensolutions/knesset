package com.chencohensolutions.knesset.ui.explorer

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.core.odata.F
import com.chencohensolutions.knesset.core.odata.IsraelTime
import com.chencohensolutions.knesset.core.odata.KnessetError
import com.chencohensolutions.knesset.core.odata.ODataQuery
import com.chencohensolutions.knesset.core.odata.parseODataInstant
import com.chencohensolutions.knesset.core.paging.PageResult
import com.chencohensolutions.knesset.core.paging.Pager
import com.chencohensolutions.knesset.core.platform.UrlOpener
import com.chencohensolutions.knesset.data.repo.Api
import com.chencohensolutions.knesset.data.repo.FetchMode
import com.chencohensolutions.knesset.data.repo.FieldDef
import com.chencohensolutions.knesset.data.repo.LookupRepo
import com.chencohensolutions.knesset.data.repo.MetadataParser
import com.chencohensolutions.knesset.domain.Documents
import com.chencohensolutions.knesset.resources.*
import com.chencohensolutions.knesset.ui.ExplorerItemRoute
import com.chencohensolutions.knesset.ui.ExplorerSetRoute
import com.chencohensolutions.knesset.ui.LocalNav
import com.chencohensolutions.knesset.ui.components.DataText
import com.chencohensolutions.knesset.ui.components.DetailTopBar
import com.chencohensolutions.knesset.ui.components.EmptyState
import com.chencohensolutions.knesset.ui.components.ErrorState
import com.chencohensolutions.knesset.ui.components.ItemRow
import com.chencohensolutions.knesset.ui.components.LoadingState
import com.chencohensolutions.knesset.ui.components.OfflineBanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

enum class Op(val symbol: String) { EQ("="), NE("≠"), CONTAINS("∋"), GT(">"), GE("≥"), LT("<"), LE("≤"), IS_NULL("∅"), NOT_NULL("≠∅") }

data class FieldFilter(val field: FieldDef, val op: Op, val value: String)

object ExplorerFilters {
    /** Turns one user filter into a `$filter` fragment typed by the field, or null when the value does not fit. */
    fun fragment(f: FieldFilter): String? {
        val name = f.field.name
        if (f.op == Op.IS_NULL) return F.isNull(name)
        if (f.op == Op.NOT_NULL) return F.isNotNull(name)
        val v = f.value.trim()
        if (v.isEmpty()) return null
        val literal = when {
            f.field.isString -> F.literal(v)
            f.field.isBool -> when (v.lowercase()) { "true", "1", "כן", "yes" -> "true"; "false", "0", "לא", "no" -> "false"; else -> return null }
            f.field.isNumber -> v.toLongOrNull()?.toString() ?: return null
            f.field.isDate -> parseDate(v)?.let { F.dateLiteral(it) } ?: return null
            else -> F.literal(v)
        }
        return when (f.op) {
            Op.CONTAINS -> if (f.field.isString) "contains($name,$literal)" else null
            Op.EQ -> "$name eq $literal"
            Op.NE -> "$name ne $literal"
            Op.GT -> "$name gt $literal"
            Op.GE -> "$name ge $literal"
            Op.LT -> "$name lt $literal"
            Op.LE -> "$name le $literal"
            else -> null
        }
    }

    /** `dd/MM/yyyy`, `yyyy-MM-dd` or a full ISO timestamp, as Israel time. */
    fun parseDate(text: String): kotlin.time.Instant? = runCatching {
        val dmy = Regex("""^(\d{1,2})[/.](\d{1,2})[/.](\d{4})$""").find(text)
        when {
            dmy != null -> IsraelTime.startOfDay(LocalDate(dmy.groupValues[3].toInt(), dmy.groupValues[2].toInt(), dmy.groupValues[1].toInt()))
            Regex("""^\d{4}-\d{2}-\d{2}$""").matches(text) -> IsraelTime.startOfDay(LocalDate.parse(text))
            else -> parseODataInstant(text)
        }
    }.getOrNull()

    fun display(value: kotlinx.serialization.json.JsonElement?): String? = when (value) {
        null, JsonNull -> null
        is JsonPrimitive -> value.contentOrNull?.let { s ->
            if (Regex("""^\d{4}-\d{2}-\d{2}T""").containsMatchIn(s)) runCatching { IsraelTime.dateTime(parseODataInstant(s)) }.getOrDefault(s) else s
        }
        else -> value.toString()
    }
}

// ---------- Set list ----------

@Composable
fun ExplorerScreen() {
    val lookups = koinInject<LookupRepo>()
    var sets by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<KnessetError?>(null) }
    var filter by remember { mutableStateOf("") }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        error = null
        try { sets = lookups.serviceDocument().sorted() } catch (e: KnessetError) { error = e }
    }
    val nav = LocalNav.current
    Scaffold(topBar = { DetailTopBar(stringResource(Res.string.data_explorer)) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(filter, { filter = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), placeholder = { Text(stringResource(Res.string.search)) }, singleLine = true)
            when {
                error != null -> ErrorState(error!!, { attempt++ })
                sets == null -> LoadingState()
                else -> {
                    val shown = sets!!.filter { it.contains(filter, ignoreCase = true) }
                    Text(stringResource(Res.string.explorer_sets, shown.size), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(shown, key = { it }) { name ->
                            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { nav.navigate(ExplorerSetRoute(name)) }.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                Text(name, fontFamily = FontFamily.Monospace)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

// ---------- One entity set ----------

data class ExplorerSetState(
    val fields: List<FieldDef> = emptyList(),
    val filters: List<FieldFilter> = emptyList(),
    val sortField: String? = null,
    val sortDesc: Boolean = true,
    val metadataError: KnessetError? = null,
)

class ExplorerSetViewModel(val name: String, private val api: Api, private val lookups: LookupRepo) : ViewModel() {
    private val _state = MutableStateFlow(ExplorerSetState())
    val state: StateFlow<ExplorerSetState> = _state.asStateFlow()

    val pager = Pager<JsonObject>(viewModelScope, pageSize = 50) { skip, top, withCount, force ->
        val s = _state.value
        val q = ODataQuery(name).apply {
            s.filters.forEach { filter(ExplorerFilters.fragment(it)) }
            val sort = s.sortField ?: if (s.fields.any { it.name == "Id" } || s.fields.isEmpty()) "Id" else null
            if (sort != null) orderBy = sort + if (s.sortDesc) " desc" else ""
            // Never request hidden fields such as the lobbyist IdentityNumber.
            if (s.fields.isNotEmpty()) select = s.fields.map { it.name }
            this.top = top; this.skip = skip; count = withCount
        }
        val page = api.rawPage(q, Ttl.LIST, if (force) FetchMode.FORCE else FetchMode.NORMAL)
        PageResult(page.rows, page.count, page.offline)
    }

    init {
        viewModelScope.launch {
            try {
                val fields = MetadataParser.parse(lookups.metadataXml())[name].orEmpty()
                _state.update { it.copy(fields = fields) }
            } catch (e: KnessetError) {
                _state.update { it.copy(metadataError = e) }
            }
            if (name in SENSITIVE_SETS && _state.value.fields.isEmpty()) {
                // Without the field list the hidden fields cannot be excluded, so do not load at all.
                return@launch
            }
            pager.refresh()
        }
    }

    fun addFilter(f: FieldFilter) { _state.update { it.copy(filters = it.filters + f) }; pager.refresh() }
    fun removeFilter(f: FieldFilter) { _state.update { it.copy(filters = it.filters - f) }; pager.refresh() }
    fun sortBy(field: String) {
        _state.update { if (it.sortField == field) it.copy(sortDesc = !it.sortDesc) else it.copy(sortField = field, sortDesc = true) }
        pager.refresh()
    }

    companion object {
        val SENSITIVE_SETS = setOf("V_Lobbyists")
    }
}

private fun JsonObject.str(key: String) = ExplorerFilters.display(this[key])

@Composable
fun ExplorerSetScreen(name: String) {
    val api = koinInject<Api>()
    val lookups = koinInject<LookupRepo>()
    val vm = viewModel(key = "explorer$name") { ExplorerSetViewModel(name, api, lookups) }
    val s by vm.state.collectAsStateWithLifecycle()
    val p by vm.pager.state.collectAsStateWithLifecycle()
    var addOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    val nav = LocalNav.current
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 10 } }
    LaunchedEffect(nearEnd, p.items.size) { if (nearEnd) vm.pager.loadMore() }

    Scaffold(topBar = { DetailTopBar(name) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { AssistChip(onClick = { addOpen = true }, label = { Text(stringResource(Res.string.explorer_add_filter)) }, leadingIcon = { Icon(Icons.Filled.Add, null) }, enabled = s.fields.isNotEmpty()) }
                item {
                    Box {
                        AssistChip(onClick = { sortOpen = true }, label = { Text(s.sortField?.let { it + if (s.sortDesc) " ↓" else " ↑" } ?: stringResource(Res.string.sort)) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) }, enabled = s.fields.isNotEmpty())
                        DropdownMenu(sortOpen, { sortOpen = false }) {
                            s.fields.forEach { f -> DropdownMenuItem(text = { Text(f.name) }, onClick = { vm.sortBy(f.name); sortOpen = false }) }
                        }
                    }
                }
                items(s.filters) { f ->
                    InputChip(selected = true, onClick = { vm.removeFilter(f) }, label = { Text(com.chencohensolutions.knesset.domain.Bidi.ltr(f.field.name + " " + f.op.symbol) + " " + com.chencohensolutions.knesset.domain.Bidi.isolate(f.value)) }, trailingIcon = { Icon(Icons.Filled.Clear, stringResource(Res.string.clear_all)) })
                }
            }
            p.count?.let { Text(stringResource(Res.string.results_count, it), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium) }
            if (p.offline) OfflineBanner()
            when {
                p.loadingFirst -> LoadingState()
                p.error != null && p.items.isEmpty() -> ErrorState(p.error!!, vm.pager::retry)
                p.isEmpty -> EmptyState()
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(p.items) { _, row ->
                        val flat = row.mapValues { it.value.let { v -> ExplorerFilters.display(v) } }
                        val id = row.str("Id")?.toIntOrNull()
                        val title = MetadataParser.titleOf(flat)
                        ItemRow(
                            title = title,
                            subtitle = flat.entries.filter { it.key != "Id" && it.value != null && it.value != title }.take(3).joinToString(" · ") { "${it.key}: ${it.value}" },
                            route = id?.let { ExplorerItemRoute(name, it) },
                        )
                        HorizontalDivider()
                    }
                    if (p.loadingMore) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
            }
        }
    }
    if (addOpen) FilterDialog(s.fields, onDismiss = { addOpen = false }, onAdd = { vm.addFilter(it); addOpen = false })
}

@Composable
private fun FilterDialog(fields: List<FieldDef>, onDismiss: () -> Unit, onAdd: (FieldFilter) -> Unit) {
    var field by remember { mutableStateOf(fields.firstOrNull { it.name == "Name" } ?: fields.first()) }
    var op by remember { mutableStateOf(if (field.isString) Op.CONTAINS else Op.EQ) }
    var value by remember { mutableStateOf("") }
    var fieldOpen by remember { mutableStateOf(false) }
    var opOpen by remember { mutableStateOf(false) }
    val candidate = FieldFilter(field, op, value)
    val valid = ExplorerFilters.fragment(candidate) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.explorer_add_filter)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { fieldOpen = true }) { Text("${field.name} (${field.type})") }
                    DropdownMenu(fieldOpen, { fieldOpen = false }) {
                        fields.forEach { f -> DropdownMenuItem(text = { Text("${f.name} (${f.type})") }, onClick = { field = f; op = if (f.isString) Op.CONTAINS else Op.EQ; fieldOpen = false }) }
                    }
                }
                Box {
                    OutlinedButton(onClick = { opOpen = true }) { Text(op.symbol) }
                    DropdownMenu(opOpen, { opOpen = false }) {
                        Op.entries.filter { it != Op.CONTAINS || field.isString }.forEach { o -> DropdownMenuItem(text = { Text(o.symbol + "  " + o.name.lowercase()) }, onClick = { op = o; opOpen = false }) }
                    }
                }
                if (op != Op.IS_NULL && op != Op.NOT_NULL) {
                    OutlinedTextField(value, { value = it }, singleLine = true, placeholder = { Text(if (field.isDate) "dd/MM/yyyy" else field.type) })
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onAdd(candidate) }) { Text(stringResource(Res.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

// ---------- One row ----------

@Composable
fun ExplorerItemScreen(name: String, id: Int) {
    val api = koinInject<Api>()
    val lookups = koinInject<LookupRepo>()
    val opener = koinInject<UrlOpener>()
    val nav = LocalNav.current
    var row by remember { mutableStateOf<JsonObject?>(null) }
    var error by remember { mutableStateOf<KnessetError?>(null) }
    var offline by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        error = null
        try {
            val fields = runCatching { MetadataParser.parse(lookups.metadataXml())[name].orEmpty() }.getOrDefault(emptyList())
            if (name in ExplorerSetViewModel.SENSITIVE_SETS && fields.isEmpty()) throw KnessetError.NotFound()
            val q = ODataQuery(name).apply { filter("Id eq $id"); if (fields.isNotEmpty()) select = fields.map { it.name } }
            val page = api.rawPage(q, Ttl.DETAIL)
            offline = page.offline
            row = page.rows.firstOrNull() ?: throw KnessetError.NotFound()
        } catch (e: KnessetError) { error = e }
    }
    Scaffold(topBar = { DetailTopBar("$name · $id") }) { padding ->
        when {
            error != null -> ErrorState(error!!, { attempt++ }, Modifier.padding(padding).fillMaxSize())
            row == null -> LoadingState(Modifier.padding(padding).fillMaxSize())
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (offline) item { OfflineBanner() }
                items(row!!.entries.filter { it.key !in MetadataParser.HIDDEN_FIELDS }.toList(), key = { it.key }) { (key, value) ->
                    val text = ExplorerFilters.display(value)
                    val target = MetadataParser.target(key)
                    val asInt = text?.toIntOrNull()
                    val isLink = key.endsWith("FilePath") || key.endsWith("Url")
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(key, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                        when {
                            text == null -> Text("—", color = MaterialTheme.colorScheme.outline)
                            target != null && asInt != null && asInt > 0 -> TextButton(onClick = { nav.navigate(ExplorerItemRoute(target, asInt)) }, contentPadding = PaddingValues(0.dp)) { Text("$text → $target") }
                            isLink -> Row(verticalAlignment = Alignment.CenterVertically) {
                                DataText(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { Documents.normalizeUrl(text)?.let(opener::open) }) { Icon(Icons.Outlined.OpenInNew, stringResource(Res.string.open)) }
                            }
                            else -> DataText(text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
