package com.chencohensolutions.knesset.ui

import com.chencohensolutions.knesset.core.AppSettings
import com.chencohensolutions.knesset.data.repo.FetchMode
import com.chencohensolutions.knesset.data.repo.KnessetContext
import com.chencohensolutions.knesset.data.repo.Loader
import com.chencohensolutions.knesset.data.repo.LookupRepo
import com.chencohensolutions.knesset.decodedUrl
import com.chencohensolutions.knesset.domain.Documents
import com.chencohensolutions.knesset.json
import com.chencohensolutions.knesset.route
import com.chencohensolutions.knesset.testApi
import com.chencohensolutions.knesset.ui.detail.Section
import com.chencohensolutions.knesset.ui.legislation.billsSpec
import com.chencohensolutions.knesset.ui.legislation.loadBill
import com.chencohensolutions.knesset.ui.list.ListViewModel
import com.chencohensolutions.knesset.resources.Res
import com.chencohensolutions.knesset.resources.documents
import com.chencohensolutions.knesset.resources.initiators
import com.chencohensolutions.knesset.resources.joined_signers
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegislationTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val emptyPage = """{"value":[]}"""

    @Test
    fun hebrewSearchStatusAndKindBuildOneQuery() = runTest(dispatcher) {
        val urls = mutableListOf<String>()
        val api = testApi(dispatcher = dispatcher) { req ->
            val url = req.decodedUrl()
            urls += url
            when {
                url.contains("KNS_KnessetDates") && url.contains("\$skip=0") -> route(req, "KNS_KnessetDates" to "knesset_dates_all.json")
                url.contains("KNS_KnessetDates") -> json(emptyPage)
                url.contains("KNS_Status") -> route(req, "KNS_Status" to "status.json")
                url.contains("contains(Name") -> route(req, "KNS_Bill" to "bills_search_hinuch.json")
                else -> route(req, "KNS_Bill" to "bills_k25_page1.json")
            }
        }
        val lookups = LookupRepo(api)
        val ctx = KnessetContext(lookups, AppSettings(MapSettings())).also { it.start() }
        ctx.select(25)
        val vm = ListViewModel(billsSpec, api, ctx, lookups)
        advanceUntilIdle()
        assertEquals(5, vm.pagerState.value.items.size)

        vm.setQuery("חינוך")
        advanceTimeBy(500); advanceUntilIdle()
        vm.setChoice("status", setOf("118", "177"))
        vm.setChoice("kind", setOf("פרטית"))
        advanceUntilIdle()

        val last = urls.last { it.contains("KNS_Bill?") }
        assertTrue(
            last.contains("\$filter=KnessetNum eq 25 and contains(Name,'חינוך') and SubTypeDesc eq 'פרטית' and (StatusID eq 118 or StatusID eq 177)"),
            last,
        )
        assertTrue(last.contains("\$orderby=LastUpdatedDate desc,Id desc&\$top=50&\$skip=0"), last)
        assertTrue(last.contains("\$count=true"), last)
        val state = vm.pagerState.value
        assertEquals(3, state.items.size)
        assertTrue(state.items.all { it.title.contains("חינוך") })
        assertTrue(state.items.all { it.status != null }, "status resolved through KNS_Status")
    }

    @Test
    fun billDetailHasInitiatorsAndOpenablePdfs() = runTest(dispatcher) {
        val urls = mutableListOf<String>()
        val api = testApi { req ->
            urls += req.decodedUrl()
            route(req, "KNS_Bill?\$filter=Id eq 2229019&\$expand" to "bill_2229019_full.json")
        }
        val content = loadBill(Loader(api, FetchMode.NORMAL), 2229019)
        assertTrue(urls.first().contains("KNS_Bill?\$filter=Id eq 2229019&\$expand=KNS_BillInitiator(\$expand=KNS_Person)"), "detail uses the filter form")
        val initiators = content.sections.first { it.title == Res.string.initiators } as Section.Links
        val joined = content.sections.first { it.title == Res.string.joined_signers } as Section.Links
        assertEquals(11, initiators.items.size + joined.items.size)
        assertTrue(initiators.items.all { it.route is MemberRoute })
        val docs = (content.sections.first { it.title == Res.string.documents } as Section.Documents).docs
        assertTrue(docs.isNotEmpty())
        assertEquals("PDF", docs.first().format, "PDF first")
        assertTrue(docs.all { it.url.startsWith("https://fs.knesset.gov.il/") && !it.url.contains("\\") }, docs.toString())
        assertEquals("ממשלתית", content.favoriteSubtitle)
    }

    @Test
    fun documentUrlNormalization() {
        assertEquals("https://fs.knesset.gov.il/25/law/25_ls1_5747645.pdf", Documents.normalizeUrl("https://fs.knesset.gov.il/25\\law\\25_ls1_5747645.pdf"))
        assertEquals("https://fs.knesset.gov.il/26/law/a.pdf", Documents.normalizeUrl("fs.knesset.gov.il/26\\law\\a.pdf"))
        assertEquals("https://main.knesset.gov.il/x", Documents.normalizeUrl("http://main.knesset.gov.il/x"))
        assertEquals(null, Documents.normalizeUrl("  "))
    }
}
