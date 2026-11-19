package com.chencohensolutions.knesset.ui

import com.chencohensolutions.knesset.core.odata.parseODataInstant
import com.chencohensolutions.knesset.data.dto.PersonToPositionDto
import com.chencohensolutions.knesset.domain.Bidi
import com.chencohensolutions.knesset.domain.Hemicycle
import com.chencohensolutions.knesset.ui.people.dateSpan
import com.chencohensolutions.knesset.ui.theme.Contrast
import com.chencohensolutions.knesset.ui.theme.KnessetColors
import com.chencohensolutions.knesset.ui.theme.Section
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RtlAndContrastTest {
    @Test
    fun bodyTextReachesAaInBothThemes() {
        listOf(KnessetColors.Light, KnessetColors.Dark).forEach { c ->
            listOf(c.background, c.surface, c.surfaceRaised).forEach { bg ->
                assertTrue(Contrast.ratio(c.textPrimary, bg) >= 4.5, "primary on $bg: ${Contrast.ratio(c.textPrimary, bg)}")
                assertTrue(Contrast.ratio(c.textSecondary, bg) >= 4.5, "secondary on $bg: ${Contrast.ratio(c.textSecondary, bg)}")
            }
        }
    }

    @Test
    fun whiteHeaderTextReachesThreeToOneOnEveryGradientStop() {
        Section.entries.forEach { s ->
            listOf(s.gradientStart, s.gradientEnd).forEach { stop ->
                val r = Contrast.ratio(Color.White, Contrast.underScrim(stop))
                assertTrue(r >= 3.0, "$s $stop: $r")
            }
        }
    }

    @Test
    fun accentsAreReadableOnTheirContainers() {
        Section.entries.forEach { s ->
            assertTrue(Contrast.ratio(s.accentLight, s.containerLight) >= 3.0, "$s light")
            assertTrue(Contrast.ratio(s.accentDark, KnessetColors.Dark.container(s)) >= 3.0, "$s dark")
        }
    }

    @Test
    fun isolatesWrapAndStrip() {
        assertEquals("⁨התשפ\"ד-2024⁩", Bidi.isolate("התשפ\"ד-2024"))
        assertEquals("⁦https://knesset.gov.il/OdataV4/ParliamentInfo/⁩", Bidi.ltr("https://knesset.gov.il/OdataV4/ParliamentInfo/"))
        assertEquals("כנסת 26 · פ/59/26", Bidi.strip(Bidi.isolate("כנסת 26") + " · " + Bidi.ltr("פ/59/26")))
    }

    @Test
    fun openDateRangesAreWordedNotDangling() {
        val since = PersonToPositionDto(id = 1, startDate = parseODataInstant("2026-11-11T00:00:00+02:00"))
        val until = PersonToPositionDto(id = 2, finishDate = parseODataInstant("2027-07-01T00:00:00+03:00"))
        val both = PersonToPositionDto(id = 3, startDate = parseODataInstant("2022-11-15T00:00:00+02:00"), finishDate = parseODataInstant("2026-11-09T00:00:00+02:00"))
        assertEquals("מאז 11/11/2026", Bidi.strip(dateSpan(since)))
        assertEquals("עד 01/07/2027", Bidi.strip(dateSpan(until)))
        assertEquals("15/11/2022–09/11/2026", Bidi.strip(dateSpan(both)))
        assertTrue(dateSpan(since).contains('⁨'), "dates are isolated")
    }

    @Test
    fun hemicycleMirrorsForRightToLeft() {
        // The chart draws x as-is in LTR and -x in RTL, so the first seats sit on the reading start side.
        val seats = Hemicycle.layout(24, rows = 1)
        val ltrFirst = seats.first().x
        val rtlFirst = -seats.first().x
        assertTrue(ltrFirst < 0 && rtlFirst > 0)
    }
}
