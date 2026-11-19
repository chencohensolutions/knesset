package com.chencohensolutions.knesset

import com.chencohensolutions.knesset.core.AppSettings
import com.chencohensolutions.knesset.core.ThemeMode
import com.chencohensolutions.knesset.core.UiLanguage
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsTest {
    @Test
    fun defaultsAreHebrewSystemThemeAndCurrentKnesset() {
        val settings = AppSettings(MapSettings())
        assertEquals(UiLanguage.HEBREW, settings.language.value)
        assertEquals(ThemeMode.SYSTEM, settings.theme.value)
        assertNull(settings.defaultKnesset.value)
    }

    @Test
    fun valuesPersistAcrossInstances() {
        val store = MapSettings()
        AppSettings(store).apply {
            setLanguage(UiLanguage.ENGLISH); setTheme(ThemeMode.DARK); setDefaultKnesset(25)
        }
        val reloaded = AppSettings(store)
        assertEquals(UiLanguage.ENGLISH, reloaded.language.value)
        assertEquals(ThemeMode.DARK, reloaded.theme.value)
        assertEquals(25, reloaded.defaultKnesset.value)
    }
}
