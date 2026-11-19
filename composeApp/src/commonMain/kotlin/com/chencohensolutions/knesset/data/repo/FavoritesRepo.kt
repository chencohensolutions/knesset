package com.chencohensolutions.knesset.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.core.odata.KnessetError
import com.chencohensolutions.knesset.core.odata.ODataInstant
import com.chencohensolutions.knesset.db.KnessetDatabase
import com.chencohensolutions.knesset.ui.Kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

data class Favorite(
    val kind: Kind,
    val id: Int,
    val title: String,
    val subtitle: String?,
    val addedAt: Long,
    val seenUpdatedAt: Long?,
    val latestUpdatedAt: Long?,
) {
    /** The item changed on the server since the user last opened it (plan 2.8). */
    val hasUpdate get() = latestUpdatedAt != null && seenUpdatedAt != null && latestUpdatedAt > seenUpdatedAt
}

@Serializable
private class IdAndUpdated(@SerialName("Id") val id: Int, @SerialName("LastUpdatedDate") val lastUpdatedDate: ODataInstant? = null)

/** Starred items, stored on the device only. */
class FavoritesRepo(private val db: KnessetDatabase, private val api: Api, private val clock: Clock = Clock.System) {
    private val q get() = db.knessetQueries

    fun all(): Flow<List<Favorite>> = q.favoriteAll().asFlow().mapToList(Dispatchers.IO).map { rows ->
        rows.mapNotNull { r ->
            val kind = Kind.entries.firstOrNull { it.name == r.kind } ?: return@mapNotNull null
            Favorite(kind, r.id.toInt(), r.title, r.subtitle, r.added_at, r.seen_updated_at, r.latest_updated_at)
        }
    }

    fun isFavorite(kind: Kind, id: Int): Flow<Boolean> =
        q.favoriteGet(kind.name, id.toLong()).asFlow().mapToOneOrNull(Dispatchers.IO).map { it != null }

    suspend fun add(kind: Kind, id: Int, title: String, subtitle: String?, lastUpdated: Instant?) = withContext(Dispatchers.IO) {
        val updated = lastUpdated?.toEpochMilliseconds()
        q.favoritePut(kind.name, id.toLong(), title, subtitle, clock.now().toEpochMilliseconds(), updated, updated)
    }

    suspend fun remove(kind: Kind, id: Int) = withContext(Dispatchers.IO) { q.favoriteDelete(kind.name, id.toLong()) }

    /** Called when a starred item is opened: what the user saw becomes the new baseline. */
    suspend fun markSeen(kind: Kind, id: Int, lastUpdated: Instant?) = withContext(Dispatchers.IO) {
        if (q.favoriteGet(kind.name, id.toLong()).executeAsOneOrNull() == null) return@withContext
        val t = lastUpdated?.toEpochMilliseconds() ?: return@withContext
        q.favoriteSetSeen(t, t, kind.name, id.toLong())
    }

    /** Fetches LastUpdatedDate for every favorite, 20 ids per request, and records it. */
    suspend fun checkForUpdates() {
        val favorites = withContext(Dispatchers.IO) { q.favoriteAll().executeAsList() }
        val loader = Loader(api, FetchMode.FORCE)
        favorites.groupBy { it.kind }.forEach { (kindName, rows) ->
            val kind = Kind.entries.firstOrNull { it.name == kindName } ?: return@forEach
            if (kind == Kind.LOBBYIST) return@forEach
            val fetched = try {
                loader.byIds(kind.entitySet, rows.map { it.id.toInt() }, IdAndUpdated.serializer(), listOf("Id", "LastUpdatedDate"), Ttl.LIVE)
            } catch (e: KnessetError) {
                return@forEach
            }
            withContext(Dispatchers.IO) {
                fetched.forEach { f ->
                    f.lastUpdatedDate?.let { q.favoriteSetLatest(it.toEpochMilliseconds(), kind.name, f.id.toLong()) }
                }
            }
        }
    }
}

/** Recent searches, newest first, at most 10. */
class RecentSearchRepo(private val db: KnessetDatabase, private val clock: Clock = Clock.System) {
    private val q get() = db.knessetQueries
    fun all(): Flow<List<String>> = q.recentAll().asFlow().mapToList(Dispatchers.IO)
    suspend fun add(text: String) = withContext(Dispatchers.IO) {
        val t = text.trim()
        if (t.length < 2) return@withContext
        q.recentPut(t, clock.now().toEpochMilliseconds()); q.recentTrim()
    }
    suspend fun clear() = withContext(Dispatchers.IO) { q.recentClear() }
}
