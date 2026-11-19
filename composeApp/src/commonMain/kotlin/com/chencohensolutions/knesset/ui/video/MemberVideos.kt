package com.chencohensolutions.knesset.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
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
import com.chencohensolutions.knesset.core.paging.PageResult
import com.chencohensolutions.knesset.core.paging.Pager
import com.chencohensolutions.knesset.core.platform.UrlOpener
import com.chencohensolutions.knesset.data.dto.CommitteeSessionDto
import com.chencohensolutions.knesset.data.dto.PersonDto
import com.chencohensolutions.knesset.data.dto.PersonToPositionDto
import com.chencohensolutions.knesset.data.dto.PlenumSessionDto
import com.chencohensolutions.knesset.data.dto.PlenumVoteResultDto
import com.chencohensolutions.knesset.data.repo.Api
import com.chencohensolutions.knesset.data.repo.FetchMode
import com.chencohensolutions.knesset.data.repo.KnessetContext
import com.chencohensolutions.knesset.data.repo.Loader
import com.chencohensolutions.knesset.data.repo.PeopleRepo
import com.chencohensolutions.knesset.data.video.KnessetVodSource
import com.chencohensolutions.knesset.data.video.VideoLinkSource
import com.chencohensolutions.knesset.domain.People
import com.chencohensolutions.knesset.resources.*
import com.chencohensolutions.knesset.ui.LocalNav
import com.chencohensolutions.knesset.ui.MemberVideosRoute
import com.chencohensolutions.knesset.ui.SittingRoute
import com.chencohensolutions.knesset.ui.VideoPlayerRoute
import com.chencohensolutions.knesset.ui.components.DetailTopBar
import com.chencohensolutions.knesset.ui.components.EmptyState
import com.chencohensolutions.knesset.ui.components.ErrorState
import com.chencohensolutions.knesset.ui.components.ItemRow
import com.chencohensolutions.knesset.ui.components.LoadingState
import com.chencohensolutions.knesset.ui.people.MembersViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Instant

/** A committee the member sat on, with the membership dates that bound its sessions. */
data class Membership(val committeeId: Int, val committeeName: String, val start: Instant?, val end: Instant?)

data class VideoRow(val sessionId: Int, val committee: String, val at: Instant?, val firstItem: String?, val items: List<String>)

object MemberVideoLogic {
    /** Committee memberships of a person, optionally limited to one Knesset (plan section 8). */
    fun memberships(positions: List<PersonToPositionDto>, knesset: Int?): List<Membership> = positions
        .filter { it.committeeID != null && (knesset == null || it.knessetNum == knesset) }
        .map { Membership(it.committeeID!!, it.committeeName?.trim() ?: "#${it.committeeID}", it.startDate, it.finishDate) }

    /**
     * `(CommitteeID eq a or ...) and StartDate ge <earliest start> and StartDate lt now`. The server refuses
     * (HTTP 473) a filter with a date range per committee, so each membership's own dates are checked on the
     * device with [inMembership]. At most 20 committees (the server refuses longer `or` chains with 400).
     */
    fun sessionFilter(memberships: List<Membership>, now: Instant, onlyCommittee: Int? = null): String? {
        val chosen = memberships.filter { onlyCommittee == null || it.committeeId == onlyCommittee }
        if (chosen.isEmpty()) return null
        val ids = chosen.map { it.committeeId }.distinct().take(MAX_COMMITTEES)
        val earliest = chosen.filter { it.committeeId in ids }.let { list -> if (list.any { it.start == null }) null else list.mapNotNull { it.start }.minOrNull() }
        return F.and(F.anyOf("CommitteeID", ids), earliest?.let { F.ge("StartDate", it) }, "StartDate lt ${F.dateLiteral(now)}")
    }

    /** Whether a session falls inside one of the member's memberships of its committee. */
    fun inMembership(memberships: List<Membership>, committeeId: Int?, at: Instant?): Boolean =
        memberships.any { m -> m.committeeId == committeeId && (at == null || ((m.start == null || at >= m.start) && (m.end == null || at <= m.end))) }

    const val MAX_COMMITTEES = 20
}

data class MemberVideosState(
    val name: String = "",
    val memberships: List<Membership>? = null,
    val committee: Int? = null,
    val query: String = "",
    val error: KnessetError? = null,
    val sittings: List<Pair<Int, String>>? = null,
)

class MemberVideosViewModel(
    private val personId: Int,
    private val api: Api,
    private val vod: KnessetVodSource,
    private val ctx: KnessetContext,
) : ViewModel() {
    private val _state = MutableStateFlow(MemberVideosState())
    val state: StateFlow<MemberVideosState> = _state.asStateFlow()

    /** Sessions of the member's committees, newest first, keeping only those with a broadcast row. */
    val pager = Pager<VideoRow>(viewModelScope, pageSize = 100) { skip, top, _, force ->
        val s = _state.value
        val filter = MemberVideoLogic.sessionFilter(s.memberships.orEmpty(), Clock.System.now(), s.committee) ?: return@Pager PageResult(emptyList())
        val q = ODataQuery("KNS_CommitteeSession").apply {
            filter(filter); orderBy = "StartDate desc,Id desc"; this.top = top; this.skip = skip
            select = listOf("Id", "CommitteeID", "StartDate")
            expand = listOf("KNS_Committee(\$select=Id,Name)", "KNS_CmtSessionItem(\$select=Id,Name,Ordinal)")
        }
        val page = api.page(q, CommitteeSessionDto.serializer(), Ttl.DETAIL, if (force) FetchMode.FORCE else FetchMode.NORMAL)
        val withBroadcast = vod.withBroadcastRow(page.rows.map { it.id })
        // Keep the raw page size so the pager knows whether more pages exist.
        val rows = page.rows.map { r ->
            val items = r.cmtSessionItem.sortedBy { it.ordinal ?: Int.MAX_VALUE }.mapNotNull { it.name }
            val keep = r.id in withBroadcast && MemberVideoLogic.inMembership(s.memberships.orEmpty(), r.committeeID, r.startDate)
            VideoRow(r.id, r.committee?.name?.trim() ?: "#${r.committeeID}", r.startDate, items.firstOrNull(), items) to keep
        }
        PageResult(rows.filter { it.second }.map { it.first } + List(rows.count { !it.second }) { PLACEHOLDER }, null, page.offline)
    }

    init {
        viewModelScope.launch {
            try {
                val l = Loader(api, FetchMode.NORMAL)
                val person = l.one("KNS_Person", personId, PersonDto.serializer())
                val positions = l.all(ODataQuery("KNS_PersonToPosition").apply { filter(F.eq("PersonID", personId)); filter(F.isNotNull("CommitteeID")); orderBy = "StartDate desc,Id desc" }, PersonToPositionDto.serializer(), Ttl.DETAIL, max = 500)
                _state.update { it.copy(name = People.fullName(person.firstName, person.lastName), memberships = MemberVideoLogic.memberships(positions, ctx.selection.value.selected)) }
                pager.refresh()
            } catch (e: KnessetError) {
                _state.update { it.copy(error = e) }
            }
        }
    }

    fun setCommittee(id: Int?) { _state.update { it.copy(committee = id) }; pager.refresh() }
    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    /** Plenum sittings in which the member voted (distinct SessionID of their vote results). */
    fun loadSittings() {
        if (_state.value.sittings != null) return
        viewModelScope.launch {
            val l = Loader(api, FetchMode.NORMAL)
            val sittings = l.optional {
                val q = ODataQuery("KNS_PlenumVoteResult").apply {
                    filter(F.eq("MkId", personId)); ctx.selection.value.selected?.let { k -> filter(com.chencohensolutions.knesset.data.repo.LookupRepo(api).knessetDateFilter(k, "VoteDate")) }
                    orderBy = "VoteDate desc,Id desc"; select = listOf("Id", "SessionID", "VoteDate")
                }
                val ids = all(q, PlenumVoteResultDto.serializer(), Ttl.DETAIL, max = 1_000).mapNotNull { it.sessionID }.distinct().take(100)
                val names = byIds("KNS_PlenumSession", ids, PlenumSessionDto.serializer(), listOf("Id", "Name", "StartDate")).associateBy { it.id }
                ids.map { it to (names[it]?.name ?: "#$it") }
            }.orEmpty()
            _state.update { it.copy(sittings = sittings) }
        }
    }

    companion object {
        /** Stands in for rows without a broadcast so page sizes stay intact; never shown. */
        val PLACEHOLDER = VideoRow(-1, "", null, null, emptyList())
    }
}

@Composable
fun MemberVideosScreen(personId: Int) {
    val api = koinInject<Api>()
    val vod = koinInject<KnessetVodSource>()
    val ctx = koinInject<KnessetContext>()
    val vm = viewModel(key = "videos$personId") { MemberVideosViewModel(personId, api, vod, ctx) }
    val s by vm.state.collectAsStateWithLifecycle()
    val p by vm.pager.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    Scaffold(topBar = { DetailTopBar(listOf(stringResource(Res.string.videos), s.name).filter { it.isNotBlank() }.joinToString(" · ")) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                stringResource(Res.string.videos_disclaimer),
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(Res.string.videos_committees)) })
                Tab(tab == 1, { tab = 1; vm.loadSittings() }, text = { Text(stringResource(Res.string.videos_plenum)) })
            }
            when {
                s.error != null -> ErrorState(s.error!!, null)
                s.memberships == null -> LoadingState()
                tab == 0 -> CommitteeVideos(vm, s, p, snackbar)
                else -> PlenumSittings(s)
            }
        }
    }
}

@Composable
private fun CommitteeVideos(vm: MemberVideosViewModel, s: MemberVideosState, p: com.chencohensolutions.knesset.core.paging.PagerState<VideoRow>, snackbar: SnackbarHostState) {
    val committees = s.memberships.orEmpty().distinctBy { it.committeeId }
    val play = rememberVideoOpener(snackbar)
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 10 } }
    LaunchedEffect(nearEnd, p.items.size) { if (nearEnd) vm.pager.loadMore() }
    OutlinedTextField(
        s.query, vm::setQuery, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        placeholder = { Text(stringResource(Res.string.videos_search_items)) }, leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
    )
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(committees, key = { it.committeeId }) { m ->
            FilterChip(s.committee == m.committeeId, { vm.setCommittee(if (s.committee == m.committeeId) null else m.committeeId) }, { Text(m.committeeName, maxLines = 1) })
        }
    }
    val downloadItems by koinInject<com.chencohensolutions.knesset.data.video.VideoDownloads>().items.collectAsStateWithLifecycle()
    val downloadsDone = downloadItems.filter { it.state == com.chencohensolutions.knesset.data.video.DownloadState.DONE }.map { it.url }.toSet()
    val rows = p.items.filter { it.sessionId > 0 && (s.query.isBlank() || it.items.any { i -> i.contains(s.query.trim()) }) }
    when {
        committees.isEmpty() -> EmptyState()
        p.loadingFirst -> LoadingState()
        p.error != null && rows.isEmpty() -> ErrorState(p.error!!, vm.pager::retry)
        rows.isEmpty() && p.endReached -> EmptyState()
        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(rows, key = { it.sessionId }) { r ->
                ItemRow(
                    title = r.committee,
                    subtitle = r.firstItem,
                    date = r.at?.let(IsraelTime::dateTime),
                    trailing = {
                        val saved = downloadsDone.contains(KnessetVodSource.streamUrl(r.sessionId))
                        Icon(
                            if (saved) Icons.Outlined.DownloadDone else Icons.Outlined.PlayCircle,
                            stringResource(if (saved) Res.string.dl_done else Res.string.play),
                            tint = if (saved) com.chencohensolutions.knesset.ui.theme.AppTheme.colors.positive else MaterialTheme.colorScheme.primary,
                        )
                    },
                    onClick = { play.committee(r.sessionId, r.committee, r.at) },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
            if (p.loadingMore || (!p.endReached && rows.size < 10)) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
    }
}

@Composable
private fun PlenumSittings(s: MemberVideosState) {
    val sittings = s.sittings
    when {
        sittings == null -> LoadingState()
        sittings.isEmpty() -> EmptyState()
        else -> LazyColumn(Modifier.fillMaxSize()) {
            item { Text(stringResource(Res.string.videos_plenum_hint), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
            items(sittings, key = { it.first }) { (id, name) ->
                ItemRow(name, route = SittingRoute(id))
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

/** Resolves a session's video on tap: play the stream in the app, else open the broadcast page, else say there is none. */
class VideoOpener(
    private val source: VideoLinkSource,
    private val nav: androidx.navigation.NavHostController,
    private val opener: UrlOpener,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val snackbar: SnackbarHostState,
    private val noVideo: String,
) {
    fun committee(sessionId: Int, title: String, at: Instant?) {
        scope.launch {
            val refs = try { source.forCommitteeSession(sessionId, title, at) } catch (e: KnessetError) { emptyList() }
            val stream = refs.firstOrNull { it.isStream }
            val page = refs.firstOrNull { !it.isStream }
            when {
                stream != null -> nav.navigate(VideoPlayerRoute(stream.url, title, page?.url))
                page != null -> opener.open(page.url)
                else -> snackbar.showSnackbar(noVideo)
            }
        }
    }
}

@Composable
fun rememberVideoOpener(snackbar: SnackbarHostState): VideoOpener {
    val source = koinInject<VideoLinkSource>()
    val opener = koinInject<UrlOpener>()
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val noVideo = stringResource(Res.string.no_video)
    return remember(nav) { VideoOpener(source, nav, opener, scope, snackbar, noVideo) }
}

/** The "Videos" entry in the People tab: pick a member first. */
@Composable
fun VideosEntryContent() {
    val ctx = koinInject<KnessetContext>()
    val repo = koinInject<PeopleRepo>()
    val vm = viewModel(key = "members") { MembersViewModel(repo, ctx) }
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(Res.string.videos_pick_member), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            s.query, { q -> vm.update { it.copy(query = q) } }, Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            placeholder = { Text(stringResource(Res.string.search)) }, leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true,
        )
        when {
            s.loading -> LoadingState()
            s.error != null -> ErrorState(s.error!!, null)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(s.filtered, key = { it.personId }) { m ->
                    ItemRow(m.name, m.factionName, initials = People.initials(m.firstName, m.lastName), route = MemberVideosRoute(m.personId))
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
