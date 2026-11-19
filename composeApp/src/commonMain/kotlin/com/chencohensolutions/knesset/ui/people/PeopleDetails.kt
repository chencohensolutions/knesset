package com.chencohensolutions.knesset.ui.people

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.core.odata.F
import com.chencohensolutions.knesset.core.odata.IsraelTime
import com.chencohensolutions.knesset.core.odata.ODataQuery
import com.chencohensolutions.knesset.data.dto.AgendaDto
import com.chencohensolutions.knesset.data.dto.BillInitiatorDto
import com.chencohensolutions.knesset.data.dto.FactionDto
import com.chencohensolutions.knesset.data.dto.GovMinistryDto
import com.chencohensolutions.knesset.data.dto.IsraelLawDto
import com.chencohensolutions.knesset.data.dto.IsraelLawMinistryDto
import com.chencohensolutions.knesset.data.dto.MkSiteCodeDto
import com.chencohensolutions.knesset.data.dto.PersonDto
import com.chencohensolutions.knesset.data.dto.PersonToPositionDto
import com.chencohensolutions.knesset.data.dto.PlenumVoteResultDto
import com.chencohensolutions.knesset.data.dto.QueryDto
import com.chencohensolutions.knesset.data.repo.KnessetContext
import com.chencohensolutions.knesset.data.repo.Loader
import com.chencohensolutions.knesset.data.repo.LookupRepo
import com.chencohensolutions.knesset.data.repo.MemberLogic
import com.chencohensolutions.knesset.data.repo.PeopleRepo
import com.chencohensolutions.knesset.domain.People
import com.chencohensolutions.knesset.resources.*
import com.chencohensolutions.knesset.ui.BillRoute
import com.chencohensolutions.knesset.ui.CommitteeRoute
import com.chencohensolutions.knesset.ui.FactionRoute
import com.chencohensolutions.knesset.ui.Kind
import com.chencohensolutions.knesset.ui.LawRoute
import com.chencohensolutions.knesset.ui.MemberRoute
import com.chencohensolutions.knesset.ui.MemberVideosRoute
import com.chencohensolutions.knesset.ui.MinistryRoute
import com.chencohensolutions.knesset.ui.MotionRoute
import com.chencohensolutions.knesset.ui.QuestionRoute
import com.chencohensolutions.knesset.ui.VoteRoute
import com.chencohensolutions.knesset.ui.components.ItemRow
import com.chencohensolutions.knesset.ui.components.LoadingState
import com.chencohensolutions.knesset.ui.components.SectionTabsScreen
import com.chencohensolutions.knesset.ui.components.rememberDetailVm
import com.chencohensolutions.knesset.ui.components.rememberListVm
import com.chencohensolutions.knesset.ui.detail.ActionLink
import com.chencohensolutions.knesset.ui.detail.DetailContent
import com.chencohensolutions.knesset.ui.detail.DetailScreen
import com.chencohensolutions.knesset.ui.detail.DetailTab
import com.chencohensolutions.knesset.ui.detail.LinkItem
import com.chencohensolutions.knesset.ui.detail.Section
import com.chencohensolutions.knesset.ui.detail.fact
import com.chencohensolutions.knesset.ui.detail.factsOf
import com.chencohensolutions.knesset.ui.favorites.FavoriteAction
import com.chencohensolutions.knesset.ui.list.FilterDef
import com.chencohensolutions.knesset.ui.list.ListContent
import com.chencohensolutions.knesset.ui.list.ListRow
import com.chencohensolutions.knesset.ui.list.ListSpec
import com.chencohensolutions.knesset.ui.list.SortOption
import com.chencohensolutions.knesset.ui.lobbyists.LobbyistsContent
import com.chencohensolutions.knesset.ui.lobbyists.rememberLobbyistsAvailable
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.koinInject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Replaced by localized text when rendered (the data layer has no access to string resources). */
const val VIDEOS_DISCLAIMER_MARKER = "\u0001videos_disclaimer"
const val VIDEOS_COMMITTEES_MARKER = "\u0001videos_open"

/** "Chair, Finance Committee" style label for one role row. */
fun roleLabel(p: PersonToPositionDto): String {
    val what = p.dutyDesc?.trim()?.takeIf { it.isNotEmpty() } ?: p.position?.description ?: "#${p.positionID}"
    val where = listOfNotNull(p.factionName, p.committeeName, p.govMinistryName).firstOrNull()?.trim()
    return if (where != null && !what.contains(where)) "$what · $where" else what
}

fun dateSpan(p: PersonToPositionDto): String =
    listOf(p.startDate?.let(IsraelTime::date) ?: "", p.finishDate?.let(IsraelTime::date) ?: "").joinToString("–")

private fun roleRoute(p: PersonToPositionDto): Any? = when {
    p.committeeID != null -> CommitteeRoute(p.committeeID)
    p.govMinistryID != null -> MinistryRoute(p.govMinistryID)
    p.factionID != null -> FactionRoute(p.factionID)
    else -> null
}

suspend fun loadMember(l: Loader, id: Int, lookups: LookupRepo): DetailContent = coroutineScope {
    val personAsync = async { l.one("KNS_Person", id, PersonDto.serializer()) }
    val statusesAsync = async { runCatching { lookups.statuses() }.getOrDefault(emptyMap()) }
    val positionsAsync = async { l.optional {
        all(ODataQuery("KNS_PersonToPosition").apply { filter(F.eq("PersonID", id)); orderBy = "StartDate desc,Id desc"; expand = listOf("KNS_Position") }, PersonToPositionDto.serializer(), Ttl.DETAIL, max = 500)
    }.orEmpty() }
    val siteIdAsync = async { l.optional { list(ODataQuery("KNS_MkSiteCode").apply { filter(F.eq("KnsID", id)) }, MkSiteCodeDto.serializer(), Ttl.LOOKUP).firstOrNull()?.siteId } }
    val billsAsync = async { l.optional {
        list(ODataQuery("KNS_BillInitiator").apply {
            filter(F.eq("PersonID", id)); orderBy = "Id desc"; top = 100
            expand = listOf("KNS_Bill(\$select=Id,Name,StatusID,KnessetNum,LastUpdatedDate)")
        }, BillInitiatorDto.serializer(), Ttl.DETAIL)
    }.orEmpty() }
    val votesAsync = async { l.optional {
        list(ODataQuery("KNS_PlenumVoteResult").apply {
            filter(F.eq("MkId", id)); orderBy = "VoteDate desc,Id desc"; top = 100
            expand = listOf("Vote(\$select=Id,VoteTitle,VoteDateTime)")
        }, PlenumVoteResultDto.serializer(), Ttl.DETAIL)
    }.orEmpty() }
    val queriesAsync = async { l.optional {
        list(ODataQuery("KNS_Query").apply { filter(F.eq("PersonID", id)); orderBy = "SubmitDate desc,Id desc"; top = 100; select = listOf("Id", "Name", "StatusID", "SubmitDate", "TypeDesc") }, QueryDto.serializer(), Ttl.DETAIL)
    }.orEmpty() }
    val motionsAsync = async { l.optional {
        list(ODataQuery("KNS_Agenda").apply { filter(F.eq("InitiatorPersonID", id)); orderBy = "Id desc"; top = 100; select = listOf("Id", "Name", "StatusID", "SubTypeDesc", "KnessetNum") }, AgendaDto.serializer(), Ttl.DETAIL)
    }.orEmpty() }
    val person = personAsync.await()
    val statuses = statusesAsync.await()
    val positions = positionsAsync.await()
    val siteId = siteIdAsync.await()
    val bills = billsAsync.await()
    val votes = votesAsync.await()
    val queries = queriesAsync.await()
    val motions = motionsAsync.await()

    val current = positions.filter { it.finishDate == null }
    val faction = current.firstOrNull { it.factionID != null } ?: positions.firstOrNull { it.factionID != null }
    val email = People.validEmail(person.email)
    val name = People.fullName(person.firstName, person.lastName)
    val voteCounts = votes.groupBy { it.resultDesc ?: "?" }.map { (k, v) -> LinkItem("$k: ${v.size}") }

    DetailContent(
        kind = Kind.MEMBER, id = id,
        title = name.ifBlank { "#$id" },
        subtitle = faction?.factionName?.trim(),
        webUrl = People.memberPage(siteId),
        lastUpdated = person.lastUpdatedDate,
        favoriteSubtitle = faction?.factionName?.trim(),
        tabs = listOf(
            DetailTab(Res.string.overview, listOf(
                Section.Facts(null, factsOf(
                    fact(Res.string.f_faction, faction?.factionName?.trim(), route = faction?.factionID?.let { FactionRoute(it) }),
                    fact(Res.string.f_gender, person.genderDesc),
                    fact(Res.string.f_email, email, url = email?.let { "mailto:$it" }),
                )),
                Section.Links(Res.string.current_roles, current.map { LinkItem(roleLabel(it), dateSpan(it), roleRoute(it)) }),
                Section.Actions(null, listOfNotNull(People.memberPage(siteId)?.let { ActionLink(Res.string.member_page, it) })),
            )),
            DetailTab(Res.string.roles, positions.groupBy { it.knessetNum ?: 0 }.toSortedMap(compareByDescending { it }).map { (k, rows) ->
                Section.Links(null, rows.map { LinkItem(roleLabel(it), listOfNotNull(if (k > 0) "כנסת $k" else null, dateSpan(it)).joinToString(" · "), roleRoute(it)) }, countInTitle = false)
            }),
            DetailTab(Res.string.section_bills, listOf(
                Section.Links(Res.string.initiated, bills.filter { it.isInitiator == true }.map { billItem(it, statuses) }),
                Section.Links(Res.string.joined, bills.filter { it.isInitiator != true }.map { billItem(it, statuses) }),
            )),
            DetailTab(Res.string.votes, listOf(
                Section.Links(Res.string.recent_vote_summary, voteCounts, countInTitle = false),
                Section.Links(Res.string.voting_record, votes.map { r ->
                    LinkItem(r.vote?.voteTitle ?: "#${r.voteID}", r.vote?.voteDateTime?.let(IsraelTime::dateTime), r.voteID?.let { VoteRoute(it) }, status = r.resultDesc)
                }),
            )),
            DetailTab(Res.string.videos, listOf(
                Section.Text(null, VIDEOS_DISCLAIMER_MARKER),
                Section.Links(null, listOf(
                    LinkItem(VIDEOS_COMMITTEES_MARKER, null, MemberVideosRoute(id)),
                ), countInTitle = false),
            )),
            DetailTab(Res.string.section_questions, listOf(
                Section.Links(null, queries.map { q -> LinkItem(q.name ?: "#${q.id}", listOfNotNull(q.typeDesc, q.submitDate?.let(IsraelTime::date)).joinToString(" · "), QuestionRoute(q.id), status = statuses[q.statusID]?.desc) }),
            )),
            DetailTab(Res.string.section_motions, listOf(
                Section.Links(null, motions.map { a -> LinkItem(a.name ?: "#${a.id}", a.subTypeDesc, MotionRoute(a.id), status = statuses[a.statusID]?.desc) }),
            )),
        ),
    )
}

private fun billItem(i: BillInitiatorDto, statuses: Map<Int, com.chencohensolutions.knesset.data.dto.StatusDto>) = LinkItem(
    i.bill?.name ?: "#${i.billID}",
    i.bill?.lastUpdatedDate?.let(IsraelTime::date),
    i.billID?.let { BillRoute(it) },
    status = statuses[i.bill?.statusID]?.desc,
)

suspend fun loadFaction(l: Loader, id: Int): DetailContent {
    val f = l.one("KNS_Faction", id, FactionDto.serializer())
    val rows = l.optional {
        all(ODataQuery("KNS_PersonToPosition").apply { filter(F.eq("FactionID", id)); orderBy = "Id"; expand = listOf("KNS_Person") }, PersonToPositionDto.serializer(), Ttl.DETAIL, max = 600)
    }.orEmpty()
    val members = MemberLogic.fromPositions(rows)
    return DetailContent(
        kind = Kind.FACTION, id = id, title = f.name?.trim() ?: "#$id",
        subtitle = f.knessetNum?.let { "כנסת $it" },
        lastUpdated = f.lastUpdatedDate,
        sections = listOf(
            Section.Facts(Res.string.details, factsOf(
                fact(Res.string.f_knesset, f.knessetNum),
                fact(Res.string.f_start, f.startDate),
                fact(Res.string.f_end, f.finishDate),
                fact(Res.string.f_member_count, members.size),
            )),
            Section.Links(Res.string.members, members.map { LinkItem(it.name, if (it.serving) null else "✕", MemberRoute(it.personId), initials = People.initials(it.firstName, it.lastName)) }),
        ),
    )
}

suspend fun loadMinistry(l: Loader, id: Int): DetailContent {
    val m = l.one("KNS_GovMinistry", id, GovMinistryDto.serializer())
    val holders = l.optional {
        all(ODataQuery("KNS_PersonToPosition").apply { filter(F.eq("GovMinistryID", id)); orderBy = "StartDate desc,Id desc"; expand = listOf("KNS_Person", "KNS_Position") }, PersonToPositionDto.serializer(), Ttl.DETAIL, max = 400)
    }.orEmpty()
    val questions = l.optional {
        list(ODataQuery("KNS_Query").apply { filter(F.eq("GovMinistryID", id)); orderBy = "SubmitDate desc,Id desc"; top = 50; select = listOf("Id", "Name", "SubmitDate", "TypeDesc") }, QueryDto.serializer(), Ttl.DETAIL)
    }.orEmpty()
    val laws = l.optional {
        val cat = m.categoryID ?: return@optional emptyList<LinkItem>()
        val rows = list(ODataQuery("KNS_IsraelLawMinistry").apply { filter(F.eq("MinistryCategoryID", cat)); top = 100 }, IsraelLawMinistryDto.serializer(), Ttl.DETAIL)
        val names = byIds("KNS_IsraelLaw", rows.mapNotNull { it.israelLawID }, IsraelLawDto.serializer(), listOf("Id", "Name")).associateBy { it.id }
        rows.mapNotNull { it.israelLawID }.distinct().map { LinkItem(names[it]?.name ?: "#$it", route = LawRoute(it)) }
    }.orEmpty()
    return DetailContent(
        kind = Kind.MINISTRY, id = id, title = m.name ?: "#$id",
        subtitle = m.categoryName,
        lastUpdated = m.lastUpdatedDate,
        sections = listOf(
            Section.Links(Res.string.ministers_over_time, holders.map {
                LinkItem(People.fullName(it.person?.firstName, it.person?.lastName), listOf(roleLabel(it), dateSpan(it)).joinToString(" · "), it.personID?.let { p -> MemberRoute(p) }, initials = People.initials(it.person?.firstName, it.person?.lastName))
            }),
            Section.Links(Res.string.section_questions, questions.map { q -> LinkItem(q.name ?: "#${q.id}", q.submitDate?.let(IsraelTime::date), QuestionRoute(q.id)) }),
            Section.Links(Res.string.laws_responsible, laws),
        ),
    )
}

val ministriesSpec = ListSpec(
    kind = Kind.MINISTRY,
    entitySet = "KNS_GovMinistry",
    serializer = GovMinistryDto.serializer(),
    search = { F.contains("Name", it) },
    knessetField = null,
    sorts = listOf(SortOption(Res.string.sort_name, "Name")),
    filters = listOf(FilterDef.Toggle("active", Res.string.filter_active, F.eq("IsActive", true))),
    ttl = Ttl.LOOKUP,
    toRow = { m, _ -> ListRow(m.id, m.name ?: "#${m.id}", subtitle = m.categoryName, route = MinistryRoute(m.id)) },
)

/** Factions of the selected Knesset with member counts from the shared members list. */
@Composable
fun FactionsContent() {
    val ctx = koinInject<KnessetContext>()
    val selection by ctx.selection.collectAsStateWithLifecycle()
    if (selection.selected == null) {
        ListContent(rememberListVm(factionsSpec, "factionsAll"))
        return
    }
    val repo = koinInject<PeopleRepo>()
    val vm = viewModel(key = "members") { MembersViewModel(repo, ctx) }
    val s by vm.state.collectAsStateWithLifecycle()
    if (s.loading) { LoadingState(); return }
    val counts = s.all.groupingBy { it.factionId }.eachCount()
    val factions = s.factions
    LazyColumn(Modifier.fillMaxSize()) {
        items(factions, key = { it.first }) { (id, name) ->
            ItemRow(name, subtitle = counts[id]?.toString(), route = FactionRoute(id))
            HorizontalDivider()
        }
    }
}

val factionsSpec = ListSpec(
    kind = Kind.FACTION,
    entitySet = "KNS_Faction",
    serializer = FactionDto.serializer(),
    search = { F.contains("Name", it) },
    baseFilters = listOf("Id ne 911"),
    sorts = listOf(SortOption(Res.string.sort_name, "KnessetNum desc,Name")),
    toRow = { f, _ -> ListRow(f.id, f.name?.trim() ?: "#${f.id}", subtitle = f.knessetNum?.let { "כנסת $it" }, route = FactionRoute(f.id)) },
)

@Composable
fun PeopleScreen() {
    val lobbyists = rememberLobbyistsAvailable()
    val sections = mutableListOf<Pair<StringResource, @Composable () -> Unit>>(
        Res.string.section_members to { MembersContent() },
        Res.string.section_factions to { FactionsContent() },
        Res.string.section_ministries to { ListContent(rememberListVm(ministriesSpec, "ministries")) },
    )
    sections += Res.string.videos to { com.chencohensolutions.knesset.ui.video.VideosEntryContent() }
    if (lobbyists) sections += Res.string.section_lobbyists to { LobbyistsContent() }
    SectionTabsScreen(Res.string.tab_people, sections)
}

@Composable
fun MemberScreen(id: Int) = DetailScreen(rememberDetailVm("member$id") { loadMember(this, id, it) }, "", { FavoriteAction(it) })

@Composable
fun FactionScreen(id: Int) = DetailScreen(rememberDetailVm("faction$id") { loadFaction(this, id) }, "", { FavoriteAction(it) })

@Composable
fun MinistryScreen(id: Int) = DetailScreen(rememberDetailVm("ministry$id") { loadMinistry(this, id) }, "", { FavoriteAction(it) })
