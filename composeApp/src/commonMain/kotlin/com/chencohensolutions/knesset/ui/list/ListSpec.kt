package com.chencohensolutions.knesset.ui.list

import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.data.dto.StatusDto
import com.chencohensolutions.knesset.data.repo.LookupRepo
import com.chencohensolutions.knesset.ui.Kind
import kotlinx.serialization.KSerializer
import org.jetbrains.compose.resources.StringResource

/** One row of any list. */
data class ListRow(
    val id: Int,
    val title: String,
    val subtitle: String? = null,
    val status: String? = null,
    val date: String? = null,
    val initials: String? = null,
    val bar: Triple<Int, Int, Int>? = null,
    val route: Any? = null,
    val url: String? = null,
)

data class Option(val value: String, val label: String)

/** What a filter chip needs to build its options. */
class FilterContext(val lookups: LookupRepo, val knesset: Int?)

sealed class FilterDef(val id: String, val label: StringResource) {
    /** On or off; when on, [fragment] is added to `$filter`. */
    class Toggle(id: String, label: StringResource, val fragment: String) : FilterDef(id, label)

    /** One or more values from [options]; [fragment] turns the chosen values into a `$filter` fragment. */
    class Choice(
        id: String,
        label: StringResource,
        val multi: Boolean,
        val options: suspend (FilterContext) -> List<Option>,
        val fragment: (Set<String>) -> String?,
    ) : FilterDef(id, label)

    /** A date range on [field] (ge start of first day, lt start of the day after the last). */
    class DateRange(id: String, label: StringResource, val field: String) : FilterDef(id, label)
}

data class SortOption(val label: StringResource, val orderBy: String)

/** Lookups a row mapper may need. */
class RowContext(val statuses: Map<Int, StatusDto>) {
    fun status(id: Int?): String? = id?.let { statuses[it]?.desc }
}

/** Declarative description of a list screen (plan 4.3: one generic list drives every list). */
class ListSpec<R>(
    val kind: Kind,
    val entitySet: String,
    val serializer: KSerializer<R>,
    val select: List<String> = emptyList(),
    val expand: List<String> = emptyList(),
    /** Builds the search fragment from the typed text, or null when the list has no search. */
    val search: ((String) -> String?)? = null,
    /** The field the Knesset selector filters on, or null when the entity has none. */
    val knessetField: String? = "KnessetNum",
    /** For entities without KnessetNum: a fragment that limits rows to the selected Knesset (for example by date). */
    val knessetFilter: (suspend (knesset: Int, lookups: LookupRepo) -> String?)? = null,
    val sorts: List<SortOption>,
    val filters: List<FilterDef> = emptyList(),
    val baseFilters: List<String> = emptyList(),
    val ttl: Ttl = Ttl.LIST,
    /** Drop rows whose id was already shown (the members query repeats people). */
    val distinctRows: Boolean = false,
    val needsStatuses: Boolean = false,
    val toRow: (R, RowContext) -> ListRow,
)
