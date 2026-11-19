package com.chencohensolutions.knesset.core.odata

import kotlin.time.Instant

/**
 * Builds an OData v4 request URL. Values are percent-encoded; the characters the Knesset server
 * accepts literally in system query options (`$ ( ) , ; = :`) are left as they are.
 */
class ODataQuery(val entitySet: String) {
    var key: Int? = null
    val filters = mutableListOf<String>()
    var orderBy: String? = null
    var top: Int? = null
    var skip: Int? = null
    var select: List<String> = emptyList()
    var expand: List<String> = emptyList()
    var count: Boolean = false

    fun filter(fragment: String?) = apply { if (!fragment.isNullOrBlank()) filters += fragment }

    fun copy(block: ODataQuery.() -> Unit = {}): ODataQuery = ODataQuery(entitySet).also { q ->
        q.key = key; q.filters += filters; q.orderBy = orderBy; q.top = top; q.skip = skip
        q.select = select; q.expand = expand; q.count = count
    }.apply(block)

    fun toUrl(base: String = BASE_URL): String {
        val path = buildString {
            append(base.trimEnd('/')).append('/').append(entitySet)
            key?.let { append('(').append(it).append(')') }
        }
        val params = buildList {
            if (filters.isNotEmpty()) add("\$filter" to filters.joinToString(" and ") { wrap(it) })
            orderBy?.let { add("\$orderby" to it) }
            top?.let { add("\$top" to it.toString()) }
            skip?.let { add("\$skip" to it.toString()) }
            if (select.isNotEmpty()) add("\$select" to select.joinToString(","))
            if (expand.isNotEmpty()) add("\$expand" to expand.joinToString(","))
            if (count) add("\$count" to "true")
        }
        if (params.isEmpty()) return path
        return path + "?" + params.joinToString("&") { (k, v) -> k + "=" + encode(v) }
    }

    private fun wrap(fragment: String): String =
        if (filters.size > 1 && fragment.contains(" or ") && !isWrapped(fragment)) "($fragment)" else fragment

    private fun isWrapped(f: String): Boolean {
        if (!f.startsWith("(") || !f.endsWith(")")) return false
        var depth = 0
        f.forEachIndexed { i, c ->
            if (c == '(') depth++ else if (c == ')') depth--
            if (depth == 0 && i < f.length - 1) return false
        }
        return true
    }

    companion object {
        const val BASE_URL = "https://knesset.gov.il/OdataV4/ParliamentInfo/"
        /** The server never returns more than this many rows per request. */
        const val SERVER_PAGE_CAP = 100

        private const val LITERAL = "\$(),;=:"

        fun encode(value: String): String = buildString {
            value.encodeToByteArray().forEach { byte ->
                val c = (byte.toInt() and 0xFF).toChar()
                if (c.isLetterOrDigit() && c.code < 128 || c in "-._~" || c in LITERAL) append(c)
                else append('%').append(HEX[(byte.toInt() shr 4) and 0xF]).append(HEX[byte.toInt() and 0xF])
            }
        }

        private const val HEX = "0123456789ABCDEF"
    }
}

/** Safe builders for `$filter` fragments. */
object F {
    fun eq(field: String, value: Int) = "$field eq $value"
    fun eq(field: String, value: Boolean) = "$field eq $value"
    fun eqText(field: String, value: String) = "$field eq ${literal(value)}"
    fun ne(field: String, value: Int) = "$field ne $value"
    fun contains(field: String, text: String) = "contains($field,${literal(text)})"
    fun ge(field: String, value: Instant) = "$field ge ${dateLiteral(value)}"
    fun le(field: String, value: Instant) = "$field le ${dateLiteral(value)}"
    fun isNotNull(field: String) = "$field ne null"
    fun isNull(field: String) = "$field eq null"
    fun anyOf(field: String, values: Collection<Int>): String? = when (values.size) {
        0 -> null
        1 -> eq(field, values.first())
        else -> values.joinToString(" or ", "(", ")") { eq(field, it) }
    }
    fun or(vararg fragments: String?): String? {
        val parts = fragments.filterNotNull()
        return when (parts.size) { 0 -> null; 1 -> parts[0]; else -> parts.joinToString(" or ", "(", ")") }
    }
    fun and(vararg fragments: String?): String? {
        val parts = fragments.filterNotNull()
        return when (parts.size) { 0 -> null; 1 -> parts[0]; else -> parts.joinToString(" and ") { if (it.contains(" or ") && !it.startsWith("(")) "($it)" else it } }
    }

    /** Every whitespace separated token must appear in at least one of [fields]. */
    fun allTokensIn(text: String, vararg fields: String): String? {
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        return and(*tokens.map { t -> or(*fields.map { contains(it, t) }.toTypedArray()) }.toTypedArray())
    }

    /** A quoted OData string literal; single quotes are doubled. */
    fun literal(text: String) = "'" + text.replace("'", "''") + "'"

    /** `2026-11-19T00:00:00+02:00` in Israel time, which the server accepts in `$filter`. */
    fun dateLiteral(value: Instant): String = IsraelTime.isoWithOffset(value)
}
