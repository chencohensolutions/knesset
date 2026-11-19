package com.chencohensolutions.knesset.ui

import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.core.odata.ODataClient
import com.chencohensolutions.knesset.core.odata.ODataPage
import com.chencohensolutions.knesset.data.dto.PersonToPositionDto
import com.chencohensolutions.knesset.data.repo.FetchMode
import com.chencohensolutions.knesset.data.repo.MemberLogic
import com.chencohensolutions.knesset.data.repo.PeopleRepo
import com.chencohensolutions.knesset.decodedUrl
import com.chencohensolutions.knesset.fixture
import com.chencohensolutions.knesset.json
import com.chencohensolutions.knesset.route
import com.chencohensolutions.knesset.testApi
import com.chencohensolutions.knesset.ui.people.roleLabel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PeopleTest {
    private val rows = ODataClient.json.decodeFromString(ODataPage.serializer(PersonToPositionDto.serializer()), fixture("mks_k26_page1.json")).value

    @Test
    fun membersMatchFactionMembershipWithoutDuplicates() {
        val members = MemberLogic.fromPositions(rows)
        val expectedPeople = rows.filter { it.factionID != null }.mapNotNull { it.personID }.toSet()
        assertEquals(expectedPeople, members.map { it.personId }.toSet())
        assertEquals(members.size, members.map { it.personId }.distinct().size, "no duplicates")
        assertTrue(members.all { it.factionId != null && !it.factionName.isNullOrBlank() })
        assertTrue(members.all { it.name.isNotBlank() })
    }

    @Test
    fun repoPagesUntilShortPageAndQueriesFactionRows() = runTest {
        val urls = mutableListOf<String>()
        val api = testApi { req ->
            urls += req.decodedUrl()
            if (req.decodedUrl().contains("\$skip=100")) json("""{"value":[]}""") else route(req, "KNS_PersonToPosition" to "mks_k26_page1.json")
        }
        val members = PeopleRepo(api).members(26, FetchMode.NORMAL, Ttl.LIST)
        assertEquals(rows.mapNotNull { it.personID }.toSet().size, members.size)
        assertTrue(urls[0].contains("KNS_PersonToPosition?\$filter=KnessetNum eq 26 and FactionID ne null&\$orderby=Id&\$top=100&\$skip=0&\$expand=KNS_Person"), urls[0])
        assertEquals(2, urls.size)
    }

    @Test
    fun searchNeedsEveryToken() {
        val members = MemberLogic.fromPositions(rows)
        val ohana = members.filter { MemberLogic.matches(it, "אמיר אוחנה") }
        assertEquals(listOf(30300), ohana.map { it.personId })
    }

    @Test
    fun roleLabels() {
        val positions = ODataClient.json.decodeFromString(ODataPage.serializer(PersonToPositionDto.serializer()), fixture("person_30300_positions.json")).value
        val labels = positions.take(3).map(::roleLabel)
        assertEquals("יושב–ראש הכנסת", labels[0])
        assertTrue(labels[2].startsWith("חבר/ת סיעה · הליכוד"), labels[2])
    }
}
