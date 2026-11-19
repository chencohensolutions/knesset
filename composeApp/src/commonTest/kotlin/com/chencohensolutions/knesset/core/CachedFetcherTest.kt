package com.chencohensolutions.knesset.core

import app.cash.turbine.test
import com.chencohensolutions.knesset.FakeClock
import com.chencohensolutions.knesset.JSON_HEADERS
import com.chencohensolutions.knesset.MemoryCacheStore
import com.chencohensolutions.knesset.core.cache.CachedFetcher
import com.chencohensolutions.knesset.core.cache.Resource
import com.chencohensolutions.knesset.core.cache.Ttl
import com.chencohensolutions.knesset.core.odata.KnessetError
import com.chencohensolutions.knesset.core.odata.ODataClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class CachedFetcherTest {
    private val url = "https://knesset.gov.il/OdataV4/ParliamentInfo/KNS_Status"
    private var calls = 0
    private var online = true
    private val clock = FakeClock()
    private val store = MemoryCacheStore()
    private val fetcher = CachedFetcher(
        ODataClient(ODataClient.configure(HttpClient(MockEngine {
            calls++
            if (!online) throw IOException("offline")
            respond("""{"value":[$calls]}""", HttpStatusCode.OK, JSON_HEADERS)
        }), "t"), retryDelaysMs = emptyList()),
        store, clock,
    )

    @Test
    fun missThenFreshHitThenStaleRevalidates() = runTest {
        fetcher.stream(url, Ttl.LIST).test {
            assertEquals(Resource.Loading, awaitItem())
            assertEquals(Resource.Data("""{"value":[1]}"""), awaitItem())
            awaitComplete()
        }
        clock.now += 14.minutes
        fetcher.stream(url, Ttl.LIST).test {
            assertEquals(Resource.Data("""{"value":[1]}""", isStale = false, fromCache = true), awaitItem())
            awaitComplete()
        }
        assertEquals(1, calls)
        clock.now += 2.minutes
        fetcher.stream(url, Ttl.LIST).test {
            assertEquals(Resource.Data("""{"value":[1]}""", isStale = true, fromCache = true), awaitItem())
            assertEquals(Resource.Data("""{"value":[2]}"""), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun forceBypassesTtl() = runTest {
        fetcher.get(url, Ttl.LOOKUP)
        val again = fetcher.get(url, Ttl.LOOKUP, force = true)
        assertEquals(2, calls)
        assertTrue(!again.fromCache)
    }

    @Test
    fun networkFailureKeepsCachedBody() = runTest {
        fetcher.get(url, Ttl.LIVE)
        clock.now += 10.minutes
        online = false
        fetcher.stream(url, Ttl.LIVE).test {
            assertEquals(Resource.Data("""{"value":[1]}""", isStale = true, fromCache = true), awaitItem())
            val error = awaitItem() as Resource.Error
            assertTrue(error.error is KnessetError.Offline)
            assertEquals("""{"value":[1]}""", error.cached)
            awaitComplete()
        }
        val fetched = fetcher.get(url, Ttl.LIVE)
        assertTrue(fetched.fromCache && fetched.isStale)
    }

    @Test
    fun nothingCachedAndOfflineThrows() = runTest {
        online = false
        assertFailsWith<KnessetError.Offline> { fetcher.get(url, Ttl.LIST) }
    }

    @Test
    fun ttlTable() {
        assertEquals(7.days, Ttl.LOOKUP.duration)
        assertEquals(5.minutes, Ttl.LIVE.duration)
        assertEquals(Ttl.PAST_KNESSET, Ttl.forKnesset(Ttl.LIST, 25, 26))
        assertEquals(Ttl.LIST, Ttl.forKnesset(Ttl.LIST, 26, 26))
        assertEquals(Ttl.LIST, Ttl.forKnesset(Ttl.LIST, null, 26))
        assertEquals(Ttl.LOOKUP, Ttl.forKnesset(Ttl.LOOKUP, 20, 26))
    }
}
