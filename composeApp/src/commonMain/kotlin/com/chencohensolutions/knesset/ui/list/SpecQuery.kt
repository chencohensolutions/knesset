package com.chencohensolutions.knesset.ui.list

import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.core.odata.F
import com.chencohensolutions.knesset.core.paging.PageResult
import com.chencohensolutions.knesset.data.repo.Api
import com.chencohensolutions.knesset.data.repo.FetchMode
import com.chencohensolutions.knesset.data.repo.LookupRepo

/** One page of a spec with a search text and the Knesset selector applied, outside a list screen. */
suspend fun <R> runSpec(
    spec: ListSpec<R>,
    api: Api,
    lookups: LookupRepo,
    knesset: Int?,
    text: String?,
    top: Int,
    ttl: Ttl = spec.ttl,
    mode: FetchMode = FetchMode.NORMAL,
): PageResult<ListRow> {
    val q = com.chencohensolutions.knesset.core.odata.ODataQuery(spec.entitySet).apply {
        spec.baseFilters.forEach { filter(it) }
        if (knesset != null) {
            if (spec.knessetField != null) filter(F.eq(spec.knessetField, knesset))
            else spec.knessetFilter?.let { filter(it(knesset, lookups)) }
        }
        if (!text.isNullOrBlank()) filter(spec.search?.invoke(text.trim()))
        val sort = spec.sorts.first().orderBy
        orderBy = if (sort.contains("Id ")) sort else "$sort,Id desc"
        select = spec.select
        expand = spec.expand
        this.top = top
        count = true
    }
    val ctx = RowContext(if (spec.needsStatuses) runCatching { lookups.statuses() }.getOrDefault(emptyMap()) else emptyMap())
    val page = api.page(q, spec.serializer, ttl, mode)
    return PageResult(page.rows.map { spec.toRow(it, ctx) }, page.count, page.offline)
}
