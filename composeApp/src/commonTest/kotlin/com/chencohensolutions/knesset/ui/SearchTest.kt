package com.chencohensolutions.knesset.ui

import com.chencohensolutions.knesset.core.AppSettings
import com.chencohensolutions.knesset.data.repo.KnessetContext
import com.chencohensolutions.knesset.data.repo.LookupRepo
import com.chencohensolutions.knesset.data.repo.RecentSearchRepo
import com.chencohensolutions.knesset.decodedUrl
import com.chencohensolutions.knesset.json
import com.chencohensolutions.knesset.route
import com.chencohensolutions.knesset.testApi
import com.chencohensolutions.knesset.ui.search.SearchViewModel
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun surnameReturnsMembersAndBillsInGroups() = runTest(dispatcher) {
        val urls = mutableListOf<String>()
        val api = testApi(dispatcher = dispatcher) { req ->
            val url = req.decodedUrl()
            urls += url
            when {
                url.contains("KNS_KnessetDates") && url.contains("\$skip=0") -> route(req, "KNS_KnessetDates" to "knesset_dates_all.json")
                url.contains("KNS_Status") -> route(req, "KNS_Status" to "status.json")
                url.contains("KNS_Person?") -> route(req, "KNS_Person" to "search_persons_bennett.json")
                url.contains("KNS_Bill?") -> route(req, "KNS_Bill" to "search_bills_bennett.json")
                else -> json("""{"value":[],"@odata.count":0}""")
            }
        }
        val lookups = LookupRepo(api)
        val ctx = KnessetContext(lookups, AppSettings(MapSettings())).also { it.start() }
        ctx.select(25)
        val recent = FakeRecent()
        val vm = SearchViewModel(api, lookups, ctx, recent.repo, "")
        vm.setQuery("בנט")
        advanceUntilIdle()
        val groups = vm.state.value.groups
        val members = groups.first { it.kind == Kind.MEMBER }
        val bills = groups.first { it.kind == Kind.BILL }
        assertEquals(listOf("נפתלי בנט", "בנטוב").map { it.split(" ").last() }, members.rows.map { it.title.split(" ").last() })
        assertTrue(members.rows.all { it.route is MemberRoute })
        assertEquals(5, bills.rows.size)
        assertEquals(6L, bills.count, "count enables show all")
        assertEquals(SearchViewModel.KINDS, groups.map { it.kind }, "every kind ran, in order")
        val personUrl = urls.first { it.contains("KNS_Person?") }
        assertTrue(personUrl.contains("(contains(FirstName,'בנט') or contains(LastName,'בנט'))"), personUrl)
        assertTrue(!personUrl.contains("KnessetNum"), "members are searched across all people")
        assertTrue(urls.first { it.contains("KNS_Bill?") }.contains("KnessetNum eq 25 and contains(Name,'בנט')"))
        assertEquals(listOf("בנט"), recent.saved)
        vm.viewModelScope.cancel()
    }
}

/** RecentSearchRepo over an in-memory database. */
expect class FakeRecent() {
    val repo: RecentSearchRepo
    val saved: List<String>
}
