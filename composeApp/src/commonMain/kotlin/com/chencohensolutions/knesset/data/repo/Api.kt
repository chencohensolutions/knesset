package com.chencohensolutions.knesset.data.repo

import com.chencohensolutions.knesset.core.cache.CachedFetcher
import com.chencohensolutions.knesset.core.cache.Resource
import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.core.odata.KnessetError
import com.chencohensolutions.knesset.core.odata.ODataClient
import com.chencohensolutions.knesset.core.odata.ODataPage
import com.chencohensolutions.knesset.core.odata.ODataQuery
import com.chencohensolutions.knesset.core.paging.PageResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Typed access to the API through the cache. Shared by every repository. */
class Api(val fetcher: CachedFetcher, val json: Json = ODataClient.json) {

    fun <T> decode(serializer: KSerializer<T>, body: String): T = try {
        json.decodeFromString(serializer, body)
    } catch (e: SerializationException) {
        throw KnessetError.Parse(e)
    } catch (e: IllegalArgumentException) {
        throw KnessetError.Parse(e)
    }

    /** A whole collection response, cached and revalidated. */
    fun <T> streamList(q: ODataQuery, serializer: KSerializer<T>, ttl: Ttl, force: Boolean = false): Flow<Resource<List<T>>> =
        fetcher.stream(q.toUrl(), ttl, force).map { r -> r.decodeWith { decode(ODataPage.serializer(serializer), it).value } }

    /** One entity by id. Uses `$filter=Id eq X` because a by-key GET with a collection expand fails (plan 1.2). */
    fun <T> streamDetail(
        entitySet: String,
        id: Int,
        serializer: KSerializer<T>,
        expand: List<String> = emptyList(),
        ttl: Ttl = Ttl.DETAIL,
        force: Boolean = false,
    ): Flow<Resource<T>> {
        val q = ODataQuery(entitySet).apply { filter("Id eq $id"); this.expand = expand }
        return fetcher.stream(q.toUrl(), ttl, force).map { r ->
            r.decodeWith { decode(ODataPage.serializer(serializer), it).value.firstOrNull() ?: throw KnessetError.NotFound() }
        }
    }

    suspend fun <T> page(q: ODataQuery, serializer: KSerializer<T>, ttl: Ttl, force: Boolean = false): PageResult<T> {
        val fetched = fetcher.get(q.toUrl(), ttl, force)
        val page = decode(ODataPage.serializer(serializer), fetched.body)
        return PageResult(page.value, page.count, offline = fetched.isStale)
    }

    suspend fun <T> list(q: ODataQuery, serializer: KSerializer<T>, ttl: Ttl, force: Boolean = false): List<T> =
        page(q, serializer, ttl, force).rows

    /** Every row of a query, paging with `$top`/`$skip` up to [max] rows. */
    suspend fun <T> all(q: ODataQuery, serializer: KSerializer<T>, ttl: Ttl, max: Int = 2_000, force: Boolean = false): List<T> {
        val out = mutableListOf<T>()
        var skip = 0
        while (out.size < max) {
            val rows = list(q.copy { top = ODataQuery.SERVER_PAGE_CAP; this.skip = skip }, serializer, ttl, force)
            out += rows
            if (rows.size < ODataQuery.SERVER_PAGE_CAP) break
            skip += rows.size
        }
        return out
    }

    /** Raw JSON objects, for the Data Explorer. */
    suspend fun rawPage(q: ODataQuery, ttl: Ttl, force: Boolean = false): PageResult<JsonObject> = page(q, JsonObject.serializer(), ttl, force)

    suspend fun text(url: String, ttl: Ttl, force: Boolean = false): String = fetcher.get(url, ttl, force).body

    private inline fun <R> Resource<String>.decodeWith(crossinline decodeBody: (String) -> R): Resource<R> = when (this) {
        Resource.Loading -> Resource.Loading
        is Resource.Data -> try {
            Resource.Data(decodeBody(value), isStale, fromCache)
        } catch (e: KnessetError) {
            Resource.Error(e)
        }
        is Resource.Error -> Resource.Error(error, cached?.let { runCatching { decodeBody(it) }.getOrNull() })
    }
}
