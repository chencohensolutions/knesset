package com.chencohensolutions.knesset.core.cache

import com.chencohensolutions.knesset.core.odata.KnessetError
import com.chencohensolutions.knesset.core.odata.ODataClient
import com.chencohensolutions.knesset.db.KnessetDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Time to live per kind of data (plan 3.5). */
enum class Ttl(val duration: Duration) {
    LOOKUP(7.days),
    PAST_KNESSET(30.days),
    DETAIL(1.hours),
    LIST(15.minutes),
    LIVE(5.minutes);

    companion object {
        /** A query pinned to a past Knesset rarely changes, so it lives longer. */
        fun forKnesset(base: Ttl, knesset: Int?, current: Int?): Ttl =
            if (knesset != null && current != null && knesset < current && base != LOOKUP) PAST_KNESSET else base
    }
}

sealed interface Resource<out T> {
    data object Loading : Resource<Nothing>
    data class Data<T>(val value: T, val isStale: Boolean = false, val fromCache: Boolean = false) : Resource<T>
    data class Error<T>(val error: KnessetError, val cached: T? = null) : Resource<T>

    fun valueOrNull(): T? = when (this) {
        is Data -> value
        is Error -> cached
        Loading -> null
    }
}

fun <T, R> Resource<T>.map(transform: (T) -> R): Resource<R> = when (this) {
    is Resource.Data -> Resource.Data(transform(value), isStale, fromCache)
    is Resource.Error -> Resource.Error(error, cached?.let(transform))
    Resource.Loading -> Resource.Loading
}

/** A body with where it came from. */
data class Fetched(val body: String, val fromCache: Boolean, val isStale: Boolean)

/** Storage behind the response cache. */
interface HttpCacheStore {
    suspend fun get(url: String): Pair<String, Long>?
    suspend fun put(url: String, body: String, fetchedAt: Long)
    suspend fun clear()
}

class SqlHttpCacheStore(private val db: KnessetDatabase, private val maxBytes: Long = 50L * 1024 * 1024) : HttpCacheStore {
    private val q get() = db.knessetQueries
    private var writesSinceTrim = 0

    override suspend fun get(url: String) = withContext(Dispatchers.IO) {
        q.cacheGet(url).executeAsOneOrNull()?.let { it.body to it.fetched_at }
    }

    override suspend fun put(url: String, body: String, fetchedAt: Long) {
        withContext(Dispatchers.IO) {
            q.cachePut(url, body, fetchedAt)
            if (++writesSinceTrim >= 20) { writesSinceTrim = 0; trim() }
        }
    }

    /** Deletes the oldest fifth of the rows until the cache fits in [maxBytes]. */
    fun trim() {
        var guard = 0
        while (q.cacheTotalSize().executeAsOne() > maxBytes && guard++ < 20) {
            val rows = q.cacheCount().executeAsOne()
            q.cacheDeleteOldest(maxOf(1, rows / 5))
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { q.cacheClear() }
    }
}

/**
 * Stale-while-revalidate over [ODataClient]: cached bodies are returned at once, and refetched when
 * older than their time to live. On a network failure the cached body is kept.
 */
class CachedFetcher(
    private val client: ODataClient,
    private val store: HttpCacheStore,
    private val clock: Clock = Clock.System,
) {
    /** Emits the cached body (if any), then the fresh one when the cache is missing or stale. */
    fun stream(url: String, ttl: Ttl, force: Boolean = false): Flow<Resource<String>> = flow {
        val cached = store.get(url)
        val now = clock.now().toEpochMilliseconds()
        val stale = cached != null && now - cached.second > ttl.duration.inWholeMilliseconds
        if (cached != null) {
            emit(Resource.Data(cached.first, isStale = stale, fromCache = true))
            if (!stale && !force) return@flow
        } else {
            emit(Resource.Loading)
        }
        try {
            val body = client.getText(url)
            store.put(url, body, clock.now().toEpochMilliseconds())
            emit(Resource.Data(body))
        } catch (e: KnessetError) {
            emit(Resource.Error(e, cached?.first))
        }
    }

    /** One body: fresh cache, else network, else stale cache. Throws when there is nothing at all. */
    suspend fun get(url: String, ttl: Ttl, force: Boolean = false): Fetched {
        val cached = store.get(url)
        val now = clock.now().toEpochMilliseconds()
        val stale = cached != null && now - cached.second > ttl.duration.inWholeMilliseconds
        if (cached != null && !stale && !force) return Fetched(cached.first, fromCache = true, isStale = false)
        return try {
            val body = client.getText(url)
            store.put(url, body, clock.now().toEpochMilliseconds())
            Fetched(body, fromCache = false, isStale = false)
        } catch (e: KnessetError) {
            if (cached != null && e !is KnessetError.NotFound) Fetched(cached.first, fromCache = true, isStale = true) else throw e
        }
    }

    suspend fun clear() = store.clear()
}
