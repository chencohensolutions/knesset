package com.chencohensolutions.knesset.ui

import com.chencohensolutions.knesset.data.repo.FetchMode
import com.chencohensolutions.knesset.data.repo.Loader
import com.chencohensolutions.knesset.data.repo.PeopleRepo
import com.chencohensolutions.knesset.decodedUrl
import com.chencohensolutions.knesset.json
import com.chencohensolutions.knesset.resources.Res
import com.chencohensolutions.knesset.resources.members_by_result
import com.chencohensolutions.knesset.resources.agenda
import com.chencohensolutions.knesset.resources.results
import com.chencohensolutions.knesset.route
import com.chencohensolutions.knesset.testApi
import com.chencohensolutions.knesset.ui.detail.Section
import com.chencohensolutions.knesset.ui.plenum.loadVote
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlenumTest {
    @Test
    fun sittingAgendaIsPagedPastTheExpandCap() = runTest {
        // 130 agenda items: an expand would stop at 100.
        fun items(skip: Int) = (skip until minOf(skip + 100, 130)).joinToString(",", "{\"value\":[", "]}") {
            """{"Id":${it + 1},"ItemID":${5000 + it},"PlenumSessionID":2246405,"ItemTypeID":2,"Ordinal":$it,"Name":"פריט $it"}"""
        }
        val api = testApi { req ->
            val url = req.decodedUrl()
            when {
                url.contains("KNS_PlmSessionItem") -> json(items(Regex("skip=(\\d+)").find(url)!!.groupValues[1].toInt()))
                url.contains("KNS_PlenumSession?") -> {
                    assertTrue(!url.contains("KNS_PlmSessionItem"), "agenda is not expanded")
                    json("""{"value":[{"Id":2246405,"Name":"ישיבה","KnessetNum":26,"StartDate":"2026-11-17T16:00:00+02:00","KNS_DocumentPlenumSession":[]}]}""")
                }
                else -> json("""{"value":[]}""")
            }
        }
        val none = object : com.chencohensolutions.knesset.data.video.VideoLinkSource {
            override suspend fun forCommitteeSession(sessionId: Int, title: String, at: kotlin.time.Instant?) = emptyList<com.chencohensolutions.knesset.data.video.VideoRef>()
            override suspend fun forPlenumDay(date: kotlinx.datetime.LocalDate) = emptyList<com.chencohensolutions.knesset.data.video.VideoRef>()
        }
        val content = com.chencohensolutions.knesset.ui.plenum.loadSitting(Loader(api, FetchMode.NORMAL), 2246405, none)
        val agenda = content.sections.first { it.title == Res.string.agenda } as Section.Links
        assertEquals(130, agenda.items.size)
        assertEquals("פריט 0", agenda.items.first().title)
        assertTrue(agenda.items.all { it.route is BillRoute })
    }

    @Test
    fun voteTotalsEqualResultRowsByResultAndByFaction() = runTest {
        val api = testApi { req ->
            val url = req.decodedUrl()
            when {
                url.contains("\$skip=100") -> json("""{"value":[]}""")
                url.contains("KNS_PlenumVoteResult") -> route(req, "VoteID eq 46690" to "vote_results_page1.json")
                url.contains("KNS_PlenumVote?") -> route(req, "Id eq 46690" to "vote_46690_detail.json")
                url.contains("KNS_PersonToPosition") -> route(req, "KNS_PersonToPosition" to "mks_k26_page1.json")
                else -> route(req)
            }
        }
        val content = loadVote(Loader(api, FetchMode.NORMAL), 46690, PeopleRepo(api))
        val tally = (content.sections.first { it.title == Res.string.results } as Section.Tally).tally
        assertEquals(10, tally.total)
        assertEquals(10, tally.byResult.sumOf { it.second })
        assertEquals(listOf("בעד" to 2, "נגד" to 7, "נוכח" to 1), tally.byResult)
        val groups = content.sections.first { it.title == Res.string.members_by_result } as Section.Groups
        assertEquals(10, groups.groups.sumOf { it.second.size }, "by result")
        assertEquals(10, groups.alternate.sumOf { it.second.size }, "by faction")
        assertTrue(groups.groups.flatMap { it.second }.all { it.route is MemberRoute })
    }
}
