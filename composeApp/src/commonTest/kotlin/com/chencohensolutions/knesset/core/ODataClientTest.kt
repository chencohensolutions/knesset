package com.chencohensolutions.knesset.core

import com.chencohensolutions.knesset.JSON_HEADERS
import com.chencohensolutions.knesset.core.odata.KnessetError
import com.chencohensolutions.knesset.core.odata.ODataClient
import com.chencohensolutions.knesset.core.odata.ODataQuery
import com.chencohensolutions.knesset.core.odata.F
import com.chencohensolutions.knesset.fixture
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ODataClientTest {
    private fun client(engine: MockEngine) = ODataClient(ODataClient.configure(HttpClient(engine), "1.0"), retryDelaysMs = listOf(1_000, 3_000))

    @Test
    fun returnsJsonBodyAndSendsTheUrlUnchanged() = runTest {
        var seen = ""
        var agent: String? = null
        val engine = MockEngine { req ->
            seen = req.url.toString(); agent = req.headers[HttpHeaders.UserAgent]
            respond(fixture("knesset_dates_current.json"), HttpStatusCode.OK, JSON_HEADERS)
        }
        val url = ODataQuery("KNS_Bill").apply {
            filter(F.contains("Name", "חינוך")); filter(F.ge("StartDate", kotlin.time.Instant.parse("2026-11-18T22:00:00Z")))
        }.toUrl()
        val body = client(engine).getText(url)
        assertTrue(body.contains("\"KnessetNum\":26"))
        // The engine must receive exactly what we built: %2B stays encoded, $ and quotes as built.
        assertEquals(url, seen)
        assertEquals("KnessetApp/1.0", agent)
    }

    @Test
    fun htmlWith200IsBlockedOrUnavailable() = runTest {
        val engine = MockEngine {
            respond("<html><body>האתר אינו זמין ממיקומך</body></html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
        }
        assertFailsWith<KnessetError.BlockedOrUnavailable> { client(engine).getText("https://x/KNS_Bill") }
    }

    @Test
    fun bodyStartingWithAngleBracketIsBlockedEvenIfLabelledJson() = runTest {
        val engine = MockEngine { respond("<!DOCTYPE html>", HttpStatusCode.OK, JSON_HEADERS) }
        assertFailsWith<KnessetError.BlockedOrUnavailable> { client(engine).getText("https://x/KNS_Bill") }
    }

    @Test
    fun rateLimitIsRetriedHonoringRetryAfterThenSucceeds() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls == 1) respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "2"))
            else respond("""{"value":[]}""", HttpStatusCode.OK, JSON_HEADERS)
        }
        val start = testScheduler.currentTime
        client(engine).getText("https://x/KNS_Bill")
        assertEquals(2, calls)
        assertEquals(2_000, testScheduler.currentTime - start)
    }

    @Test
    fun serviceUnavailableRetriesTwiceWithBackoffThenFails() = runTest {
        var calls = 0
        val engine = MockEngine { calls++; respondError(HttpStatusCode.ServiceUnavailable) }
        val start = testScheduler.currentTime
        assertFailsWith<KnessetError.RateLimited> { client(engine).getText("https://x/KNS_Bill") }
        assertEquals(3, calls)
        assertEquals(4_000, testScheduler.currentTime - start)
    }

    @Test
    fun internalServerErrorIsNotRetried() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond("""{"error":"Internal Server Error"}""", HttpStatusCode.InternalServerError, JSON_HEADERS)
        }
        val e = assertFailsWith<KnessetError.Server> { client(engine).getText("https://x/KNS_Bill(1)?\$expand=KNS_BillName") }
        assertEquals(500, e.code)
        assertEquals(1, calls)
    }

    @Test
    fun notFoundMapsToNotFound() = runTest {
        // V_Lobbyists answers 404 with an empty body.
        val engine = MockEngine { respond("", HttpStatusCode.NotFound) }
        assertFailsWith<KnessetError.NotFound> { client(engine).getText("https://x/V_Lobbyists") }
    }

    @Test
    fun ioFailureIsOffline() = runTest {
        val engine = MockEngine { throw IOException("Unable to resolve host") }
        assertFailsWith<KnessetError.Offline> { client(engine).getText("https://x/KNS_Bill") }
    }

    @Test
    fun atMostTwoRequestsInFlight() = runTest {
        // The engine runs on its own threads, so count under a lock.
        val lock = Mutex()
        var inFlight = 0
        var peak = 0
        val engine = MockEngine {
            lock.withLock { inFlight++; peak = maxOf(peak, inFlight) }
            delay(100)
            lock.withLock { inFlight-- }
            respond("""{"value":[]}""", HttpStatusCode.OK, JSON_HEADERS)
        }
        val c = client(engine)
        (1..6).map { async { c.getText("https://x/$it") } }.awaitAll()
        assertEquals(2, peak)
    }
}
