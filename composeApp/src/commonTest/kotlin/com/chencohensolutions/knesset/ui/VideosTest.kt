package com.chencohensolutions.knesset.ui

import com.chencohensolutions.knesset.FakeClock
import com.chencohensolutions.knesset.MemoryCacheStore
import com.chencohensolutions.knesset.core.odata.ODataClient
import com.chencohensolutions.knesset.core.odata.ODataPage
import com.chencohensolutions.knesset.data.dto.PersonToPositionDto
import com.chencohensolutions.knesset.data.video.KnessetTvParser
import com.chencohensolutions.knesset.data.video.KnessetTvSource
import com.chencohensolutions.knesset.data.video.KnessetVodSource
import com.chencohensolutions.knesset.data.video.PoliteFetcher
import com.chencohensolutions.knesset.decodedUrl
import com.chencohensolutions.knesset.fixture
import com.chencohensolutions.knesset.json
import com.chencohensolutions.knesset.testApi
import com.chencohensolutions.knesset.ui.video.Membership
import com.chencohensolutions.knesset.ui.video.MemberVideoLogic
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class VideosTest {
    private val html = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8")
    private val m3u8 = headersOf(HttpHeaders.ContentType, "application/vnd.apple.mpegurl")

    @Test
    fun parsesTheKnessetChannelListingAndProgramPage() {
        val programs = KnessetTvParser.programs(fixture("knessettv_parliament_page1.html"))
        assertEquals(12, programs.size)
        val day = programs.filter { it.date == LocalDate(2026, 11, 18) }
        assertTrue(day.map { it.id }.containsAll(listOf(95899, 95900)), day.toString())
        assertEquals("חלק א", programs.first { it.id == 95899 }.part)
        // Part ה of the 17th aired after midnight; the title date wins.
        assertEquals(LocalDate(2026, 11, 17), programs.first { it.id == 95880 }.date)
        assertEquals(
            "https://api.bynetcdn.com/Redirector/knesset/manifest/KNS030943-01/HLS/playlist.m3u8",
            KnessetTvParser.streamOf(fixture("knessettv_program_95899.html")),
        )
    }

    @Test
    fun plenumDayCollectsItsPartsInOrderAndCachesPages() = runTest {
        val urls = mutableListOf<String>()
        val engine = MockEngine { req ->
            val url = req.url.toString(); urls += url
            when {
                url == KnessetTvSource.LISTING -> respond(fixture("knessettv_parliament_page1.html"), HttpStatusCode.OK, html)
                url.contains("/programs/") -> respond(fixture("knessettv_program_95899.html").replace("KNS030943-01", "P" + url.filter { it.isDigit() }), HttpStatusCode.OK, html)
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val web = PoliteFetcher(HttpClient(engine), MemoryCacheStore(), FakeClock(), minIntervalMs = 0)
        val source = KnessetTvSource(web)
        val refs = source.forPlenumDay(LocalDate(2026, 11, 18))
        assertTrue(refs.size >= 3, refs.toString())
        assertEquals(refs.sortedBy { it.title }.map { it.title }.size, refs.size)
        assertTrue(refs.all { it.isStream && it.url.endsWith("/HLS/playlist.m3u8") })
        assertEquals(refs.map { it.url }, refs.map { it.url }.sorted(), "parts in program order")
        assertTrue(urls.none { it.contains("videoUrl=") || it.contains("KnessetPlayer") || it.contains("watch.knesset.tv") }, "only robots-allowed pages")
        val before = urls.size
        source.forPlenumDay(LocalDate(2026, 11, 18))
        assertEquals(before, urls.size, "second lookup is served from the cache")
    }

    @Test
    fun brokenMarkupMeansNoVideoNotAnError() = runTest {
        val engine = MockEngine { respond("<html><body>new design</body></html>", HttpStatusCode.OK, html) }
        val source = KnessetTvSource(PoliteFetcher(HttpClient(engine), MemoryCacheStore(), FakeClock(), minIntervalMs = 0))
        assertTrue(source.forPlenumDay(LocalDate(2026, 11, 18)).isEmpty())
    }

    @Test
    fun committeeStreamWhenTheManifestExistsElseBroadcastPageAndNegativeIsCached() = runTest {
        var manifestCalls = 0
        val webEngine = MockEngine { req ->
            manifestCalls++
            if (req.url.toString().contains("CmtSession_2220560")) respond("#EXTM3U\n#EXT-X-VERSION:3\n", HttpStatusCode.OK, m3u8)
            else respond("", HttpStatusCode.NotFound)
        }
        val api = testApi { req ->
            assertTrue(req.decodedUrl().contains("KNS_BroadcastCommitteSession?\$filter=Id eq 386993"), req.decodedUrl())
            json("""{"value":[{"Id":386993,"BroadcastId":318,"BroadcastUrl":"https://main.knesset.gov.il/Activity/committees/Economics/Pages/CommitteeTVarchive.aspx?TopicID=318"}]}""")
        }
        val vod = KnessetVodSource(PoliteFetcher(HttpClient(webEngine), MemoryCacheStore(), FakeClock(), minIntervalMs = 0), api)
        val past = Instant.parse("2025-01-01T08:00:00Z")

        val found = vod.forCommitteeSession(2220560, "ועדה", past).single()
        assertTrue(found.isStream)
        assertEquals(KnessetVodSource.streamUrl(2220560), found.url)
        assertEquals("https://video.knesset.gov.il/KnsVod/_definst_/mp4:archive/CMT/CmtSession_2220560.mp4/playlist.m3u8", found.url)

        val old = vod.forCommitteeSession(386993, "ועדה", past).single()
        assertFalse(old.isStream)
        assertTrue(old.url.startsWith("https://main.knesset.gov.il/Activity/committees/Economics/"))
        val calls = manifestCalls
        vod.forCommitteeSession(386993, "ועדה", past)
        assertEquals(calls, manifestCalls, "the 404 is cached")

        assertTrue(vod.forCommitteeSession(2247410, "ועדה", Instant.parse("2099-01-01T00:00:00Z")).isEmpty(), "future sessions have no video")
    }

    @Test
    fun memberSessionFilterRespectsMembershipDatesAndThePast() {
        val positions = ODataClient.json.decodeFromString(ODataPage.serializer(PersonToPositionDto.serializer()), fixture("person_30300_positions.json")).value
        val all = MemberVideoLogic.memberships(positions, null)
        assertTrue(all.all { it.committeeId > 0 })
        assertTrue(MemberVideoLogic.memberships(positions, 26).all { m -> positions.any { it.committeeID == m.committeeId && it.knessetNum == 26 } })

        val now = Instant.parse("2026-11-19T10:00:00Z")
        val ms = listOf(
            Membership(4186, "כספים", Instant.parse("2022-11-14T22:00:00Z"), Instant.parse("2023-06-30T21:00:00Z")),
            Membership(4191, "חוקה", Instant.parse("2026-11-09T22:00:00Z"), null),
        )
        assertEquals(
            "(CommitteeID eq 4186 or CommitteeID eq 4191) and StartDate ge 2022-11-15T00:00:00+02:00 and StartDate lt 2026-11-19T12:00:00+02:00",
            MemberVideoLogic.sessionFilter(ms, now),
        )
        assertEquals("CommitteeID eq 4191 and StartDate ge 2026-11-10T00:00:00+02:00 and StartDate lt 2026-11-19T12:00:00+02:00", MemberVideoLogic.sessionFilter(ms, now, 4191))
        // Membership dates are applied on the device.
        assertTrue(MemberVideoLogic.inMembership(ms, 4186, Instant.parse("2023-01-10T08:00:00Z")))
        assertFalse(MemberVideoLogic.inMembership(ms, 4186, Instant.parse("2024-01-10T08:00:00Z")), "after leaving the committee")
        assertFalse(MemberVideoLogic.inMembership(ms, 4191, Instant.parse("2026-11-01T08:00:00Z")), "before joining")
        assertFalse(MemberVideoLogic.inMembership(ms, 9999, Instant.parse("2026-11-12T08:00:00Z")))
        val many = (1..30).map { Membership(it, "c$it", Instant.parse("2020-01-01T00:00:00Z"), null) }
        assertEquals(20, Regex("CommitteeID eq").findAll(MemberVideoLogic.sessionFilter(many, now)!!).count(), "or chain capped at 20")
        assertEquals(null, MemberVideoLogic.sessionFilter(emptyList(), now))
    }
}
