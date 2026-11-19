package com.chencohensolutions.knesset.ui.plenum

import androidx.compose.runtime.Composable
import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.core.odata.F
import com.chencohensolutions.knesset.core.odata.IsraelTime
import com.chencohensolutions.knesset.core.odata.ODataQuery
import com.chencohensolutions.knesset.data.dto.AgendaDto
import com.chencohensolutions.knesset.data.dto.PlenumSessionDto
import com.chencohensolutions.knesset.data.dto.PlenumVoteDto
import com.chencohensolutions.knesset.data.dto.PlenumVoteResultDto
import com.chencohensolutions.knesset.data.dto.PlmSessionItemDto
import com.chencohensolutions.knesset.data.dto.QueryDto
import com.chencohensolutions.knesset.data.repo.FetchMode
import com.chencohensolutions.knesset.data.repo.Loader
import com.chencohensolutions.knesset.data.repo.LookupRepo
import com.chencohensolutions.knesset.data.repo.PeopleRepo
import com.chencohensolutions.knesset.domain.Documents
import com.chencohensolutions.knesset.domain.ItemRefs
import com.chencohensolutions.knesset.domain.People
import com.chencohensolutions.knesset.domain.VoteTally
import com.chencohensolutions.knesset.resources.*
import com.chencohensolutions.knesset.ui.CommitteeRoute
import com.chencohensolutions.knesset.ui.Kind
import com.chencohensolutions.knesset.ui.MemberRoute
import com.chencohensolutions.knesset.ui.MinistryRoute
import com.chencohensolutions.knesset.ui.MotionRoute
import com.chencohensolutions.knesset.ui.QuestionRoute
import com.chencohensolutions.knesset.ui.SittingRoute
import com.chencohensolutions.knesset.ui.VoteRoute
import com.chencohensolutions.knesset.ui.VideoPlayerRoute
import com.chencohensolutions.knesset.data.video.VideoLinkSource
import com.chencohensolutions.knesset.ui.components.SectionTabsScreen
import com.chencohensolutions.knesset.ui.components.rememberDetailVm
import com.chencohensolutions.knesset.ui.components.rememberListVm
import com.chencohensolutions.knesset.ui.detail.DetailContent
import com.chencohensolutions.knesset.ui.detail.DetailScreen
import com.chencohensolutions.knesset.ui.detail.LinkItem
import com.chencohensolutions.knesset.ui.detail.Section
import com.chencohensolutions.knesset.ui.detail.fact
import com.chencohensolutions.knesset.ui.detail.factsOf
import com.chencohensolutions.knesset.ui.favorites.FavoriteAction
import com.chencohensolutions.knesset.ui.list.FilterDef
import com.chencohensolutions.knesset.ui.list.ListContent
import com.chencohensolutions.knesset.ui.list.ListRow
import com.chencohensolutions.knesset.ui.list.ListSpec
import com.chencohensolutions.knesset.ui.list.Option
import com.chencohensolutions.knesset.ui.list.SortOption
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val MOTION_STATUS_TYPE = 4
private const val QUERY_STATUS_TYPE = 1

private fun statusOptions(type: Int): suspend (com.chencohensolutions.knesset.ui.list.FilterContext) -> List<Option> = { ctx ->
    ctx.lookups.statuses().values.filter { it.typeID == type && !it.desc.isNullOrBlank() }.sortedBy { it.orderTransition ?: 0 }.map { Option(it.id.toString(), it.desc!!) }
}

val votesSpec = ListSpec(
    kind = Kind.VOTE,
    entitySet = "KNS_PlenumVote",
    serializer = PlenumVoteDto.serializer(),
    select = listOf("Id", "VoteTitle", "VoteSubject", "VoteDateTime", "VoteMethodDesc", "IsNoConfidenceInGov"),
    search = { F.contains("VoteTitle", it) },
    knessetField = null,
    knessetFilter = { k, lookups -> lookups.knessetDateFilter(k, "VoteDateTime") },
    sorts = listOf(SortOption(Res.string.sort_date, "VoteDateTime desc"), SortOption(Res.string.sort_date_asc, "VoteDateTime asc")),
    filters = listOf(
        FilterDef.DateRange("date", Res.string.filter_date, "VoteDateTime"),
        FilterDef.Choice("method", Res.string.filter_vote_method, multi = false,
            options = { listOf(Option("1", "אלקטרונית"), Option("2", "שמית")) },
            fragment = { v -> F.anyOf("VoteMethodID", v.mapNotNull { it.toIntOrNull() }) }),
        FilterDef.Toggle("noConfidence", Res.string.filter_no_confidence, F.eq("IsNoConfidenceInGov", true)),
    ),
    toRow = { v, _ ->
        ListRow(v.id, v.voteTitle ?: "#${v.id}", subtitle = listOfNotNull(v.voteSubject, v.voteMethodDesc).joinToString(" · ").ifBlank { null },
            date = v.voteDateTime?.let(IsraelTime::dateTime), route = VoteRoute(v.id))
    },
)

val sittingsSpec = ListSpec(
    kind = Kind.SITTING,
    entitySet = "KNS_PlenumSession",
    serializer = PlenumSessionDto.serializer(),
    select = listOf("Id", "Name", "Number", "StartDate", "IsSpecialMeeting", "KnessetNum"),
    search = { F.contains("Name", it) },
    sorts = listOf(SortOption(Res.string.sort_date, "StartDate desc"), SortOption(Res.string.sort_date_asc, "StartDate asc")),
    filters = listOf(
        FilterDef.DateRange("date", Res.string.filter_date, "StartDate"),
        FilterDef.Toggle("special", Res.string.filter_special, F.eq("IsSpecialMeeting", true)),
    ),
    toRow = { s, _ ->
        ListRow(s.id, s.name ?: "#${s.id}", date = s.startDate?.let(IsraelTime::dateTime),
            subtitle = s.number?.let { "#$it" }, route = SittingRoute(s.id))
    },
)

val motionsSpec = ListSpec(
    kind = Kind.MOTION,
    entitySet = "KNS_Agenda",
    serializer = AgendaDto.serializer(),
    select = listOf("Id", "Name", "SubTypeDesc", "StatusID", "ClassificationDesc", "KnessetNum", "LastUpdatedDate"),
    search = { F.contains("Name", it) },
    sorts = listOf(SortOption(Res.string.sort_last_updated, "LastUpdatedDate desc"), SortOption(Res.string.sort_name, "Name")),
    filters = listOf(
        FilterDef.Choice("status", Res.string.filter_status, multi = true, options = statusOptions(MOTION_STATUS_TYPE),
            fragment = { v -> F.anyOf("StatusID", v.mapNotNull { it.toIntOrNull() }) }),
        FilterDef.Choice("committee", Res.string.filter_committee, multi = false,
            options = { ctx -> ctx.lookups.committees(ctx.knesset).map { Option(it.id.toString(), it.name ?: "") } },
            fragment = { v -> F.anyOf("CommitteeID", v.mapNotNull { it.toIntOrNull() }) }),
    ),
    needsStatuses = true,
    toRow = { a, ctx ->
        ListRow(a.id, a.name ?: "#${a.id}", status = ctx.status(a.statusID), subtitle = a.subTypeDesc,
            date = a.lastUpdatedDate?.let(IsraelTime::date), route = MotionRoute(a.id))
    },
)

val questionsSpec = ListSpec(
    kind = Kind.QUESTION,
    entitySet = "KNS_Query",
    serializer = QueryDto.serializer(),
    select = listOf("Id", "Name", "TypeDesc", "StatusID", "SubmitDate", "ReplyMinisterDate", "KnessetNum"),
    search = { F.contains("Name", it) },
    sorts = listOf(SortOption(Res.string.sort_submitted, "SubmitDate desc"), SortOption(Res.string.sort_last_updated, "LastUpdatedDate desc"), SortOption(Res.string.sort_name, "Name")),
    filters = listOf(
        FilterDef.Choice("type", Res.string.filter_question_type, multi = true,
            options = { listOf(Option("רגילה", "רגילה"), Option("דחופה", "דחופה"), Option("ישירה", "ישירה")) },
            fragment = { v -> F.or(*v.map { F.eqText("TypeDesc", it) }.toTypedArray()) }),
        FilterDef.Choice("status", Res.string.filter_status, multi = true, options = statusOptions(QUERY_STATUS_TYPE),
            fragment = { v -> F.anyOf("StatusID", v.mapNotNull { it.toIntOrNull() }) }),
        FilterDef.Choice("ministry", Res.string.filter_ministry, multi = false,
            options = { ctx -> ctx.lookups.ministries().filter { it.isActive != false }.map { Option(it.id.toString(), it.name ?: "") } },
            fragment = { v -> F.anyOf("GovMinistryID", v.mapNotNull { it.toIntOrNull() }) }),
        FilterDef.Toggle("answered", Res.string.filter_answered, F.isNotNull("ReplyMinisterDate")),
        FilterDef.Toggle("unanswered", Res.string.filter_unanswered, F.isNull("ReplyMinisterDate")),
        FilterDef.DateRange("submitted", Res.string.filter_submitted, "SubmitDate"),
    ),
    needsStatuses = true,
    toRow = { q, ctx ->
        ListRow(q.id, q.name ?: "#${q.id}", status = ctx.status(q.statusID), subtitle = q.typeDesc,
            date = q.submitDate?.let(IsraelTime::date), route = QuestionRoute(q.id))
    },
)

@Composable
fun PlenumScreen() {
    SectionTabsScreen(
        Res.string.tab_plenum,
        listOf(
            Res.string.section_votes to { ListContent(rememberListVm(votesSpec, "votes")) },
            Res.string.section_sittings to { ListContent(rememberListVm(sittingsSpec, "sittings")) },
            Res.string.section_motions to { ListContent(rememberListVm(motionsSpec, "motions")) },
            Res.string.section_questions to { ListContent(rememberListVm(questionsSpec, "questions")) },
        ),
    )
}

// ---------- Details ----------

/** What a vote was about: the agenda item with the same ItemID tells its type. */
private suspend fun resolveItem(l: Loader, itemId: Int?): Any? {
    itemId ?: return null
    val item = l.optional {
        list(ODataQuery("KNS_PlmSessionItem").apply { filter(F.eq("ItemID", itemId)); top = 1; select = listOf("ItemID", "ItemTypeID") }, PlmSessionItemDto.serializer(), Ttl.DETAIL).firstOrNull()
    }
    return ItemRefs.kindOf(item?.itemTypeID)?.route(itemId)
}

/** Groups vote results by result (default) and by faction (plan 2.6). */
fun voteGroups(rows: List<PlenumVoteResultDto>, factionOf: (Int) -> String?): Pair<List<Pair<String, List<LinkItem>>>, List<Pair<String, List<LinkItem>>>> {
    val unique = rows.distinctBy { it.id }
    fun item(r: PlenumVoteResultDto, sub: String?) = LinkItem(
        People.fullName(r.firstName, r.lastName).ifBlank { "#${r.mkId}" }, sub, r.mkId?.let { MemberRoute(it) },
        initials = People.initials(r.firstName, r.lastName),
    )
    val tally = VoteTally.of(unique)
    val byResult = tally.byResult.map { (desc, _) ->
        desc to unique.filter { (it.resultDesc?.trim() ?: "?") == desc }.sortedBy { it.lastName }.map { item(it, it.mkId?.let(factionOf)) }
    }
    val byFaction = unique.groupBy { r -> r.mkId?.let(factionOf) ?: "—" }.toSortedMap().map { (f, list) ->
        f to list.sortedWith(compareBy({ it.resultCode }, { it.lastName })).map { item(it, it.resultDesc) }
    }
    return byResult to byFaction
}

suspend fun loadVote(l: Loader, id: Int, people: PeopleRepo): DetailContent = coroutineScope {
    val resultsAsync = async {
        l.all(ODataQuery("KNS_PlenumVoteResult").apply { filter(F.eq("VoteID", id)); orderBy = "Id" }, PlenumVoteResultDto.serializer(), Ttl.DETAIL, max = 400)
    }
    val v = l.one("KNS_PlenumVote", id, PlenumVoteDto.serializer(), listOf("KNS_PlenumSession"))
    val itemRoute = async { resolveItem(l, v.itemID) }
    val results = resultsAsync.await()
    val knesset = v.plenumSession?.knessetNum
    val factions = if (knesset != null) runCatching {
        people.members(knesset, if (l.mode == FetchMode.FORCE) FetchMode.NORMAL else l.mode, Ttl.LIST).associate { it.personId to it.factionName }
    }.getOrDefault(emptyMap()) else emptyMap()
    val (byResult, byFaction) = voteGroups(results) { factions[it] }
    DetailContent(
        kind = Kind.VOTE, id = id,
        title = v.voteTitle ?: "#$id",
        subtitle = listOfNotNull(v.voteDateTime?.let(IsraelTime::dateTime), v.voteSubject).joinToString(" · "),
        status = v.voteStatusDesc,
        lastUpdated = v.lastUpdatedDate,
        favoriteSubtitle = v.voteDateTime?.let(IsraelTime::date),
        sections = listOf(
            Section.Tally(Res.string.results, VoteTally.of(results)),
            Section.Facts(Res.string.details, factsOf(
                fact(Res.string.f_subject, v.voteSubject),
                fact(Res.string.f_date, v.voteDateTime),
                fact(Res.string.f_vote_method, v.voteMethodDesc),
                fact(Res.string.f_for_option, v.forOptionDesc),
                fact(Res.string.f_against_option, v.againstOptionDesc),
                if (v.isNoConfidenceInGov == true) fact(Res.string.filter_no_confidence, "✓") else null,
                fact(Res.string.f_sitting, v.plenumSession?.name, route = v.sessionID?.let { SittingRoute(it) }),
                fact(Res.string.f_item, v.voteTitle, route = itemRoute.await()),
            )),
            Section.Groups(Res.string.members_by_result, byResult, alternateLabel = Res.string.by_faction, alternate = byFaction, primaryLabel = Res.string.by_result),
        ),
    )
}

suspend fun loadSitting(l: Loader, id: Int, videos: VideoLinkSource): DetailContent = coroutineScope {
    val votesAsync = async {
        l.optional { all(ODataQuery("KNS_PlenumVote").apply { filter(F.eq("SessionID", id)); orderBy = "Ordinal,Id" }, PlenumVoteDto.serializer(), Ttl.DETAIL, max = 300) }.orEmpty()
    }
    // Expanded collections stop at 100 rows and long sittings have more items, so the agenda is paged on its own.
    val itemsAsync = async {
        l.all(ODataQuery("KNS_PlmSessionItem").apply { filter(F.eq("PlenumSessionID", id)); orderBy = "Ordinal,Id" }, PlmSessionItemDto.serializer(), Ttl.DETAIL, max = 1_000)
    }
    val s = l.one("KNS_PlenumSession", id, PlenumSessionDto.serializer(), listOf("KNS_DocumentPlenumSession"))
    // Knesset Channel videos of the sitting day, matched by date. Any failure, or a slow site, just shows nothing.
    val videoRefs = s.startDate?.let { start ->
        withTimeoutOrNull(12_000) { runCatching { videos.forPlenumDay(IsraelTime.local(start).date) }.getOrDefault(emptyList()) }
    }.orEmpty()
    DetailContent(
        kind = Kind.SITTING, id = id,
        title = s.name ?: "#$id",
        subtitle = listOfNotNull(s.knessetNum?.let { "כנסת $it" }, s.number?.let { "#$it" }).joinToString(" · "),
        lastUpdated = s.lastUpdatedDate,
        favoriteSubtitle = s.startDate?.let(IsraelTime::date),
        sections = listOf(
            Section.Facts(Res.string.details, factsOf(
                fact(Res.string.f_start, s.startDate),
                fact(Res.string.f_end, s.finishDate),
                if (s.isSpecialMeeting == true) fact(Res.string.filter_special, "✓") else null,
            )),
            Section.Links(Res.string.agenda, itemsAsync.await().sortedBy { it.ordinal ?: Long.MAX_VALUE }.map { i ->
                LinkItem(i.name ?: "#${i.itemID}", i.itemTypeDesc, i.itemID?.let { ItemRefs.kindOf(i.itemTypeID)?.route(it) })
            }),
            Section.Links(Res.string.votes, votesAsync.await().map { v -> LinkItem(v.voteTitle ?: "#${v.id}", listOfNotNull(v.voteDateTime?.let(IsraelTime::time), v.voteSubject).joinToString(" · "), VoteRoute(v.id)) }),
            Section.Links(Res.string.plenum_videos, videoRefs.map { v -> LinkItem(v.title, v.source, VideoPlayerRoute(v.url, v.title)) }),
            Section.Documents(Res.string.protocols, Documents.prepare(s.documentPlenumSession)),
        ),
    )
}

suspend fun loadMotion(l: Loader, id: Int, lookups: LookupRepo): DetailContent = coroutineScope {
    val joinedAsync = async {
        l.optional { list(ODataQuery("KNS_Agenda").apply { filter(F.eq("LeadingAgendaID", id)); top = 100; select = listOf("Id", "Name", "InitiatorPersonID") }, AgendaDto.serializer(), Ttl.DETAIL) }.orEmpty()
    }
    val a = l.one("KNS_Agenda", id, AgendaDto.serializer(), listOf("KNS_DocumentAgenda", "KNS_Committee", "KNS_RecommendCommittee", "KNS_Person", "KNS_PersonMinister", "KNS_Status"))
    val leading = a.leadingAgendaID?.takeIf { it != id && it > 0 }?.let { lid ->
        l.optional { one("KNS_Agenda", lid, AgendaDto.serializer()) }
    }
    DetailContent(
        kind = Kind.MOTION, id = id,
        title = a.name ?: "#$id",
        status = a.status?.desc,
        subtitle = listOfNotNull(a.subTypeDesc, a.classificationDesc).joinToString(" · "),
        lastUpdated = a.lastUpdatedDate,
        favoriteSubtitle = a.subTypeDesc,
        sections = listOf(
            Section.Facts(Res.string.details, factsOf(
                fact(Res.string.f_knesset, a.knessetNum),
                fact(Res.string.f_number, a.number),
                fact(Res.string.f_initiator, a.person?.let { People.fullName(it.firstName, it.lastName) }, route = a.initiatorPersonID?.let { MemberRoute(it) }),
                fact(Res.string.f_minister, a.personMinister?.let { People.fullName(it.firstName, it.lastName) }, route = a.ministerPersonID?.let { MemberRoute(it) }),
                fact(Res.string.f_gov_recommendation, a.govRecommendationDesc),
                fact(Res.string.f_president_decision, a.presidentDecisionDate),
                fact(Res.string.f_committee, a.committee?.name, route = a.committee?.id?.let { CommitteeRoute(it) }),
                fact(Res.string.f_recommended_committee, a.recommendCommittee?.name, route = a.recommendCommittee?.id?.let { CommitteeRoute(it) }),
                fact(Res.string.f_postponement, a.postopenmentReasonDesc),
                fact(Res.string.f_leading_motion, leading?.name, route = leading?.id?.let { MotionRoute(it) }),
            )),
            Section.Links(Res.string.joined_motions, joinedAsync.await().filter { it.id != id }.map { LinkItem(it.name ?: "#${it.id}", route = MotionRoute(it.id)) }),
            Section.Documents(Res.string.documents, Documents.prepare(a.documentAgenda)),
        ),
    )
}

suspend fun loadQuestion(l: Loader, id: Int): DetailContent {
    val q = l.one("KNS_Query", id, QueryDto.serializer(), listOf("KNS_DocumentQuery", "KNS_Person", "KNS_GovMinistry", "KNS_Status"))
    return DetailContent(
        kind = Kind.QUESTION, id = id,
        title = q.name ?: "#$id",
        status = q.status?.desc,
        subtitle = listOfNotNull(q.typeDesc, q.govMinistry?.name).joinToString(" · "),
        lastUpdated = q.lastUpdatedDate,
        favoriteSubtitle = q.typeDesc,
        sections = listOf(
            Section.Facts(Res.string.details, factsOf(
                fact(Res.string.f_knesset, q.knessetNum),
                fact(Res.string.f_number, q.number),
                fact(Res.string.f_type, q.typeDesc),
                fact(Res.string.f_asker, q.person?.let { People.fullName(it.firstName, it.lastName) }, route = q.personID?.let { MemberRoute(it) }),
                fact(Res.string.f_ministry, q.govMinistry?.name, route = q.govMinistryID?.let { MinistryRoute(it) }),
                fact(Res.string.f_submitted, q.submitDate),
                fact(Res.string.f_reply_planned, q.replyDatePlanned),
                fact(Res.string.f_replied, q.replyMinisterDate),
            )),
            Section.Documents(Res.string.documents, Documents.prepare(q.documentQuery)),
        ),
    )
}

@Composable
fun VoteScreen(id: Int) {
    val people = koinInject<PeopleRepo>()
    DetailScreen(rememberDetailVm("vote$id") { loadVote(this, id, people) }, stringResource(Res.string.section_votes), { FavoriteAction(it) })
}

@Composable
fun SittingScreen(id: Int) {
    val videos = koinInject<VideoLinkSource>()
    DetailScreen(rememberDetailVm("sitting$id") { loadSitting(this, id, videos) }, stringResource(Res.string.section_sittings), { FavoriteAction(it) })
}

@Composable
fun MotionScreen(id: Int) = DetailScreen(rememberDetailVm("motion$id") { loadMotion(this, id, it) }, stringResource(Res.string.section_motions), { FavoriteAction(it) })

@Composable
fun QuestionScreen(id: Int) = DetailScreen(rememberDetailVm("question$id") { loadQuestion(this, id) }, stringResource(Res.string.section_questions), { FavoriteAction(it) })
