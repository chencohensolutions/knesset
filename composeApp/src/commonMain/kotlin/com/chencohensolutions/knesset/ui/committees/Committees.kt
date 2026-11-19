package com.chencohensolutions.knesset.ui.committees

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.core.odata.F
import com.chencohensolutions.knesset.core.odata.IsraelTime
import com.chencohensolutions.knesset.core.odata.ODataQuery
import com.chencohensolutions.knesset.data.dto.AgendaDto
import com.chencohensolutions.knesset.data.dto.BillDto
import com.chencohensolutions.knesset.data.dto.CommitteeDto
import com.chencohensolutions.knesset.data.dto.CommitteeSessionDto
import com.chencohensolutions.knesset.data.dto.JointCommitteeDto
import com.chencohensolutions.knesset.data.dto.PersonToPositionDto
import com.chencohensolutions.knesset.data.dto.SecondaryLawDto
import com.chencohensolutions.knesset.data.repo.Loader
import com.chencohensolutions.knesset.domain.Documents
import com.chencohensolutions.knesset.domain.ItemRefs
import com.chencohensolutions.knesset.domain.People
import com.chencohensolutions.knesset.resources.*
import com.chencohensolutions.knesset.ui.BillRoute
import com.chencohensolutions.knesset.ui.CommitteeRoute
import com.chencohensolutions.knesset.ui.CommitteeSessionRoute
import com.chencohensolutions.knesset.ui.Kind
import com.chencohensolutions.knesset.ui.MemberRoute
import com.chencohensolutions.knesset.ui.MotionRoute
import com.chencohensolutions.knesset.ui.SecondaryLawRoute
import com.chencohensolutions.knesset.ui.components.SectionTabsScreen
import com.chencohensolutions.knesset.ui.components.rememberDetailVm
import com.chencohensolutions.knesset.ui.components.rememberListVm
import com.chencohensolutions.knesset.ui.detail.ActionLink
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
import com.chencohensolutions.knesset.ui.people.dateSpan
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

val committeesSpec = ListSpec(
    kind = Kind.COMMITTEE,
    entitySet = "KNS_Committee",
    serializer = CommitteeDto.serializer(),
    select = listOf("Id", "Name", "CategoryDesc", "CommitteeTypeDesc", "AdditionalTypeDesc", "CommitteeParentName", "ParentCommitteeID", "IsCurrent", "FinishDate", "KnessetNum"),
    search = { F.contains("Name", it) },
    sorts = listOf(SortOption(Res.string.sort_name, "Name"), SortOption(Res.string.sort_last_updated, "LastUpdatedDate desc")),
    filters = listOf(
        FilterDef.Toggle("active", Res.string.filter_active, F.isNull("FinishDate")),
        FilterDef.Toggle("main", Res.string.filter_main_committees, F.isNull("ParentCommitteeID")),
        FilterDef.Choice("type", Res.string.filter_committee_type, multi = false,
            options = { listOf(Option("קבועה", "קבועה"), Option("מיוחדת", "מיוחדת"), Option("משנה", "משנה"), Option("משותפת", "משותפת")) },
            fragment = { v -> F.or(*v.map { F.contains("AdditionalTypeDesc", it) }.toTypedArray()) }),
    ),
    ttl = Ttl.LOOKUP,
    toRow = { c, _ ->
        ListRow(
            c.id, c.name?.trim() ?: "#${c.id}",
            subtitle = listOfNotNull(c.additionalTypeDesc ?: c.committeeTypeDesc, c.committeeParentName?.let { "↳ $it" }).joinToString(" · ").ifBlank { null },
            status = if (c.finishDate != null) c.finishDate.let { "✕ " + IsraelTime.date(it) } else null,
            route = CommitteeRoute(c.id),
        )
    },
)

private fun sessionRow(s: CommitteeSessionDto) = ListRow(
    id = s.id,
    title = s.committee?.name?.trim() ?: "#${s.committeeID}",
    subtitle = listOfNotNull(s.cmtSessionItem.minByOrNull { it.ordinal ?: Int.MAX_VALUE }?.name, s.location).joinToString(" · ").ifBlank { null },
    date = s.startDate?.let(IsraelTime::time),
    status = s.statusDesc?.takeIf { it.contains("בוטל") },
    group = s.startDate?.let(IsraelTime::date),
    route = CommitteeSessionRoute(s.id),
)

/** The session calendar: from the start of [today] on, oldest first, one header per day (plan 2.7). */
fun calendarSpec(today: LocalDate) = ListSpec(
    kind = Kind.COMMITTEE_SESSION,
    entitySet = "KNS_CommitteeSession",
    serializer = CommitteeSessionDto.serializer(),
    select = listOf("Id", "CommitteeID", "StartDate", "Location", "StatusDesc", "TypeDesc"),
    expand = listOf("KNS_Committee(\$select=Id,Name)", "KNS_CmtSessionItem(\$select=Id,Name,Ordinal)"),
    search = null,
    baseFilters = listOf(F.ge("StartDate", IsraelTime.startOfDay(today))),
    sorts = listOf(SortOption(Res.string.sort_date_asc, "StartDate asc,Id asc")),
    filters = listOf(
        FilterDef.Choice("committee", Res.string.filter_committee, multi = true,
            options = { ctx -> ctx.lookups.committees(ctx.knesset).map { Option(it.id.toString(), it.name ?: "") } },
            fragment = { v -> F.anyOf("CommitteeID", v.mapNotNull { it.toIntOrNull() }) }),
        FilterDef.DateRange("date", Res.string.filter_date, "StartDate"),
    ),
    ttl = Ttl.LIVE,
    toRow = { s, _ -> sessionRow(s) },
)

fun todayInIsrael(now: Instant = Clock.System.now()): LocalDate = now.toLocalDateTime(IsraelTime.zone).date

@Composable
fun CommitteesScreen() {
    val today = remember { todayInIsrael() }
    SectionTabsScreen(
        Res.string.tab_committees,
        listOf(
            Res.string.section_committees to { ListContent(rememberListVm(committeesSpec, "committees", initialChoices = emptyMap())) },
            Res.string.section_calendar to { ListContent(rememberListVm(calendarSpec(today), "calendar$today")) },
        ),
    )
}

/** Chair first, then other duties, then plain members (plan 2.7). */
private fun dutyRank(duty: String?): Int = when {
    duty == null -> 3
    duty.contains("יו\"ר") || duty.contains("יושב") || duty.contains("יו״ר") -> if (duty.contains("מ\"מ") || duty.contains("ממלא")) 1 else 0
    else -> 2
}

suspend fun loadCommittee(l: Loader, id: Int, today: LocalDate): DetailContent = coroutineScope {
    val c = l.one("KNS_Committee", id, CommitteeDto.serializer())
    // Expanding the joint committee navigations is refused by the server (HTTP 473), so query the join table.
    val jointAsync = async {
        l.optional {
            val rows = list(ODataQuery("KNS_JointCommittee").apply { filter(F.or(F.eq("CommitteeID", id), F.eq("ParticipantCommitteeID", id))); top = 100 }, JointCommitteeDto.serializer(), Ttl.DETAIL)
            val ids = rows.flatMap { listOfNotNull(it.committeeID, it.participantCommitteeID) }.filter { it != id }.distinct()
            byIds("KNS_Committee", ids, CommitteeDto.serializer(), listOf("Id", "Name"))
        }.orEmpty()
    }
    val membersAsync = async {
        l.optional { all(ODataQuery("KNS_PersonToPosition").apply { filter(F.eq("CommitteeID", id)); orderBy = "StartDate desc,Id desc"; expand = listOf("KNS_Person") }, PersonToPositionDto.serializer(), Ttl.DETAIL, max = 400) }.orEmpty()
    }
    val startToday = IsraelTime.startOfDay(today)
    val upcomingAsync = async {
        l.optional { list(ODataQuery("KNS_CommitteeSession").apply { filter(F.eq("CommitteeID", id)); filter(F.ge("StartDate", startToday)); orderBy = "StartDate asc,Id asc"; top = 50; expand = listOf("KNS_CmtSessionItem(\$select=Id,Name,Ordinal)") }, CommitteeSessionDto.serializer(), Ttl.LIVE) }.orEmpty()
    }
    val pastAsync = async {
        l.optional { list(ODataQuery("KNS_CommitteeSession").apply { filter(F.eq("CommitteeID", id)); filter("StartDate lt ${F.dateLiteral(startToday)}"); orderBy = "StartDate desc,Id desc"; top = 50; expand = listOf("KNS_CmtSessionItem(\$select=Id,Name,Ordinal)") }, CommitteeSessionDto.serializer(), Ttl.DETAIL) }.orEmpty()
    }
    val billsAsync = async {
        l.optional { list(ODataQuery("KNS_Bill").apply { filter(F.eq("CommitteeID", id)); orderBy = "LastUpdatedDate desc,Id desc"; top = 50; select = listOf("Id", "Name", "LastUpdatedDate") }, BillDto.serializer(), Ttl.DETAIL) }.orEmpty()
    }
    val motionsAsync = async {
        l.optional { list(ODataQuery("KNS_Agenda").apply { filter(F.eq("CommitteeID", id)); orderBy = "Id desc"; top = 50; select = listOf("Id", "Name", "SubTypeDesc") }, AgendaDto.serializer(), Ttl.DETAIL) }.orEmpty()
    }
    val secondaryAsync = async {
        l.optional { list(ODataQuery("KNS_SecondaryLaw").apply { filter(F.eq("CommitteeID", id)); orderBy = "LastUpdatedDate desc,Id desc"; top = 50; select = listOf("Id", "Name", "StatusName") }, SecondaryLawDto.serializer(), Ttl.DETAIL) }.orEmpty()
    }
    val members = membersAsync.await().filter { it.personID != null }
        .sortedWith(compareBy<PersonToPositionDto>({ if (it.finishDate == null) 0 else 1 }, { dutyRank(it.dutyDesc) }, { it.person?.lastName ?: "" }))
        .distinctBy { it.personID to it.dutyDesc to (it.finishDate == null) }
    fun memberItem(p: PersonToPositionDto) = LinkItem(
        People.fullName(p.person?.firstName, p.person?.lastName).ifBlank { "#${p.personID}" },
        listOfNotNull(p.dutyDesc, dateSpan(p)).joinToString(" · "),
        MemberRoute(p.personID!!), initials = People.initials(p.person?.firstName, p.person?.lastName),
    )
    fun sessionItem(s: CommitteeSessionDto) = LinkItem(
        s.cmtSessionItem.minByOrNull { it.ordinal ?: Int.MAX_VALUE }?.name ?: (s.typeDesc ?: "#${s.id}"),
        s.startDate?.let(IsraelTime::dateTime), CommitteeSessionRoute(s.id), status = s.statusDesc?.takeIf { it.contains("בוטל") },
    )
    val joint = jointAsync.await()
    val email = People.validEmail(c.email)
    DetailContent(
        kind = Kind.COMMITTEE, id = id,
        title = c.name?.trim() ?: "#$id",
        subtitle = listOfNotNull(c.additionalTypeDesc ?: c.committeeTypeDesc, c.knessetNum?.let { "כנסת $it" }).joinToString(" · "),
        lastUpdated = c.lastUpdatedDate,
        favoriteSubtitle = c.additionalTypeDesc,
        sections = listOf(
            Section.Facts(Res.string.details, factsOf(
                fact(Res.string.f_category, c.categoryDesc),
                fact(Res.string.f_type, c.committeeTypeDesc),
                fact(Res.string.f_committee_kind, c.additionalTypeDesc),
                fact(Res.string.f_knesset, c.knessetNum),
                fact(Res.string.f_parent_committee, c.committeeParentName, route = c.parentCommitteeID?.let { CommitteeRoute(it) }),
                fact(Res.string.f_start, c.startDate),
                fact(Res.string.f_end, c.finishDate),
                fact(Res.string.f_email, email, url = email?.let { "mailto:$it" }),
            )),
            Section.Links(Res.string.members, members.filter { it.finishDate == null }.map(::memberItem)),
            Section.Links(Res.string.former_members, members.filter { it.finishDate != null }.map(::memberItem)),
            Section.Links(Res.string.joint_committees, joint.map { LinkItem(it.name ?: "#${it.id}", route = CommitteeRoute(it.id)) }),
            Section.Links(Res.string.upcoming_sessions, upcomingAsync.await().map(::sessionItem)),
            Section.Links(Res.string.past_sessions, pastAsync.await().map(::sessionItem)),
            Section.Links(Res.string.section_bills, billsAsync.await().map { LinkItem(it.name ?: "#${it.id}", it.lastUpdatedDate?.let(IsraelTime::date), BillRoute(it.id)) }),
            Section.Links(Res.string.section_motions, motionsAsync.await().map { LinkItem(it.name ?: "#${it.id}", it.subTypeDesc, MotionRoute(it.id)) }),
            Section.Links(Res.string.section_secondary, secondaryAsync.await().map { LinkItem(it.name ?: "#${it.id}", it.statusName, SecondaryLawRoute(it.id)) }),
        ),
    )
}

/** Session pages are on http://main.knesset.gov.il; upgrade to https. */
fun httpsUrl(raw: String?): String? = Documents.normalizeUrl(raw)

suspend fun loadCommitteeSession(l: Loader, id: Int): DetailContent {
    val s = l.one("KNS_CommitteeSession", id, CommitteeSessionDto.serializer(), listOf("KNS_Committee", "KNS_CmtSessionItem", "KNS_DocumentCommitteeSession"))
    return DetailContent(
        kind = Kind.COMMITTEE_SESSION, id = id,
        title = s.committee?.name?.trim() ?: "#$id",
        subtitle = s.startDate?.let(IsraelTime::dateTime),
        status = s.statusDesc,
        webUrl = httpsUrl(s.sessionUrl),
        lastUpdated = s.lastUpdatedDate,
        favoriteSubtitle = s.startDate?.let(IsraelTime::dateTime),
        sections = listOf(
            Section.Facts(Res.string.details, factsOf(
                fact(Res.string.f_committee, s.committee?.name, route = s.committeeID?.let { CommitteeRoute(it) }),
                fact(Res.string.f_start, s.startDate),
                fact(Res.string.f_end, s.finishDate),
                fact(Res.string.f_location, s.location),
                fact(Res.string.f_type, s.typeDesc),
                fact(Res.string.f_status, s.statusDesc),
                fact(Res.string.f_number, s.number),
                fact(Res.string.f_note, s.note),
            )),
            Section.Actions(null, listOfNotNull(
                httpsUrl(s.sessionUrl)?.let { ActionLink(Res.string.session_page, it) },
                httpsUrl(s.broadcastUrl)?.let { ActionLink(Res.string.broadcast, it) },
            )),
            Section.Links(Res.string.agenda, s.cmtSessionItem.sortedBy { it.ordinal ?: Int.MAX_VALUE }.map { i ->
                LinkItem(i.name ?: "#${i.itemID}", null, i.itemID?.let { ItemRefs.kindOf(i.itemTypeID)?.route(it) })
            }),
            Section.Documents(Res.string.documents, Documents.prepare(s.documentCommitteeSession)),
        ),
    )
}

@Composable
fun CommitteeScreen(id: Int) {
    val today = remember { todayInIsrael() }
    DetailScreen(rememberDetailVm("committee$id") { loadCommittee(this, id, today) }, stringResource(Res.string.section_committees), { FavoriteAction(it) })
}

@Composable
fun CommitteeSessionScreen(id: Int) =
    DetailScreen(rememberDetailVm("csession$id") { loadCommitteeSession(this, id) }, stringResource(Res.string.section_calendar), { FavoriteAction(it) })

