package com.chencohensolutions.knesset.core

import com.chencohensolutions.knesset.core.odata.F
import com.chencohensolutions.knesset.core.odata.ODataQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ODataQueryTest {
    private val base = ODataQuery.BASE_URL

    @Test
    fun plainEntitySet() {
        assertEquals("${base}KNS_Status", ODataQuery("KNS_Status").toUrl())
    }

    @Test
    fun keyAndOptionsInFixedOrder() {
        val url = ODataQuery("KNS_Bill").apply {
            filter(F.eq("KnessetNum", 25))
            orderBy = "LastUpdatedDate desc,Id desc"
            top = 50; skip = 100; count = true
            select = listOf("Id", "Name")
        }.toUrl()
        assertEquals(
            "${base}KNS_Bill?\$filter=KnessetNum%20eq%2025&\$orderby=LastUpdatedDate%20desc,Id%20desc&\$top=50&\$skip=100&\$select=Id,Name&\$count=true",
            url,
        )
    }

    @Test
    fun hebrewTextIsUtf8PercentEncodedAndQuotesAreDoubled() {
        val url = ODataQuery("KNS_Bill").apply { filter(F.contains("Name", "חוק ה'בחירות")) }.toUrl()
        // ח = D7 97, the inner quote becomes '' and both quotes are %27
        assertEquals(
            "${base}KNS_Bill?\$filter=contains(Name,%27%D7%97%D7%95%D7%A7%20%D7%94%27%27%D7%91%D7%97%D7%99%D7%A8%D7%95%D7%AA%27)",
            url,
        )
    }

    @Test
    fun gershayimNeedsNoEscaping() {
        assertEquals("'התשפ\"ד'", F.literal("התשפ\"ד"))
        assertEquals("'it''s'", F.literal("it's"))
    }

    @Test
    fun dateLiteralUsesIsraelOffsetAndPlusIsEncoded() {
        val winter = Instant.parse("2026-11-18T22:00:00Z")
        assertEquals("StartDate ge 2026-11-19T00:00:00+02:00", F.ge("StartDate", winter))
        val url = ODataQuery("KNS_CommitteeSession").apply { filter(F.ge("StartDate", winter)) }.toUrl()
        assertEquals("${base}KNS_CommitteeSession?\$filter=StartDate%20ge%202026-11-19T00:00:00%2B02:00", url)
        val summer = Instant.parse("2026-07-27T21:00:00Z")
        assertEquals("StartDate le 2026-07-28T00:00:00+03:00", F.le("StartDate", summer))
    }

    @Test
    fun orFragmentsAreParenthesizedWhenCombined() {
        val q = ODataQuery("KNS_Bill").apply {
            filter(F.anyOf("StatusID", listOf(1, 2)))
            filter(F.eq("KnessetNum", 25))
        }
        assertEquals("(StatusID eq 1 or StatusID eq 2) and KnessetNum eq 25", q.filters.joinToString(" and "))
        assertEquals(null, F.anyOf("StatusID", emptyList()))
        assertEquals("StatusID eq 3", F.anyOf("StatusID", listOf(3)))
    }

    @Test
    fun personSearchRequiresEveryToken() {
        assertEquals(
            "(contains(FirstName,'אמיר') or contains(LastName,'אמיר')) and (contains(FirstName,'אוחנה') or contains(LastName,'אוחנה'))",
            F.allTokensIn("  אמיר   אוחנה ", "FirstName", "LastName"),
        )
        assertEquals(null, F.allTokensIn("   ", "FirstName"))
    }

    @Test
    fun nestedExpandKeepsDollarAndParentheses() {
        val url = ODataQuery("KNS_Bill").apply {
            filter("Id eq 5")
            expand = listOf("KNS_BillInitiator(\$expand=KNS_Person)", "KNS_Status")
        }.toUrl()
        assertEquals("${base}KNS_Bill?\$filter=Id%20eq%205&\$expand=KNS_BillInitiator(\$expand=KNS_Person),KNS_Status", url)
    }

    @Test
    fun copyIsIndependent() {
        val a = ODataQuery("KNS_Bill").apply { filter("A eq 1"); top = 5 }
        val b = a.copy { filter("B eq 2"); top = 10 }
        assertEquals(listOf("A eq 1"), a.filters)
        assertEquals(5, a.top)
        assertEquals(listOf("A eq 1", "B eq 2"), b.filters)
        assertEquals(10, b.top)
    }
}
