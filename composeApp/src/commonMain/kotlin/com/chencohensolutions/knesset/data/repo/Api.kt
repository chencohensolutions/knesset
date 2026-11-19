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

/** How a load treats the cache. */
enum class FetchMode {
    /** Any cached body, even stale, without touching the network (first half of stale-while-revalidate). */
    PREFER_CACHE,
    /** Fresh cache, else network, else stale cache. */
    NORMAL,
    /** Network first (pull to refresh), cache only when the network fails. */
    FORCE,
}

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

    suspend fun <T> page(q: ODataQuery, serializer: KSerializer<T>, ttl: Ttl, mode: FetchMode = FetchMode.NORMAL): PageResult<T> =
        pageWithFlags(q, serializer, ttl, mode).first

    /** The page plus whether it was stale (needs revalidation). */
    suspend fun <T> pageWithFlags(q: ODataQuery, serializer: KSerializer<T>, ttl: Ttl, mode: FetchMode): Pair<PageResult<T>, Boolean> {
        val fetched = fetcher.get(q.toUrl(), ttl, force = mode == FetchMode.FORCE, preferCache = mode == FetchMode.PREFER_CACHE)
        val page = decode(ODataPage.serializer(serializer), fetched.body)
        return PageResult(page.value, page.count, offline = fetched.offline) to fetched.isStale
    }

    suspend fun <T> list(q: ODataQuery, serializer: KSerializer<T>, ttl: Ttl, mode: FetchMode = FetchMode.NORMAL): List<T> =
        page(q, serializer, ttl, mode).rows

    /** Every row of a query, paging with `$top`/`$skip` up to [max] rows. */
    suspend fun <T> all(q: ODataQuery, serializer: KSerializer<T>, ttl: Ttl, max: Int = 2_000, force: Boolean = false): List<T> =
        Loader(this, if (force) FetchMode.FORCE else FetchMode.NORMAL).all(q, serializer, ttl, max)

    /** Raw JSON objects, for the Data Explorer. */
    suspend fun rawPage(q: ODataQuery, ttl: Ttl, mode: FetchMode = FetchMode.NORMAL): PageResult<JsonObject> = page(q, JsonObject.serializer(), ttl, mode)

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

/**
 * One screen load. Remembers whether anything came from a stale cache ([usedStale], so the screen
 * revalidates) or from the cache because the network failed ([offline], so it shows the banner).
 */
class Loader(val api: Api, val mode: FetchMode) {
    var usedStale = false
        private set
    var offline = false
        private set

    suspend fun <T> page(q: ODataQuery, serializer: KSerializer<T>, ttl: Ttl): PageResult<T> {
        val (page, stale) = api.pageWithFlags(q, serializer, ttl, mode)
        if (stale) usedStale = true
        if (page.offline) offline = true
        return page
    }

    suspend fun <T> list(q: ODataQuery, serializer: KSerializer<T>, ttl: Ttl): List<T> = page(q, serializer, ttl).rows

    /** One entity by id. Uses `$filter=Id eq X` because a by-key GET with a collection expand fails (plan 1.2). */
    suspend fun <T> one(entitySet: String, id: Int, serializer: KSerializer<T>, expand: List<String> = emptyList(), ttl: Ttl = Ttl.DETAIL): T {
        val q = ODataQuery(entitySet).apply { filter("Id eq $id"); this.expand = expand }
        return list(q, serializer, ttl).firstOrNull() ?: throw KnessetError.NotFound()
    }

    suspend fun <T> all(q: ODataQuery, serializer: KSerializer<T>, ttl: Ttl, max: Int = 2_000): List<T> {
        val out = mutableListOf<T>()
        var skip = 0
        while (out.size < max) {
            val rows = list(q.copy { top = ODataQuery.SERVER_PAGE_CAP; this.skip = skip }, serializer, ttl)
            out += rows
            if (rows.size < ODataQuery.SERVER_PAGE_CAP) break
            skip += rows.size
        }
        return out
    }

    /** Rows whose Id is in [ids], fetched in chunks of 20 (`(Id eq 1 or Id eq 2 ...)`). */
    suspend fun <T> byIds(entitySet: String, ids: Collection<Int>, serializer: KSerializer<T>, select: List<String>, ttl: Ttl = Ttl.DETAIL): List<T> =
        ids.distinct().chunked(20).flatMap { chunk ->
            list(ODataQuery(entitySet).apply {
                filter(com.chencohensolutions.knesset.core.odata.F.anyOf("Id", chunk)); this.select = select; top = chunk.size
            }, serializer, ttl)
        }

    /** A secondary section: failures hide the section instead of failing the screen. */
    suspend fun <T> optional(block: suspend Loader.() -> T): T? = try {
        block()
    } catch (e: KnessetError) {
        null
    }
}
