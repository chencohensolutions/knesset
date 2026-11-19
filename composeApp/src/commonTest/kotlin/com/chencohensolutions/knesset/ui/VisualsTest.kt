package com.chencohensolutions.knesset.ui

import com.chencohensolutions.knesset.domain.BillStages
import com.chencohensolutions.knesset.domain.Hemicycle
import com.chencohensolutions.knesset.domain.StatusStyles
import com.chencohensolutions.knesset.domain.StatusTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VisualsTest {
    @Test
    fun hemicycleHasExactlyTheSeatsAskedForInsideTheHalfCircle() {
        listOf(1, 7, 53, 120, 133).forEach { n ->
            val seats = Hemicycle.layout(n)
            assertEquals(n, seats.size, "count $n")
            assertTrue(seats.all { it.y >= -1e-4f && it.x in -1.0001f..1.0001f })
        }
    }

    @Test
    fun hemicycleSeatsRunFromStartToEnd() {
        val seats = Hemicycle.layout(120)
        val firstTen = seats.take(10).map { it.x }.average()
        val lastTen = seats.takeLast(10).map { it.x }.average()
        assertTrue(firstTen < -0.5 && lastTen > 0.5, "$firstTen .. $lastTen")
    }

    @Test
    fun billStagesFromStatusWording() {
        assertEquals(0, BillStages.stageOf("הונחה על שולחן הכנסת לדיון מוקדם"))
        assertEquals(0, BillStages.stageOf("בוועדת הכנסת לקביעת הוועדה המטפלת"))
        assertEquals(1, BillStages.stageOf("הכנה לקריאה ראשונה"))
        assertEquals(1, BillStages.stageOf("אושרה בוועדה לקריאה ראשונה"))
        assertEquals(2, BillStages.stageOf("עברה בקריאה ראשונה"))
        assertEquals(3, BillStages.stageOf("הכנה לקריאה שנייה-שלישית"))
        assertEquals(4, BillStages.stageOf("התקבלה בקריאה שלישית"))
        assertEquals(4, BillStages.stageOf(null, publicationDate = true))
    }

    @Test
    fun statusCategories() {
        assertEquals(StatusTone.PASSED, StatusStyles.tone("התקבלה בקריאה שלישית"))
        assertEquals(StatusTone.STOPPED, StatusStyles.tone("הבקשה לדין רציפות נדחתה"))
        assertEquals(StatusTone.IN_PROGRESS, StatusStyles.tone("הכנה לקריאה ראשונה"))
        assertEquals(StatusTone.NEUTRAL, StatusStyles.tone(null))
    }
}
