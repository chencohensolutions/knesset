package com.chencohensolutions.knesset.ui.legislation

import androidx.compose.runtime.Composable
import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.core.odata.F
import com.chencohensolutions.knesset.core.odata.IsraelTime
import com.chencohensolutions.knesset.core.odata.ODataQuery
import com.chencohensolutions.knesset.data.dto.BillDto
import com.chencohensolutions.knesset.data.dto.IsraelLawDto
import com.chencohensolutions.knesset.data.dto.LawBindingDto
import com.chencohensolutions.knesset.data.dto.PlenumVoteDto
import com.chencohensolutions.knesset.data.dto.SecondaryLawDto
import com.chencohensolutions.knesset.data.repo.Loader
import com.chencohensolutions.knesset.data.repo.LookupRepo
import com.chencohensolutions.knesset.domain.Documents
import com.chencohensolutions.knesset.domain.ItemRefs
import com.chencohensolutions.knesset.domain.People
import com.chencohensolutions.knesset.resources.*
import com.chencohensolutions.knesset.ui.BillRoute
import com.chencohensolutions.knesset.ui.CommitteeRoute
import com.chencohensolutions.knesset.ui.Kind
import com.chencohensolutions.knesset.ui.LawRoute
import com.chencohensolutions.knesset.ui.MemberRoute
import com.chencohensolutions.knesset.ui.SecondaryLawRoute
import com.chencohensolutions.knesset.ui.VoteRoute
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
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// ---------- Lists ----------

/** Bill status ids belong to KNS_Status rows whose TypeID is 2 (plan 2.5). */
private const val BILL_STATUS_TYPE = 2

val billsSpec = ListSpec(
    kind = Kind.BILL,
    entitySet = "KNS_Bill",
    serializer = BillDto.serializer(),
    select = listOf("Id", "Name", "SubTypeDesc", "StatusID", "LastUpdatedDate", "KnessetNum", "PrivateNumber", "Number"),
    search = { F.contains("Name", it) },
    sorts = listOf(
        SortOption(Res.string.sort_last_updated, "LastUpdatedDate desc"),
        SortOption(Res.string.sort_publication, "PublicationDate desc"),
        SortOption(Res.string.sort_name, "Name"),
    ),
    filters = listOf(
        FilterDef.Choice("kind", Res.string.filter_bill_kind, multi = true,
            options = { listOf(Option("פרטית", "פרטית"), Option("ממשלתית", "ממשלתית"), Option("ועדה", "ועדה")) },
            fragment = { v -> F.or(*v.map { F.eqText("SubTypeDesc", it) }.toTypedArray()) }),
        FilterDef.Choice("status", Res.string.filter_status, multi = true,
            options = { ctx -> ctx.lookups.statuses().values.filter { it.typeID == BILL_STATUS_TYPE && !it.desc.isNullOrBlank() }.sortedBy { it.orderTransition ?: 0 }.map { Option(it.id.toString(), it.desc!!) } },
            fragment = { v -> F.anyOf("StatusID", v.mapNotNull { it.toIntOrNull() }) }),
        FilterDef.Choice("committee", Res.string.filter_committee, multi = false,
            options = { ctx -> ctx.lookups.committees(ctx.knesset).map { Option(it.id.toString(), it.name ?: "") } },
            fragment = { v -> F.anyOf("CommitteeID", v.mapNotNull { it.toIntOrNull() }) }),
        FilterDef.Toggle("continuation", Res.string.filter_continuation, F.eq("IsContinuationBill", true)),
        FilterDef.DateRange("updated", Res.string.filter_updated, "LastUpdatedDate"),
    ),
    needsStatuses = true,
    toRow = { b, ctx ->
        ListRow(
            id = b.id, title = b.name ?: "#${b.id}", status = ctx.status(b.statusID),
            date = b.lastUpdatedDate?.let(IsraelTime::date),
            subtitle = listOfNotNull(b.subTypeDesc, b.knessetNum?.let { "כנסת $it" }).joinToString(" · ").ifBlank { null },
            route = BillRoute(b.id),
        )
    },
)

val lawsSpec = ListSpec(
    kind = Kind.LAW,
    entitySet = "KNS_IsraelLaw",
    serializer = IsraelLawDto.serializer(),
    select = listOf("Id", "Name", "KnessetNum", "IsBasicLaw", "IsBudgetLaw", "PublicationDate", "LawValidityDesc"),
    search = { F.contains("Name", it) },
    sorts = listOf(
        SortOption(Res.string.sort_publication, "PublicationDate desc"),
        SortOption(Res.string.sort_last_updated, "LastUpdatedDate desc"),
        SortOption(Res.string.sort_name, "Name"),
    ),
    filters = listOf(
        FilterDef.Toggle("basic", Res.string.filter_basic_laws, F.eq("IsBasicLaw", true)),
        FilterDef.Toggle("budget", Res.string.filter_budget_laws, F.eq("IsBudgetLaw", true)),
        FilterDef.DateRange("published", Res.string.filter_published, "PublicationDate"),
    ),
    toRow = { l, _ ->
        ListRow(
            id = l.id, title = l.name ?: "#${l.id}", status = l.lawValidityDesc,
            date = l.publicationDate?.let(IsraelTime::date),
            subtitle = if (l.isBasicLaw == true) "חוק יסוד" else null,
            route = LawRoute(l.id),
        )
    },
)

val secondaryLawsSpec = ListSpec(
    kind = Kind.SECONDARY_LAW,
    entitySet = "KNS_SecondaryLaw",
    serializer = SecondaryLawDto.serializer(),
    select = listOf("Id", "Name", "KnessetNum", "TypeDesc", "StatusName", "ClassificationDesc", "PublicationDate", "LastUpdatedDate", "IsEmergency"),
    search = { F.contains("Name", it) },
    sorts = listOf(
        SortOption(Res.string.sort_last_updated, "LastUpdatedDate desc"),
        SortOption(Res.string.sort_publication, "PublicationDate desc"),
        SortOption(Res.string.sort_name, "Name"),
    ),
    filters = listOf(
        FilterDef.Toggle("emergency", Res.string.filter_emergency, F.eq("IsEmergency", true)),
        FilterDef.Choice("committee", Res.string.filter_committee, multi = false,
            options = { ctx -> ctx.lookups.committees(ctx.knesset).map { Option(it.id.toString(), it.name ?: "") } },
            fragment = { v -> F.anyOf("CommitteeID", v.mapNotNull { it.toIntOrNull() }) }),
        FilterDef.DateRange("published", Res.string.filter_published, "PublicationDate"),
    ),
    toRow = { s, _ ->
        ListRow(
            id = s.id, title = s.name ?: "#${s.id}", status = s.statusName,
            date = (s.publicationDate ?: s.lastUpdatedDate)?.let(IsraelTime::date),
            subtitle = listOfNotNull(s.classificationDesc ?: s.typeDesc).firstOrNull(),
            route = SecondaryLawRoute(s.id),
        )
    },
)

@Composable
fun LegislationScreen() {
    SectionTabsScreen(
        Res.string.tab_legislation,
        listOf(
            Res.string.section_bills to { ListContent(rememberListVm(billsSpec, "bills")) },
            Res.string.section_laws to { ListContent(rememberListVm(lawsSpec, "laws")) },
            Res.string.section_secondary to { ListContent(rememberListVm(secondaryLawsSpec, "secondary")) },
        ),
    )
}

// ---------- Details ----------

private fun personItem(id: Int?, first: String?, last: String?, subtitle: String? = null) = LinkItem(
    title = People.fullName(first, last).ifBlank { "#$id" },
    subtitle = subtitle,
    initials = People.initials(first, last),
    route = id?.let { MemberRoute(it) },
)

private fun voteItem(v: PlenumVoteDto) = LinkItem(
    title = v.voteTitle ?: "#${v.id}",
    subtitle = listOfNotNull(v.voteDateTime?.let(IsraelTime::dateTime), v.voteSubject).joinToString(" · "),
    route = VoteRoute(v.id),
)

suspend fun loadBill(l: Loader, id: Int): DetailContent = coroutineScope {
    val votesAsync = async {
        l.optional {
            list(ODataQuery("KNS_PlenumVote").apply { filter(F.eq("ItemID", id)); orderBy = "VoteDateTime desc,Id desc"; top = 100 }, PlenumVoteDto.serializer(), Ttl.DETAIL)
        }.orEmpty()
    }
    val b = l.one(
        "KNS_Bill", id, BillDto.serializer(),
        listOf(
            "KNS_BillInitiator(\$expand=KNS_Person)", "KNS_BillHistoryInitiator(\$expand=KNS_Person)", "KNS_Status",
            "KNS_Committee", "KNS_DocumentBill", "KNS_BillName", "KNS_BillSplit", "KNS_BillUnion", "KNS_BillUnion_Main", "KNS_LawCorrections",
        ),
    )
    val amends = l.optional {
        val bindings = list(ODataQuery("KNS_LawBinding").apply { filter(F.eq("LawID", id)); top = 100 }, LawBindingDto.serializer(), Ttl.DETAIL)
        val names = byIds("KNS_IsraelLaw", bindings.mapNotNull { it.israelLawID }, IsraelLawDto.serializer(), listOf("Id", "Name")).associateBy { it.id }
        bindings.mapNotNull { it.israelLawID }.distinct().map { lawId ->
            LinkItem(names[lawId]?.name ?: "#$lawId", bindings.firstOrNull { it.israelLawID == lawId }?.amendmentTypeDesc, LawRoute(lawId))
        }
    }.orEmpty()
    val votes = votesAsync.await()
    val related = buildList {
        b.billSplit.forEach { s ->
            val other = if (s.mainBillID == id) s.splitBillID else s.mainBillID
            if (other != null) add(LinkItem(s.name ?: "#$other", "פיצול", BillRoute(other)))
        }
        (b.billUnion + b.billUnionMain).forEach { u ->
            val other = if (u.mainBillID == id) u.unionBillID else u.mainBillID
            if (other != null && other != id) add(LinkItem("#$other", "איחוד", BillRoute(other)))
        }
    }.distinctBy { it.route }
    val relatedNames = l.optional {
        byIds("KNS_Bill", related.mapNotNull { (it.route as? BillRoute)?.id }, BillDto.serializer(), listOf("Id", "Name")).associateBy { it.id }
    }.orEmpty()
    val initiators = b.billInitiator.sortedBy { it.ordinal ?: Int.MAX_VALUE }
    DetailContent(
        kind = Kind.BILL, id = id,
        title = b.name ?: "#$id",
        status = b.status?.desc,
        subtitle = listOfNotNull(b.subTypeDesc, b.knessetNum?.let { "כנסת $it" }).joinToString(" · "),
        lastUpdated = b.lastUpdatedDate,
        favoriteSubtitle = b.subTypeDesc,
        sections = listOf(
            Section.Facts(Res.string.details, factsOf(
                fact(Res.string.f_type, b.typeDesc),
                fact(Res.string.f_kind, b.subTypeDesc),
                fact(Res.string.f_knesset, b.knessetNum),
                fact(Res.string.f_private_number, b.privateNumber),
                fact(Res.string.f_bill_number, b.number),
                fact(Res.string.f_committee, b.committee?.name, route = b.committee?.id?.let { CommitteeRoute(it) }),
                fact(Res.string.f_publication_series, b.publicationSeriesDesc),
                fact(Res.string.f_first_call_series, b.publicationSeriesFirstCallDesc),
                fact(Res.string.f_magazine, b.magazineNumber),
                fact(Res.string.f_page, b.pageNumber),
                fact(Res.string.f_publication_date, b.publicationDate),
                fact(Res.string.f_postponement, b.postponementReasonDesc),
                if (b.isContinuationBill == true) fact(Res.string.f_continuation, "✓") else null,
                fact(Res.string.f_last_updated, b.lastUpdatedDate),
            )),
            Section.Text(Res.string.summary, b.summaryLaw),
            Section.Links(Res.string.initiators, initiators.filter { it.isInitiator == true }.map { personItem(it.personID, it.person?.firstName, it.person?.lastName) }),
            Section.Links(Res.string.joined_signers, initiators.filter { it.isInitiator != true }.map { personItem(it.personID, it.person?.firstName, it.person?.lastName) }),
            Section.Links(Res.string.former_initiators, b.billHistoryInitiator.map {
                personItem(it.personID, it.person?.firstName, it.person?.lastName, listOfNotNull(it.reasonDesc, it.endDate?.let(IsraelTime::date)).joinToString(" · "))
            }),
            Section.Links(Res.string.name_history, b.billName.map { LinkItem(it.name ?: "", it.nameHistoryTypeDesc) }),
            Section.Documents(Res.string.documents, Documents.prepare(b.documentBill)),
            Section.Links(Res.string.plenum_votes, votes.map(::voteItem)),
            Section.Links(Res.string.amends_laws, amends),
            Section.Links(Res.string.related_bills, related.map { r -> r.copy(title = relatedNames[(r.route as BillRoute).id]?.name ?: r.title) }),
            Section.Links(Res.string.corrections, b.lawCorrections.map {
                LinkItem(it.correctionTypeDesc ?: "#${it.id}", listOfNotNull(it.correctionStatusDesc, it.publicationDate?.let(IsraelTime::date)).joinToString(" · "))
            }),
        ),
    )
}

suspend fun loadLaw(l: Loader, id: Int): DetailContent {
    val law = l.one(
        "KNS_IsraelLaw", id, IsraelLawDto.serializer(),
        listOf("KNS_DocumentIsraelLaw", "KNS_IsraelLawBinding", "KNS_IsraelLawClassificiation", "KNS_IsraelLawMinistry", "KNS_IsraelLawName"),
    )
    val amending = l.optional {
        val bindings = all(ODataQuery("KNS_LawBinding").apply { filter(F.eq("IsraelLawID", id)); orderBy = "Id desc" }, LawBindingDto.serializer(), Ttl.DETAIL, max = 500)
        val billIds = bindings.filter { ItemRefs.kindOf(it.lawTypeID) == Kind.BILL || it.lawTypeID == null }.mapNotNull { it.lawID }.distinct()
        val bills = byIds("KNS_Bill", billIds.take(100), BillDto.serializer(), listOf("Id", "Name", "KnessetNum")).associateBy { it.id }
        billIds.map { bid ->
            val b = bills[bid]
            val binding = bindings.first { it.lawID == bid }
            LinkItem(b?.name ?: "#$bid", listOfNotNull(binding.bindingTypeDesc, binding.amendmentTypeDesc).joinToString(" · "), BillRoute(bid))
        }
    }.orEmpty()
    val replaced = law.israelLawBinding.mapNotNull { it.israelLawReplacedID }.filter { it != id }
    val replacedNames = l.optional { byIds("KNS_IsraelLaw", replaced, IsraelLawDto.serializer(), listOf("Id", "Name")).associateBy { it.id } }.orEmpty()
    return DetailContent(
        kind = Kind.LAW, id = id,
        title = law.name ?: "#$id",
        status = law.lawValidityDesc,
        subtitle = listOfNotNull(if (law.isBasicLaw == true) "חוק יסוד" else null, if (law.isBudgetLaw == true) "חוק תקציב" else null, law.knessetNum?.let { "כנסת $it" }).joinToString(" · "),
        lastUpdated = law.lastUpdatedDate,
        favoriteSubtitle = law.lawValidityDesc,
        sections = listOf(
            Section.Facts(Res.string.details, factsOf(
                fact(Res.string.f_knesset, law.knessetNum),
                fact(Res.string.f_publication_date, law.publicationDate),
                fact(Res.string.f_latest_publication, law.latestPublicationDate),
                fact(Res.string.f_validity, law.lawValidityDesc),
                fact(Res.string.f_validity_start, law.validityStartDate),
                fact(Res.string.f_validity_start_notes, law.validityStartDateNotes),
                fact(Res.string.f_validity_end, law.validityFinishDate),
                fact(Res.string.f_validity_end_notes, law.validityFinishDateNotes),
                fact(Res.string.f_last_updated, law.lastUpdatedDate),
            )),
            Section.Links(Res.string.classifications, law.israelLawClassificiation.mapNotNull { it.classificiationDesc }.distinct().map { LinkItem(it) }),
            Section.Links(Res.string.ministries, law.israelLawMinistry.mapNotNull { it.ministryCategoryDesc }.distinct().map { LinkItem(it) }),
            Section.Links(Res.string.name_history, law.israelLawName.mapNotNull { it.name }.distinct().map { LinkItem(it) }),
            Section.Documents(Res.string.documents, Documents.prepare(law.documentIsraelLaw)),
            Section.Links(Res.string.amending_bills, amending),
            Section.Links(Res.string.replaced_laws, replaced.distinct().map { LinkItem(replacedNames[it]?.name ?: "#$it", route = LawRoute(it)) }),
        ),
    )
}

suspend fun loadSecondaryLaw(l: Loader, id: Int): DetailContent {
    val s = l.one(
        "KNS_SecondaryLaw", id, SecondaryLawDto.serializer(),
        listOf("KNS_DocumentSecondaryLaw", "KNS_SecLawRegulator", "KNS_SecLawAuthorizingLaw", "KNS_SecToSecBinding", "KNS_SecToSecBindingParent", "KNS_Committee"),
    )
    val authorizing = (s.secLawAuthorizingLaw.mapNotNull { it.authorizingLawID } + listOfNotNull(s.majorAuthorizingLawID)).distinct()
    val lawNames = l.optional { byIds("KNS_IsraelLaw", authorizing, IsraelLawDto.serializer(), listOf("Id", "Name")).associateBy { it.id } }.orEmpty()
    val relatedIds = (s.secToSecBinding.map { it.secParentID } + s.secToSecBindingParent.map { it.secChildID }).filterNotNull().filter { it != id }.distinct()
    val relatedNames = l.optional { byIds("KNS_SecondaryLaw", relatedIds, SecondaryLawDto.serializer(), listOf("Id", "Name")).associateBy { it.id } }.orEmpty()
    return DetailContent(
        kind = Kind.SECONDARY_LAW, id = id,
        title = s.name ?: "#$id",
        status = s.statusName,
        subtitle = listOfNotNull(s.typeDesc, s.classificationDesc, if (s.isEmergency == true) "חירום" else null).joinToString(" · "),
        lastUpdated = s.lastUpdatedDate,
        favoriteSubtitle = s.statusName,
        sections = listOf(
            Section.Facts(Res.string.details, factsOf(
                fact(Res.string.f_knesset, s.knessetNum),
                fact(Res.string.f_type, s.typeDesc),
                fact(Res.string.f_classification, s.classificationDesc),
                fact(Res.string.f_knesset_involvement, s.knessetInvolvementDesc),
                fact(Res.string.f_committee, s.committee?.name, route = s.committee?.id?.let { CommitteeRoute(it) }),
                fact(Res.string.f_completion_cause, s.completionCauseDesc),
                fact(Res.string.f_postponement, s.postponementReasonDesc),
                fact(Res.string.f_publication_series, s.publicationSeriesDesc),
                fact(Res.string.f_magazine, s.magazineNumber),
                fact(Res.string.f_page, s.pageNumber),
            )),
            Section.Facts(Res.string.timeline, factsOf(
                fact(Res.string.f_secretary_received, s.secretaryReceivedDate),
                fact(Res.string.f_committee_received, s.committeeReceivedDate),
                fact(Res.string.f_committee_approval, s.committeeApprovalDate),
                fact(Res.string.f_approval_without_discussion, s.approvalDateWithoutDiscussion),
                fact(Res.string.f_plenum_approval, s.plenumApprovalDate),
                fact(Res.string.f_publication_date, s.publicationDate),
            )),
            Section.Links(Res.string.regulators, s.secLawRegulator.map { LinkItem(it.regulatorDesc ?: "#${it.regulatorID}", it.regulatorTypeDesc) }),
            Section.Links(Res.string.authorizing_laws, authorizing.map { LinkItem(lawNames[it]?.name ?: "#$it", route = LawRoute(it)) }),
            Section.Links(Res.string.related_regulations, relatedIds.map { LinkItem(relatedNames[it]?.name ?: "#$it", route = SecondaryLawRoute(it)) }),
            Section.Documents(Res.string.documents, Documents.prepare(s.documentSecondaryLaw)),
        ),
    )
}

@Composable
fun BillScreen(id: Int) = DetailScreen(rememberDetailVm("bill$id") { loadBill(this, id) }, stringResource(Res.string.section_bills), { FavoriteAction(it) })

@Composable
fun LawScreen(id: Int) = DetailScreen(rememberDetailVm("law$id") { loadLaw(this, id) }, stringResource(Res.string.section_laws), { FavoriteAction(it) })

@Composable
fun SecondaryLawScreen(id: Int) = DetailScreen(rememberDetailVm("secondary$id") { loadSecondaryLaw(this, id) }, stringResource(Res.string.section_secondary), { FavoriteAction(it) })

