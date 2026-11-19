package com.chencohensolutions.knesset.ui

import com.chencohensolutions.knesset.ui.theme.FactionColors
import com.chencohensolutions.knesset.ui.theme.KnessetColors
import com.chencohensolutions.knesset.ui.theme.Section
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ThemeTokensTest {
    @Test
    fun everyKindHasTheSectionOfItsTab() {
        assertEquals(Section.LEGISLATION, Section.of(Kind.BILL))
        assertEquals(Section.PLENUM, Section.of(Kind.VOTE))
        assertEquals(Section.COMMITTEES, Section.of(Kind.COMMITTEE_SESSION))
        assertEquals(Section.PEOPLE, Section.of(Kind.MEMBER))
        assertEquals(Section.HOME, Section.of(null))
    }

    @Test
    fun factionColorsAreStableAndWrap() {
        assertEquals(FactionColors.of(1113), FactionColors.of(1113))
        assertEquals(FactionColors.palette[1113 % 12], FactionColors.of(1113))
        assertEquals(FactionColors.of(5), FactionColors.of(17))
        assertNotEquals(FactionColors.of(5), FactionColors.of(6))
        assertEquals(FactionColors.palette.last(), FactionColors.of(null))
    }

    @Test
    fun darkContainerIsATintOfTheAccent() {
        assertEquals(Section.PLENUM.containerLight, KnessetColors.Light.container(Section.PLENUM))
        assertNotEquals(Section.PLENUM.containerLight, KnessetColors.Dark.container(Section.PLENUM))
        assertEquals(Section.PLENUM.accentDark, KnessetColors.Dark.accent(Section.PLENUM))
    }
}
