package com.chencohensolutions.knesset.ui.people

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.chencohensolutions.knesset.ui.components.ChoicePill
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.core.odata.F
import com.chencohensolutions.knesset.core.odata.KnessetError
import com.chencohensolutions.knesset.data.dto.PersonDto
import com.chencohensolutions.knesset.data.repo.FetchMode
import com.chencohensolutions.knesset.data.repo.KnessetContext
import com.chencohensolutions.knesset.data.repo.Member
import com.chencohensolutions.knesset.data.repo.MemberLogic
import com.chencohensolutions.knesset.data.repo.PeopleRepo
import com.chencohensolutions.knesset.domain.People
import com.chencohensolutions.knesset.resources.Res
import com.chencohensolutions.knesset.resources.alphabetical
import com.chencohensolutions.knesset.resources.by_faction
import com.chencohensolutions.knesset.resources.clear_all
import com.chencohensolutions.knesset.resources.currently_serving
import com.chencohensolutions.knesset.resources.gender_female
import com.chencohensolutions.knesset.resources.gender_male
import com.chencohensolutions.knesset.resources.results_count
import com.chencohensolutions.knesset.resources.search
import com.chencohensolutions.knesset.resources.sort_name
import com.chencohensolutions.knesset.ui.Kind
import com.chencohensolutions.knesset.ui.MemberRoute
import com.chencohensolutions.knesset.ui.components.DataText
import com.chencohensolutions.knesset.ui.components.EmptyState
import com.chencohensolutions.knesset.ui.components.ErrorState
import com.chencohensolutions.knesset.ui.components.ItemRow
import com.chencohensolutions.knesset.ui.components.LoadingState
import com.chencohensolutions.knesset.ui.components.rememberListVm
import com.chencohensolutions.knesset.ui.list.ListContent
import com.chencohensolutions.knesset.ui.list.ListRow
import com.chencohensolutions.knesset.ui.list.ListSpec
import com.chencohensolutions.knesset.ui.list.SortOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

data class MembersState(
    val loading: Boolean = true,
    val error: KnessetError? = null,
    val all: List<Member> = emptyList(),
    val query: String = "",
    val faction: Int? = null,
    val gender: Int? = null,
    val servingOnly: Boolean = false,
    val byFaction: Boolean = true,
) {
    val filtered: List<Member> get() = all.filter { m ->
        (query.isBlank() || MemberLogic.matches(m, query)) &&
            (faction == null || m.factionId == faction) &&
            (gender == null || m.genderId == gender) &&
            (!servingOnly || m.serving)
    }
    val factions: List<Pair<Int, String>> get() = all.mapNotNull { m -> m.factionId?.let { it to (m.factionName ?: "#$it") } }.distinctBy { it.first }.sortedBy { it.second }
}

/** Members of the selected Knesset: all membership rows are loaded once (a few hundred) and filtered on the device. */
class MembersViewModel(private val repo: PeopleRepo, private val ctx: KnessetContext) : ViewModel() {
    private val _state = MutableStateFlow(MembersState())
    val state: StateFlow<MembersState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch { ctx.selection.map { it.selected }.distinctUntilChanged().collect { load(FetchMode.NORMAL) } }
    }

    fun load(mode: FetchMode) {
        val k = ctx.selection.value.selected ?: return
        job?.cancel()
        _state.update { it.copy(loading = it.all.isEmpty() || mode != FetchMode.FORCE, error = null, faction = null) }
        job = viewModelScope.launch {
            try {
                val members = repo.members(k, mode, ctx.selection.value.ttl(Ttl.LIST))
                _state.update { it.copy(loading = false, all = members) }
            } catch (e: KnessetError) {
                _state.update { it.copy(loading = false, error = e) }
            }
        }
    }

    fun update(block: (MembersState) -> MembersState) = _state.update(block)
}

/** For "all Knessets": every person, searched on the server. */
val personsSpec = ListSpec(
    kind = Kind.MEMBER,
    entitySet = "KNS_Person",
    serializer = PersonDto.serializer(),
    select = listOf("Id", "FirstName", "LastName", "GenderDesc", "IsCurrent"),
    search = { F.allTokensIn(it, "FirstName", "LastName") },
    knessetField = null,
    sorts = listOf(SortOption(Res.string.sort_name, "LastName,FirstName")),
    toRow = { p, _ ->
        ListRow(p.id, People.fullName(p.firstName, p.lastName), initials = People.initials(p.firstName, p.lastName), route = MemberRoute(p.id))
    },
)

@Composable
fun MembersContent() {
    val ctx = koinInject<KnessetContext>()
    val selection by ctx.selection.collectAsStateWithLifecycle()
    if (selection.selected == null) {
        ListContent(rememberListVm(personsSpec, "persons"))
        return
    }
    val repo = koinInject<PeopleRepo>()
    val vm = viewModel(key = "members") { MembersViewModel(repo, ctx) }
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        com.chencohensolutions.knesset.ui.components.SearchPill(s.query, { q -> vm.update { it.copy(query = q) } }, stringResource(Res.string.search))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ChoicePill(stringResource(Res.string.by_faction), s.byFaction, { vm.update { it.copy(byFaction = true) } }) }
            item { ChoicePill(stringResource(Res.string.alphabetical), !s.byFaction, { vm.update { it.copy(byFaction = false) } }) }
            item { ChoicePill(stringResource(Res.string.currently_serving), s.servingOnly, { vm.update { it.copy(servingOnly = !it.servingOnly) } }) }
            item { ChoicePill(stringResource(Res.string.gender_female), s.gender == 250, { vm.update { it.copy(gender = if (it.gender == 250) null else 250) } }) }
            item { ChoicePill(stringResource(Res.string.gender_male), s.gender == 251, { vm.update { it.copy(gender = if (it.gender == 251) null else 251) } }) }
            items(s.factions, key = { it.first }) { (id, name) ->
                ChoicePill(name, s.faction == id, { vm.update { it.copy(faction = if (it.faction == id) null else id) } }, Modifier.widthIn(max = 240.dp))
            }
        }
        val list = s.filtered
        Text(stringResource(Res.string.results_count, list.size), Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PullToRefreshBox(isRefreshing = false, onRefresh = { vm.load(FetchMode.FORCE) }, modifier = Modifier.fillMaxSize()) {
            when {
                s.loading -> LoadingState()
                s.error != null && s.all.isEmpty() -> ErrorState(s.error!!, { vm.load(FetchMode.NORMAL) })
                list.isEmpty() -> EmptyState()
                else -> MemberList(list, s.byFaction)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemberList(members: List<Member>, byFaction: Boolean) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (byFaction) {
            members.groupBy { it.factionName ?: "" }.toSortedMap().forEach { (faction, group) ->
                stickyHeader(key = "h$faction") {
                    Row(
                        Modifier.fillMaxWidth().background(com.chencohensolutions.knesset.ui.theme.AppTheme.colors.background).padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(com.chencohensolutions.knesset.ui.theme.FactionColors.of(group.first().factionId)))
                        DataText("$faction · ${group.size}", style = MaterialTheme.typography.titleSmall)
                    }
                }
                items(group, key = { "f$faction-${it.personId}" }) { MemberRow(it, showFaction = false, Modifier.animateItem()) }
            }
        } else {
            items(members, key = { it.personId }) { MemberRow(it, showFaction = true, Modifier.animateItem()) }
        }
    }
}

@Composable
private fun MemberRow(m: Member, showFaction: Boolean, modifier: Modifier = Modifier) {
    Box(modifier) {
        ItemRow(
            title = m.name.ifBlank { "#${m.personId}" },
            subtitle = if (showFaction) m.factionName else null,
            initials = People.initials(m.firstName, m.lastName),
            factionId = m.factionId,
            route = MemberRoute(m.personId),
            card = true,
        )
    }
}
