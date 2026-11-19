package com.chencohensolutions.knesset

import com.chencohensolutions.knesset.core.cache.CachedFetcher
import com.chencohensolutions.knesset.core.cache.HttpCacheStore
import com.chencohensolutions.knesset.core.odata.ODataClient
import com.chencohensolutions.knesset.data.repo.Api
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.time.Clock
import kotlin.time.Instant

val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json; odata.metadata=minimal; charset=utf-8")

fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(body, status, JSON_HEADERS)

/** The decoded URL the engine received, for readable assertions. */
fun HttpRequestData.decodedUrl(): String = url.toString().let { decodePercent(it) }

fun decodePercent(s: String): String {
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '%' && i + 2 < s.length) {
            bytes += s.substring(i + 1, i + 3).toInt(16).toByte(); i += 3
        } else {
            c.toString().encodeToByteArray().forEach { bytes += it }; i++
        }
    }
    return bytes.toByteArray().decodeToString()
}

class MemoryCacheStore : HttpCacheStore {
    val map = mutableMapOf<String, Pair<String, Long>>()
    override suspend fun get(url: String) = map[url]
    override suspend fun put(url: String, body: String, fetchedAt: Long) { map[url] = body to fetchedAt }
    override suspend fun clear() = map.clear()
}

class FakeClock(var now: Instant = Instant.parse("2026-11-19T10:00:00Z")) : Clock {
    override fun now(): Instant = now
}

/** An [Api] over a MockEngine whose handler routes by decoded URL. */
fun testApi(
    store: HttpCacheStore = MemoryCacheStore(),
    clock: Clock = FakeClock(),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): Api {
    val client = ODataClient(HttpClient(MockEngine(handler)).let { ODataClient.configure(it, "test") }, retryDelaysMs = listOf(1, 1))
    return Api(CachedFetcher(client, store, clock))
}

/** Routes a request to a fixture by the first matching fragment of the decoded URL. */
fun MockRequestHandleScope.route(request: HttpRequestData, vararg routes: Pair<String, String>): HttpResponseData {
    val url = request.decodedUrl()
    val match = routes.firstOrNull { url.contains(it.first) } ?: return respond("", HttpStatusCode.NotFound)
    return json(fixture(match.second))
}
