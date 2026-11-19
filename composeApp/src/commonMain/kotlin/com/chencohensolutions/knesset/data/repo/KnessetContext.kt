package com.chencohensolutions.knesset.data.repo

import com.chencohensolutions.knesset.core.AppSettings
import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.core.odata.KnessetError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The global Knesset selector (plan 2.2) and the startup state. */
class KnessetContext(private val lookups: LookupRepo, private val settings: AppSettings) {

    sealed interface Startup {
        data object Loading : Startup
        data object Ready : Startup
        data class Failed(val error: KnessetError) : Startup
    }

    data class Selection(
        val terms: List<Int> = emptyList(),
        val current: Int? = null,
        /** null means "all Knessets". */
        val selected: Int? = null,
    ) {
        val isCurrent get() = selected != null && selected == current
        fun ttl(base: Ttl) = Ttl.forKnesset(base, selected, current)
    }

    private val _startup = MutableStateFlow<Startup>(Startup.Loading)
    val startup: StateFlow<Startup> = _startup.asStateFlow()

    private val _selection = MutableStateFlow(Selection())
    val selection: StateFlow<Selection> = _selection.asStateFlow()

    suspend fun start(force: Boolean = false) {
        _startup.value = Startup.Loading
        try {
            val terms = lookups.knessetTerms(force)
            val preferred = settings.defaultKnesset.value?.takeIf { it in terms.numbers } ?: terms.current
            _selection.update { old ->
                Selection(terms.numbers, terms.current, if (old.terms.isEmpty()) preferred else old.selected)
            }
            _startup.value = Startup.Ready
        } catch (e: KnessetError) {
            _startup.value = Startup.Failed(e)
        }
    }

    fun select(knesset: Int?) = _selection.update { it.copy(selected = knesset) }
}
