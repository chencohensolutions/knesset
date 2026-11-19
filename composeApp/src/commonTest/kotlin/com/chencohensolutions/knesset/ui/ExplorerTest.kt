package com.chencohensolutions.knesset.ui

import com.chencohensolutions.knesset.FakeClock
import com.chencohensolutions.knesset.MemoryCacheStore
import com.chencohensolutions.knesset.core.odata.ODataClient
import com.chencohensolutions.knesset.core.odata.ServiceDocument
import com.chencohensolutions.knesset.data.repo.FieldDef
import com.chencohensolutions.knesset.data.repo.MetadataParser
import com.chencohensolutions.knesset.fixture
import com.chencohensolutions.knesset.json
import com.chencohensolutions.knesset.testApi
import com.chencohensolutions.knesset.ui.detail.DetailState
import com.chencohensolutions.knesset.ui.detail.DetailViewModel
import com.chencohensolutions.knesset.ui.explorer.ExplorerFilters
import com.chencohensolutions.knesset.ui.explorer.FieldFilter
import com.chencohensolutions.knesset.ui.explorer.Op
import com.chencohensolutions.knesset.ui.legislation.loadBill
import com.chencohensolutions.knesset.ui.lobbyists.LobbyistsAvailability
import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class ExplorerTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun everyEntitySetInTheServiceDocumentHasFields() {
        val sets = ODataClient.json.decodeFromString(ServiceDocument.serializer(), fixture("service_document.json")).value.map { it.name }
        val fields = MetadataParser.parse(fixture("metadata.xml"))
        assertEquals(48, sets.size)
        val missing = sets.filter { fields[it].isNullOrEmpty() }
        assertTrue(missing.isEmpty(), "sets without fields: $missing")
        assertTrue(sets.all { s -> fields[s]!!.any { it.name == "Id" } })
    }

    @Test
    fun identityNumberIsNeverAField() {
        val lobbyist = MetadataParser.parse(fixture("metadata.xml"))["V_Lobbyists"]!!
        assertTrue(lobbyist.none { it.name == "IdentityNumber" })
        assertTrue(lobbyist.any { it.name == "FullName" })
        val withField = MetadataParser.parse("""<EntityType Name="V_Lobbyist"><Property Name="Id" Type="Edm.Int32" /><Property Name="IdentityNumber" Type="Edm.String" /></EntityType><EntitySet Name="V_Lobbyists" EntityType="OdataService.DAL.Lobbyist.V_Lobbyist" />""")
        assertEquals(listOf("Id"), withField["V_Lobbyists"]!!.map { it.name })
    }

    @Test
    fun filterFragmentsAreTypedByField() {
        val name = FieldDef("Name", "String")
        val num = FieldDef("KnessetNum", "Int32")
        val flag = FieldDef("IsCurrent", "Boolean")
        val date = FieldDef("StartDate", "DateTimeOffset")
        assertEquals("contains(Name,'חוק ה''ספר')", ExplorerFilters.fragment(FieldFilter(name, Op.CONTAINS, "חוק ה'ספר")))
        assertEquals("KnessetNum eq 25", ExplorerFilters.fragment(FieldFilter(num, Op.EQ, " 25 ")))
        assertNull(ExplorerFilters.fragment(FieldFilter(num, Op.EQ, "abc")))
        assertNull(ExplorerFilters.fragment(FieldFilter(num, Op.CONTAINS, "2")))
        assertEquals("IsCurrent eq true", ExplorerFilters.fragment(FieldFilter(flag, Op.EQ, "כן")))
        assertEquals("StartDate ge 2026-07-28T00:00:00+03:00", ExplorerFilters.fragment(FieldFilter(date, Op.GE, "28/07/2026")))
        assertEquals("StartDate ne null", ExplorerFilters.fragment(FieldFilter(date, Op.NOT_NULL, "")))
    }

    @Test
    fun foreignKeysLinkToTheirSets() {
        assertEquals("KNS_Person", MetadataParser.target("MkId"))
        assertEquals("KNS_Committee", MetadataParser.target("ParentCommitteeID"))
        assertNull(MetadataParser.target("Id"))
        assertEquals("נפתלי בנט", MetadataParser.titleOf(mapOf("Id" to "1", "FirstName" to "נפתלי", "LastName" to "בנט")))
    }

    @Test
    fun lobbyistsHideWhenTheEndpointAnswers404() = runTest {
        val api = testApi { respond("", HttpStatusCode.NotFound) }
        val availability = LobbyistsAvailability(api)
        availability.check()
        assertFalse(availability.available.value!!)
    }

    @Test
    fun previouslyOpenedScreenRendersOfflineWithBanner() = runTest(dispatcher) {
        var online = true
        val store = MemoryCacheStore()
        val clock = FakeClock()
        val api = testApi(store = store, clock = clock, dispatcher = dispatcher) { req ->
            if (!online) throw kotlinx.io.IOException("offline")
            if (req.url.toString().contains("KNS_Bill")) json(fixture("bill_2229019_full.json")) else respond("", HttpStatusCode.NotFound)
        }
        val first = DetailViewModel(api) { loadBill(this, 2229019) }
        advanceUntilIdle()
        assertTrue((first.state.value as DetailState.Ready).offline.not())
        first.viewModelScope.cancel()

        online = false
        clock.now += 2.hours
        val second = DetailViewModel(api) { loadBill(this, 2229019) }
        advanceUntilIdle()
        val state = second.state.value
        assertTrue(state is DetailState.Ready, state.toString())
        assertTrue(state.offline, "offline banner")
        assertTrue(state.content.title.contains("נוכחות עורך דין"))
        second.viewModelScope.cancel()
    }
}
