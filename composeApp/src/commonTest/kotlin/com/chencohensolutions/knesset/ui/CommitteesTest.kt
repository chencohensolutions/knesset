package com.chencohensolutions.knesset.ui

import com.chencohensolutions.knesset.core.odata.ODataClient
import com.chencohensolutions.knesset.core.odata.ODataPage
import com.chencohensolutions.knesset.data.dto.CommitteeSessionDto
import com.chencohensolutions.knesset.fixture
import com.chencohensolutions.knesset.ui.committees.calendarSpec
import com.chencohensolutions.knesset.ui.committees.todayInIsrael
import com.chencohensolutions.knesset.ui.list.RowContext
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class CommitteesTest {
    private val sessions = ODataClient.json.decodeFromString(ODataPage.serializer(CommitteeSessionDto.serializer()), fixture("calendar_k26.json")).value

    @Test
    fun calendarStartsAtIsraelMidnightAndShowsIsraelTimesByDay() {
        val spec = calendarSpec(LocalDate(2026, 11, 19))
        assertEquals(listOf("StartDate ge 2026-11-19T00:00:00+02:00"), spec.baseFilters)
        val rows = sessions.map { spec.toRow(it, RowContext(emptyMap())) }
        assertTrue(rows.isNotEmpty())
        // First fixture session: 2026-11-23T09:15:00+02:00
        assertEquals("23/11/2026", rows.first().group)
        assertEquals("09:15", rows.first().date)
        assertTrue(rows.all { it.route is CommitteeSessionRoute })
        assertTrue(rows.all { !it.title.startsWith("#") }, "committee names resolved from the expand")
        assertEquals(rows.map { it.group }, rows.map { it.group }.sortedBy { g -> g!!.split('/').reversed().joinToString("") })
    }

    @Test
    fun todayIsComputedInIsraelNotUtc() {
        // 23:30 UTC on Nov 18 is already Nov 19 in Israel (+02:00).
        assertEquals(LocalDate(2026, 11, 19), todayInIsrael(Instant.parse("2026-11-18T23:30:00Z")))
        // 21:30 UTC on Jul 27 is already Jul 28 in summer (+03:00).
        assertEquals(LocalDate(2026, 7, 28), todayInIsrael(Instant.parse("2026-07-27T21:30:00Z")))
    }
}
