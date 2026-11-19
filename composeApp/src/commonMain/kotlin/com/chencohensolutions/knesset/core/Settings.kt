package com.chencohensolutions.knesset.core

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UiLanguage(val tag: String) { HEBREW("he"), ENGLISH("en") }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** User preferences. Stored on the device only. */
class AppSettings(private val store: Settings) {
    private val _language = MutableStateFlow(
        UiLanguage.entries.firstOrNull { it.tag == store.getStringOrNull(KEY_LANGUAGE) } ?: UiLanguage.HEBREW,
    )
    val language: StateFlow<UiLanguage> = _language.asStateFlow()

    private val _theme = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == store.getStringOrNull(KEY_THEME) } ?: ThemeMode.SYSTEM,
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    /** A fixed Knesset number to open with, or null for the current Knesset. */
    private val _defaultKnesset = MutableStateFlow(store.getIntOrNull(KEY_DEFAULT_KNESSET))
    val defaultKnesset: StateFlow<Int?> = _defaultKnesset.asStateFlow()

    private val _lastSync = MutableStateFlow(store.getLongOrNull(KEY_LAST_SYNC))
    val lastSync: StateFlow<Long?> = _lastSync.asStateFlow()

    fun setLanguage(value: UiLanguage) {
        store.putString(KEY_LANGUAGE, value.tag); _language.value = value
    }

    fun setTheme(value: ThemeMode) {
        store.putString(KEY_THEME, value.name); _theme.value = value
    }

    fun setDefaultKnesset(value: Int?) {
        if (value == null) store.remove(KEY_DEFAULT_KNESSET) else store.putInt(KEY_DEFAULT_KNESSET, value)
        _defaultKnesset.value = value
    }

    fun markSynced(epochMillis: Long) {
        store.putLong(KEY_LAST_SYNC, epochMillis); _lastSync.value = epochMillis
    }

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_THEME = "theme"
        const val KEY_DEFAULT_KNESSET = "default_knesset"
        const val KEY_LAST_SYNC = "last_sync"
    }
}
