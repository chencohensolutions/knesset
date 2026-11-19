package com.chencohensolutions.knesset.data

import com.chencohensolutions.knesset.core.AppSettings
import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.core.odata.ODataClient
import com.chencohensolutions.knesset.core.odata.ODataPage
import com.chencohensolutions.knesset.data.dto.BillDto
import com.chencohensolutions.knesset.data.dto.PersonToPositionDto
import com.chencohensolutions.knesset.data.dto.PlenumVoteDto
import com.chencohensolutions.knesset.data.dto.PlenumVoteResultDto
import com.chencohensolutions.knesset.data.repo.KnessetContext
import com.chencohensolutions.knesset.data.repo.LookupRepo
import com.chencohensolutions.knesset.decodedUrl
import com.chencohensolutions.knesset.fixture
import com.chencohensolutions.knesset.json
import com.chencohensolutions.knesset.route
import com.chencohensolutions.knesset.testApi
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val EMPTY = """{"value":[]}"""

class LookupAndDtoTest {
    private val json = ODataClient.json

    @Test
    fun knessetTermsFromFixture() = runTest {
        val urls = mutableListOf<String>()
        // The fixture is the first page of 100 rows; the second page is served empty.
        val api = testApi { req ->
            urls += req.decodedUrl()
            if (req.decodedUrl().contains("\$skip=100")) json(EMPTY) else route(req, "KNS_KnessetDates" to "knesset_dates_all.json")
        }
        val terms = LookupRepo(api).knessetTerms()
        assertEquals(26, terms.current)
        assertEquals(26, terms.numbers.first())
        assertEquals(terms.numbers.sortedDescending(), terms.numbers)
        assertTrue(25 in terms.numbers && 20 in terms.numbers, terms.numbers.toString())
        assertEquals(2, urls.size, "pages until a short page")
        assertTrue(urls[0].contains("\$orderby=KnessetNum desc,Plenum desc,Id desc&\$top=100&\$skip=0"), urls[0])
    }

    @Test
    fun selectorDefaultsToCurrentOrSavedKnesset() = runTest {
        val api = testApi { req ->
            if (req.decodedUrl().contains("\$skip=100")) json(EMPTY) else route(req, "KNS_KnessetDates" to "knesset_dates_all.json")
        }
        val settings = AppSettings(MapSettings())
        val ctx = KnessetContext(LookupRepo(api), settings)
        ctx.start()
        assertEquals(KnessetContext.Startup.Ready, ctx.startup.value)
        assertEquals(26, ctx.selection.value.selected)
        assertTrue(ctx.selection.value.isCurrent)
        assertEquals(Ttl.LIST, ctx.selection.value.ttl(Ttl.LIST))
        ctx.select(24)
        assertEquals(Ttl.PAST_KNESSET, ctx.selection.value.ttl(Ttl.LIST))
        ctx.select(null)
        assertEquals(null, ctx.selection.value.selected)

        settings.setDefaultKnesset(25)
        val ctx2 = KnessetContext(LookupRepo(api), settings)
        ctx2.start()
        assertEquals(25, ctx2.selection.value.selected)
    }

    @Test
    fun startupFailsWithoutNetworkOrCache() = runTest {
        val api = testApi { throw kotlinx.io.IOException("no network") }
        val ctx = KnessetContext(LookupRepo(api), AppSettings(MapSettings()))
        ctx.start()
        assertTrue(ctx.startup.value is KnessetContext.Startup.Failed)
    }

    @Test
    fun statusesAndItemTypesAreKeyed() = runTest {
        val api = testApi { req -> route(req, "KNS_Status" to "status.json", "KNS_ItemType" to "item_type.json") }
        val repo = LookupRepo(api)
        val statuses = repo.statuses()
        assertTrue(statuses.size >= 80, "statuses ${statuses.size}")
        assertEquals("הצעת חוק", repo.itemTypes()[2]?.desc)
        assertEquals("KNS_Bill", repo.itemTypes()[2]?.tableName)
    }

    @Test
    fun billDetailDecodesWithExpands() {
        val bill = json.decodeFromString(ODataPage.serializer(BillDto.serializer()), fixture("bill_2229019_detail.json")).value.single()
        assertEquals(2229019, bill.id)
        assertEquals("ממשלתית", bill.subTypeDesc)
        assertEquals(Instant.parse("2026-07-28T21:00:00Z"), bill.publicationDate)
        assertEquals(11, bill.billInitiator.size)
        assertTrue(bill.billInitiator.all { it.person != null })
        assertTrue(bill.documentBill.any { it.filePath?.contains("\\") == true }, "raw paths keep backslashes")
        assertEquals(4, bill.billName.size)
        assertTrue(bill.status?.desc != null)
    }

    @Test
    fun votesAndResultsDecode() {
        val votes = json.decodeFromString(ODataPage.serializer(PlenumVoteDto.serializer()), fixture("votes_latest.json"))
        assertEquals(36171L, votes.count)
        assertEquals(5, votes.value.size)
        val results = json.decodeFromString(ODataPage.serializer(PlenumVoteResultDto.serializer()), fixture("vote_results_page1.json"))
        assertEquals(10, results.value.size)
        assertEquals(setOf("בעד", "נגד", "נוכח"), results.value.mapNotNull { it.resultDesc }.toSet())
    }

    @Test
    fun membersPageHasDuplicatesWithExpandedPerson() {
        val page = json.decodeFromString(ODataPage.serializer(PersonToPositionDto.serializer()), fixture("mks_k26_page1.json"))
        assertEquals(125L, page.count)
        assertEquals(100, page.value.size)
        assertTrue(page.value.map { it.personID }.distinct().size < page.value.size)
        assertTrue(page.value.all { it.person?.id == it.personID })
    }
}
