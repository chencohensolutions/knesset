package com.chencohensolutions.knesset.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.core.odata.F
import com.chencohensolutions.knesset.core.odata.IsraelTime
import com.chencohensolutions.knesset.core.odata.ODataQuery
import com.chencohensolutions.knesset.data.dto.PlenumSessionDto
import com.chencohensolutions.knesset.data.dto.PlenumVoteResultDto
import com.chencohensolutions.knesset.data.repo.Api
import com.chencohensolutions.knesset.data.repo.FavoritesRepo
import com.chencohensolutions.knesset.data.repo.KnessetContext
import com.chencohensolutions.knesset.data.repo.Loader
import com.chencohensolutions.knesset.data.repo.LookupRepo
import com.chencohensolutions.knesset.domain.VoteTally
import com.chencohensolutions.knesset.resources.*
import com.chencohensolutions.knesset.ui.CommitteesRoute
import com.chencohensolutions.knesset.ui.FavoritesRoute
import com.chencohensolutions.knesset.ui.KindListRoute
import com.chencohensolutions.knesset.ui.Kind
import com.chencohensolutions.knesset.ui.LocalNav
import com.chencohensolutions.knesset.ui.SittingRoute
import com.chencohensolutions.knesset.ui.committees.calendarSpec
import com.chencohensolutions.knesset.ui.committees.todayInIsrael
import com.chencohensolutions.knesset.ui.components.RootTopBar
import com.chencohensolutions.knesset.ui.components.rememberDetailVm
import com.chencohensolutions.knesset.ui.detail.DetailContent
import com.chencohensolutions.knesset.ui.detail.DetailStateContent
import com.chencohensolutions.knesset.ui.detail.LinkItem
import com.chencohensolutions.knesset.ui.detail.Section
import com.chencohensolutions.knesset.ui.legislation.billsSpec
import com.chencohensolutions.knesset.ui.legislation.lawsSpec
import com.chencohensolutions.knesset.ui.list.ListRow
import com.chencohensolutions.knesset.ui.list.runSpec
import com.chencohensolutions.knesset.ui.plenum.sittingsSpec
import com.chencohensolutions.knesset.ui.plenum.votesSpec
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock

private fun ListRow.toLink() = LinkItem(title, listOfNotNull(date, subtitle).joinToString(" · ").ifBlank { null }, route, url, initials, status, bar)

/** The dashboard for the selected Knesset (plan 2.4). */
suspend fun loadHome(l: Loader, api: Api, lookups: LookupRepo, selection: KnessetContext.Selection): DetailContent = coroutineScope {
    val k = selection.selected
    val live = selection.selected == null || selection.isCurrent
    val today = todayInIsrael()
    val ttl = if (live) Ttl.LIVE else Ttl.PAST_KNESSET

    val plenumAsync = async {
        l.optional {
            val q = ODataQuery("KNS_PlenumSession").apply {
                if (k != null) filter(F.eq("KnessetNum", k))
                if (live) { filter(F.ge("StartDate", IsraelTime.startOfDay(today))); orderBy = "StartDate asc,Id asc" } else orderBy = "StartDate desc,Id desc"
                top = 1; select = listOf("Id", "Name", "StartDate")
            }
            list(q, PlenumSessionDto.serializer(), ttl).map { LinkItem(it.name ?: "#${it.id}", it.startDate?.let(IsraelTime::dateTime), SittingRoute(it.id)) }
        }.orEmpty()
    }
    val sessionsAsync = async {
        l.optional {
            val spec = if (live) calendarSpec(today) else calendarSpec(today).let { s ->
                com.chencohensolutions.knesset.ui.list.ListSpec(s.kind, s.entitySet, s.serializer, s.select, s.expand, null, s.knessetField, sorts = listOf(com.chencohensolutions.knesset.ui.list.SortOption(Res.string.sort_date, "StartDate desc")), ttl = s.ttl, toRow = s.toRow)
            }
            runSpec(spec, api, lookups, k, null, 5, ttl, l.mode).rows.map { r -> r.toLink().copy(subtitle = listOfNotNull(r.group, r.date, r.subtitle).joinToString(" · ")) }
        }.orEmpty()
    }
    val votesAsync = async {
        l.optional {
            val rows = runSpec(votesSpec, api, lookups, k, null, 5, ttl, l.mode).rows
            rows.map { r ->
                async {
                    val results = l.optional {
                        all(ODataQuery("KNS_PlenumVoteResult").apply { filter(F.eq("VoteID", r.id)); orderBy = "Id"; select = listOf("Id", "ResultCode", "ResultDesc") }, PlenumVoteResultDto.serializer(), Ttl.DETAIL, max = 300)
                    }.orEmpty()
                    val t = VoteTally.of(results)
                    r.toLink().copy(bar = Triple(t.forCount, t.againstCount, t.abstainCount), status = if (t.total > 0) "${t.forCount}–${t.againstCount}" else null)
                }
            }.awaitAll()
        }.orEmpty()
    }
    val billsAsync = async { l.optional { runSpec(billsSpec, api, lookups, k, null, 5, ttl, l.mode).rows.map { it.toLink() } }.orEmpty() }
    val lawsAsync = async { l.optional { runSpec(lawsSpec, api, lookups, k, null, 3, Ttl.LIST, l.mode).rows.map { it.toLink() } }.orEmpty() }

    DetailContent(
        kind = null, id = 0, title = "",
        sections = listOf(
            Section.Links(if (live) Res.string.home_next_sitting else Res.string.home_last_sitting, plenumAsync.await(), countInTitle = false, showAll = KindListRoute(Kind.SITTING.name)),
            Section.Links(if (live) Res.string.home_upcoming_sessions else Res.string.home_last_sessions, sessionsAsync.await(), countInTitle = false, showAll = CommitteesRoute, showWhenEmpty = true),
            Section.Links(Res.string.home_latest_votes, votesAsync.await(), countInTitle = false, showAll = KindListRoute(Kind.VOTE.name), showWhenEmpty = true),
            Section.Links(Res.string.home_recent_bills, billsAsync.await(), countInTitle = false, showAll = KindListRoute(Kind.BILL.name), showWhenEmpty = true),
            Section.Links(Res.string.home_new_laws, lawsAsync.await(), countInTitle = false, showAll = KindListRoute(Kind.LAW.name), showWhenEmpty = true),
        ),
        lastUpdated = Clock.System.now(),
    )
}

@Composable
fun HomeScreen() {
    val ctx = koinInject<KnessetContext>()
    val api = koinInject<Api>()
    val selection by ctx.selection.collectAsStateWithLifecycle()
    val vm = rememberDetailVm("home${selection.selected}") { lookups -> loadHome(this, api, lookups, selection) }
    Scaffold(topBar = { RootTopBar(stringResource(Res.string.tab_home)) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            FavoritesStrip()
            DetailStateContent(vm)
        }
    }
}

/** Starred items as a horizontal strip, hidden when there are none (plan 2.4). */
@Composable
private fun FavoritesStrip() {
    val repo = koinInject<FavoritesRepo>()
    val favorites by remember { repo.all() }.collectAsStateWithLifecycle(emptyList())
    if (favorites.isEmpty()) return
    val nav = LocalNav.current
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { AssistChip(onClick = { nav.navigate(FavoritesRoute) }, label = { Text(stringResource(Res.string.favorites)) }, leadingIcon = { Icon(Icons.Filled.Star, null) }) }
        items(favorites, key = { "${it.kind}${it.id}" }) { f ->
            AssistChip(
                onClick = { nav.navigate(f.kind.route(f.id)) },
                label = { Text((if (f.hasUpdate) "● " else "") + f.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp)) },
            )
        }
    }
}
