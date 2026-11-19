package com.chencohensolutions.knesset.ui

import com.chencohensolutions.knesset.data.video.DownloadFailure
import com.chencohensolutions.knesset.data.video.DownloadNames
import com.chencohensolutions.knesset.data.video.DownloadQueue
import com.chencohensolutions.knesset.data.video.DownloadQueue.Event
import com.chencohensolutions.knesset.data.video.DownloadState
import com.chencohensolutions.knesset.data.video.HlsEstimate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadsTest {
    private val a = "https://x/a/playlist.m3u8"
    private val b = "https://x/b/playlist.m3u8"

    @Test
    fun oneAtATimeInOrderWithoutDuplicates() {
        var q = DownloadQueue.reduce(emptyList(), Event.Enqueue(a, "חלק א", 100, 1))
        q = DownloadQueue.reduce(q, Event.Enqueue(b, "חלק ב", 200, 2))
        q = DownloadQueue.reduce(q, Event.Enqueue(a, "חלק א", 100, 3))
        assertEquals(2, q.size, "same video twice is one download")
        assertEquals(a, DownloadQueue.next(q)!!.url)
        q = DownloadQueue.reduce(q, Event.Started(a))
        assertNull(DownloadQueue.next(q), "nothing else starts while one runs")
        q = DownloadQueue.reduce(q, Event.Progress(a, 140))
        assertEquals(100, q.first { it.url == a }.progress, "progress is clamped")
        q = DownloadQueue.reduce(q, Event.Finished(a, "content://media/1", "Knesset - חלק א.mp4", 99))
        assertEquals(DownloadState.DONE, q.first { it.url == a }.state)
        assertEquals(b, DownloadQueue.next(q)!!.url)
        q = DownloadQueue.reduce(q, Event.Enqueue(a, "חלק א", 100, 4))
        assertEquals(DownloadState.DONE, q.first { it.url == a }.state, "a finished video is not downloaded again")
    }

    @Test
    fun failureCancelRetryAndRemove() {
        var q = DownloadQueue.reduce(emptyList(), Event.Enqueue(a, "t", null, 1))
        q = DownloadQueue.reduce(q, Event.Started(a))
        q = DownloadQueue.reduce(q, Event.Failed(a, DownloadFailure.NO_SPACE))
        assertEquals(DownloadState.FAILED, q.single().state)
        assertEquals(DownloadFailure.NO_SPACE, q.single().failure)
        q = DownloadQueue.reduce(q, Event.Retry(a))
        assertEquals(DownloadState.QUEUED, q.single().state)
        assertNull(q.single().failure)
        q = DownloadQueue.reduce(q, Event.Started(a))
        q = DownloadQueue.reduce(q, Event.Cancel(a))
        assertEquals(DownloadState.CANCELED, q.single().state)
        q = DownloadQueue.reduce(q, Event.Failed(a, DownloadFailure.NETWORK))
        assertEquals(DownloadState.FAILED, q.single().state)
        q = DownloadQueue.reduce(q, Event.Remove(a))
        assertTrue(q.isEmpty())
    }

    @Test
    fun fileNamesAreSafeAndKeepHebrew() {
        assertEquals("Knesset - דיון במליאת הכנסת - 18.11.26 - חלק א.mp4", DownloadNames.fileName("דיון במליאת הכנסת - 18.11.26 - חלק א"))
        assertEquals("Knesset - ועדת החוץ והביטחון 23 11 2026.mp4", DownloadNames.fileName("ועדת החוץ והביטחון  23/11/2026"))
        assertEquals("Knesset - חוק התשפ ד-2024 a b.mp4", DownloadNames.fileName("חוק התשפ\"ד-2024 a:b"))
        assertEquals("Knesset - video.mp4", DownloadNames.fileName("  ///  "))
        assertTrue(DownloadNames.fileName("א".repeat(300)).length <= 100)
    }

    @Test
    fun hlsSizeEstimateFromLivePlaylists() {
        // Shapes of the real playlists checked on 2026-11-19.
        val master = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=1871597,CODECS="avc1.42c01f,mp4a.40.2",RESOLUTION=640x360
            chunklist_w885879456.m3u8
        """.trimIndent()
        val v = HlsEstimate.variants(master).single()
        assertEquals(1871597, v.bandwidth)
        val media = "#EXTM3U\n#EXT-X-TARGETDURATION:12\n#EXTINF:10.2,\nmedia_0.ts\n#EXTINF:10.0,\nmedia_1.ts\n#EXTINF:9.8,\nmedia_2.ts\n#EXT-X-ENDLIST"
        assertEquals(30.0, HlsEstimate.durationSeconds(media), 1e-9)
        assertEquals(7_018_488, HlsEstimate.bytes(v.bandwidth, 30.0))
        val base = "https://video.knesset.gov.il/KnsVod/_definst_/mp4:archive/CMT/CmtSession_2220560.mp4/playlist.m3u8"
        assertEquals("https://video.knesset.gov.il/KnsVod/_definst_/mp4:archive/CMT/CmtSession_2220560.mp4/chunklist_w885879456.m3u8", HlsEstimate.resolve(base, v.uri))
        assertEquals("https://cdn/x.m3u8", HlsEstimate.resolve(base, "https://cdn/x.m3u8"))
        assertEquals("https://video.knesset.gov.il/a.m3u8", HlsEstimate.resolve(base, "/a.m3u8"))
    }
}
