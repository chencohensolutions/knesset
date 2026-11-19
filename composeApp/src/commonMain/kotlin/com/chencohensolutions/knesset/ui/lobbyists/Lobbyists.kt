package com.chencohensolutions.knesset.ui.lobbyists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.core.odata.F
import com.chencohensolutions.knesset.core.odata.KnessetError
import com.chencohensolutions.knesset.core.odata.ODataQuery
import com.chencohensolutions.knesset.data.dto.LobbyistDto
import com.chencohensolutions.knesset.data.dto.LobbyistsClientDto
import com.chencohensolutions.knesset.data.repo.Api
import com.chencohensolutions.knesset.data.repo.Loader
import com.chencohensolutions.knesset.resources.*
import com.chencohensolutions.knesset.ui.Kind
import com.chencohensolutions.knesset.ui.LobbyistRoute
import com.chencohensolutions.knesset.ui.components.rememberDetailVm
import com.chencohensolutions.knesset.ui.components.rememberListVm
import com.chencohensolutions.knesset.ui.detail.DetailContent
import com.chencohensolutions.knesset.ui.detail.DetailScreen
import com.chencohensolutions.knesset.ui.detail.LinkItem
import com.chencohensolutions.knesset.ui.detail.Section
import com.chencohensolutions.knesset.ui.detail.fact
import com.chencohensolutions.knesset.ui.detail.factsOf
import com.chencohensolutions.knesset.ui.list.ListContent
import com.chencohensolutions.knesset.ui.list.ListRow
import com.chencohensolutions.knesset.ui.list.ListSpec
import com.chencohensolutions.knesset.ui.list.SortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.compose.koinInject

/** Every field except IdentityNumber, which is never requested (plan 2.7, 7.8). */
val LOBBYIST_FIELDS = listOf("Id", "FullName", "PermitTypeValue", "Key", "CorporationName", "IsIndependent", "CorpNumber", "PracticeFramework", "IsMemberInFaction", "MemberInFaction")

/** Probes the lobbyist endpoint once per run. It answered 404 on 2026-11-19, so the section stays hidden then. */
class LobbyistsAvailability(private val api: Api) {
    private val _available = MutableStateFlow<Boolean?>(null)
    val available: StateFlow<Boolean?> = _available.asStateFlow()

    suspend fun check() {
        if (_available.value != null) return
        _available.value = try {
            api.list(ODataQuery("V_Lobbyists").apply { top = 1; select = listOf("Id", "FullName") }, LobbyistDto.serializer(), Ttl.LOOKUP)
            true
        } catch (e: KnessetError) {
            false
        }
    }
}

@Composable
fun rememberLobbyistsAvailable(): Boolean {
    val availability = koinInject<LobbyistsAvailability>()
    val available by availability.available.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { availability.check() }
    return available == true
}

val lobbyistsSpec = ListSpec(
    kind = Kind.LOBBYIST,
    entitySet = "V_Lobbyists",
    serializer = LobbyistDto.serializer(),
    select = LOBBYIST_FIELDS,
    search = { F.or(F.contains("FullName", it), F.contains("CorporationName", it)) },
    knessetField = null,
    sorts = listOf(SortOption(Res.string.sort_name, "FullName")),
    ttl = Ttl.LOOKUP,
    toRow = { l, _ -> ListRow(l.id, l.fullName ?: "#${l.id}", subtitle = listOfNotNull(l.corporationName, l.permitTypeValue).joinToString(" · ").ifBlank { null }, route = LobbyistRoute(l.id)) },
)

@Composable
fun LobbyistsContent() = ListContent(rememberListVm(lobbyistsSpec, "lobbyists"))

suspend fun loadLobbyist(l: Loader, id: Int): DetailContent {
    val lob = l.list(ODataQuery("V_Lobbyists").apply { filter(F.eq("Id", id)); select = LOBBYIST_FIELDS }, LobbyistDto.serializer(), Ttl.DETAIL).firstOrNull() ?: throw KnessetError.NotFound()
    val clients = l.optional { all(ODataQuery("V_LobbyistsClients").apply { filter(F.eq("LobbyistID", id)); orderBy = "Id" }, LobbyistsClientDto.serializer(), Ttl.DETAIL, max = 500) }.orEmpty()
    return DetailContent(
        kind = Kind.LOBBYIST, id = id, title = lob.fullName ?: "#$id", subtitle = lob.corporationName,
        sections = listOf(
            Section.Facts(Res.string.details, factsOf(
                fact(Res.string.f_permit_type, lob.permitTypeValue),
                fact(Res.string.f_corporation, lob.corporationName),
                fact(Res.string.f_practice_framework, lob.practiceFramework),
                fact(Res.string.f_independent, if (lob.isIndependent == true) "✓" else null),
                fact(Res.string.f_faction_member, lob.isMemberInFaction),
            )),
            Section.Links(Res.string.clients, clients.map { LinkItem(it.name ?: it.clientsNames ?: "#${it.clientID}") }),
        ),
    )
}

@Composable
fun LobbyistScreen(id: Int) = DetailScreen(rememberDetailVm("lobbyist$id") { loadLobbyist(this, id) }, "")
