package com.chencohensolutions.knesset.data.video

import com.chencohensolutions.knesset.core.cache.HttpCacheStore
import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.core.odata.F
import com.chencohensolutions.knesset.core.odata.IsraelTime
import com.chencohensolutions.knesset.core.odata.KnessetError
import com.chencohensolutions.knesset.core.odata.ODataQuery
import com.chencohensolutions.knesset.data.dto.BroadcastCommitteSessionDto
import com.chencohensolutions.knesset.data.repo.Api
import com.chencohensolutions.knesset.data.repo.FetchMode
import com.chencohensolutions.knesset.data.repo.Loader
import com.chencohensolutions.knesset.domain.Documents
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** A playable stream, or a web page that hosts the video when no stream is known. */
data class VideoRef(
    val url: String,
    val title: String,
    val at: Instant? = null,
    val isStream: Boolean = true,
    val source: String,
)

/**
 * Where videos come from. Screens only see this interface, so another source (for example a future
 * per-speaker index) can be added without changing them (plan section 8).
 */
interface VideoLinkSource {
    /** Videos of one committee session; empty when there is none. */
    suspend fun forCommitteeSession(sessionId: Int, title: String, at: Instant?): List<VideoRef>

    /** Videos of a plenum sitting day, in parts; empty when none is found. */
    suspend fun forPlenumDay(date: LocalDate): List<VideoRef>
}

/** All sources in order; each failing source simply contributes nothing. */
class CompositeVideoLinkSource(private val sources: List<VideoLinkSource>) : VideoLinkSource {
    override suspend fun forCommitteeSession(sessionId: Int, title: String, at: Instant?) =
        sources.flatMap { runCatching { it.forCommitteeSession(sessionId, title, at) }.getOrDefault(emptyList()) }

    override suspend fun forPlenumDay(date: LocalDate) =
        sources.flatMap { runCatching { it.forPlenumDay(date) }.getOrDefault(emptyList()) }
}

/** Plain GETs of non-OData pages and manifests: one at a time, at least [minIntervalMs] apart, cached. */
class PoliteFetcher(
    private val http: HttpClient,
    private val store: HttpCacheStore,
    private val clock: Clock = Clock.System,
    private val minIntervalMs: Long = 1_000,
) {
    private val mutex = Mutex()
    private var last = 0L

    /** The body, or null on 404. Cached for [ttl]; negative answers are cached too. */
    suspend fun get(url: String, ttl: kotlin.time.Duration): String? {
        val key = "web:$url"
        store.get(key)?.let { (body, at) ->
            if (clock.now().toEpochMilliseconds() - at <= ttl.inWholeMilliseconds) return body.takeIf { it != MISSING }
        }
        val body = mutex.withLock {
            val wait = last + minIntervalMs - clock.now().toEpochMilliseconds()
            if (wait > 0) delay(wait)
            try {
                val response = http.get(url) { header(HttpHeaders.Accept, "*/*") }
                last = clock.now().toEpochMilliseconds()
                when (response.status.value) {
                    in 200..299 -> response.bodyAsText()
                    404, 410 -> MISSING
                    else -> throw KnessetError.Server(response.status.value)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: KnessetError) {
                throw e
            } catch (e: Exception) {
                throw KnessetError.Offline(e)
            }
        }
        store.put(key, body, clock.now().toEpochMilliseconds())
        return body.takeIf { it != MISSING }
    }

    private companion object { const val MISSING = "\u0000missing" }
}

/**
 * Committee sessions on the Knesset's own video server, named by `KNS_CommitteeSession.Id` (verified
 * 2026-11-19: HLS 640x360 for sessions from about 2020 on; older or missing ones answer 404). When there is
 * no stream, the session's page from `KNS_BroadcastCommitteSession` is offered instead.
 */
class KnessetVodSource(private val web: PoliteFetcher, private val api: Api) : VideoLinkSource {
    override suspend fun forCommitteeSession(sessionId: Int, title: String, at: Instant?): List<VideoRef> {
        if (at != null && at > Clock.System.now()) return emptyList()
        val stream = streamUrl(sessionId)
        val manifest = web.get(stream, 30.days)
        if (manifest != null && manifest.startsWith("#EXTM3U")) return listOf(VideoRef(stream, title, at, isStream = true, source = SOURCE))
        val page = broadcastPages(listOf(sessionId))[sessionId]
        return listOfNotNull(page?.let { VideoRef(it, title, at, isStream = false, source = SOURCE) })
    }

    override suspend fun forPlenumDay(date: LocalDate): List<VideoRef> = emptyList()

    /** Broadcast page URLs for sessions, 20 ids per request, cached for 30 days (they do not change). */
    suspend fun broadcastPages(sessionIds: List<Int>): Map<Int, String> {
        val loader = Loader(api, FetchMode.NORMAL)
        return sessionIds.distinct().chunked(20).flatMap { chunk ->
            loader.list(ODataQuery("KNS_BroadcastCommitteSession").apply { filter(F.anyOf("Id", chunk)); top = chunk.size }, BroadcastCommitteSessionDto.serializer(), Ttl.PAST_KNESSET)
        }.mapNotNull { b -> Documents.normalizeUrl(b.broadcastUrl)?.let { b.id to it } }.toMap()
    }

    /** Which of these sessions have a row in the broadcast table (a broadcast was scheduled). */
    suspend fun withBroadcastRow(sessionIds: List<Int>): Set<Int> = coroutineScope {
        val loader = Loader(api, FetchMode.NORMAL)
        // Chunks run concurrently; the client still keeps at most two requests in flight.
        sessionIds.distinct().chunked(20).map { chunk ->
            async {
                loader.list(ODataQuery("KNS_BroadcastCommitteSession").apply { filter(F.anyOf("Id", chunk)); top = chunk.size; select = listOf("Id") }, BroadcastCommitteSessionDto.serializer(), Ttl.PAST_KNESSET)
            }
        }.awaitAll().flatten().map { it.id }.toSet()
    }

    companion object {
        const val SOURCE = "video.knesset.gov.il"
        fun streamUrl(sessionId: Int) = "https://video.knesset.gov.il/KnsVod/_definst_/mp4:archive/CMT/CmtSession_$sessionId.mp4/playlist.m3u8"
    }
}

/** One program on the Knesset Channel's plenum listing. */
data class TvProgram(val id: Int, val title: String, val date: LocalDate?, val part: String?)

/** Pure parsing of knesset.tv pages, kept apart so markup changes are easy to fix and test. */
object KnessetTvParser {
    private val card = Regex("""href="/parliament/programs/(\d+)/?"[\s\S]*?<h2>([^<]+)</h2>""")
    private val dateInTitle = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{2,4})""")
    private val hls = Regex("""https://api\.bynetcdn\.com/Redirector/knesset/manifest/[A-Za-z0-9_\-]+/HLS/playlist\.m3u8""")

    fun programs(html: String): List<TvProgram> = card.findAll(html).map { m ->
        val title = decode(m.groupValues[2].trim())
        TvProgram(m.groupValues[1].toInt(), title, dateOf(title), title.substringAfterLast(" - ", "").takeIf { it.startsWith("חלק") })
    }.distinctBy { it.id }.toList()

    /** The sitting date from a title such as "דיון במליאת הכנסת - 17.11.26 - חלק ה". */
    fun dateOf(title: String): LocalDate? = dateInTitle.find(title)?.let { m ->
        val y = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
        runCatching { LocalDate(y, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
    }

    fun streamOf(programHtml: String): String? = hls.find(programHtml)?.value

    private fun decode(s: String) = s.replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
}

/**
 * Plenum videos from the Knesset Channel, matched to a sitting by date (there is no shared id). Only the
 * pages robots.txt allows are fetched (`/parliament/` and `/parliament/programs/{id}/`), one at a time,
 * cached, and only when the user opens a sitting. Any failure means "no video found".
 */
class KnessetTvSource(private val web: PoliteFetcher, private val maxPages: Int = 8) : VideoLinkSource {
    override suspend fun forCommitteeSession(sessionId: Int, title: String, at: Instant?): List<VideoRef> = emptyList()

    override suspend fun forPlenumDay(date: LocalDate): List<VideoRef> {
        val matches = mutableListOf<TvProgram>()
        for (page in 1..maxPages) {
            val url = if (page == 1) LISTING else "$LISTING?page=$page"
            val html = web.get(url, if (page == 1) 1.hours else 12.hours) ?: break
            val programs = KnessetTvParser.programs(html)
            if (programs.isEmpty()) break
            matches += programs.filter { it.date == date }
            // The listing is newest first: stop once it is past the wanted day.
            if (programs.mapNotNull { it.date }.minOrNull()?.let { it < date } == true) break
        }
        return matches.sortedBy { it.id }.mapNotNull { p ->
            val stream = web.get("$PROGRAM${p.id}/", 30.days)?.let(KnessetTvParser::streamOf) ?: return@mapNotNull null
            VideoRef(stream, p.title, IsraelTime.startOfDay(date), isStream = true, source = SOURCE)
        }
    }

    companion object {
        const val SOURCE = "knesset.tv"
        const val LISTING = "https://www.knesset.tv/parliament/"
        const val PROGRAM = "https://www.knesset.tv/parliament/programs/"
    }
}
