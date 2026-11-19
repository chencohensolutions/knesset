package com.chencohensolutions.knesset.core

import com.chencohensolutions.knesset.core.odata.KnessetError
import com.chencohensolutions.knesset.core.paging.PageResult
import com.chencohensolutions.knesset.core.paging.Pager
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PagerTest {
    private data class Call(val skip: Int, val top: Int, val count: Boolean)

    @Test
    fun pagesUntilAShortPageAndAsksCountOnlyFirst() = runTest {
        val calls = mutableListOf<Call>()
        val total = 120
        val pager = Pager(this, pageSize = 50) { skip, top, withCount, _ ->
            calls += Call(skip, top, withCount)
            PageResult((skip until minOf(skip + top, total)).toList(), if (withCount) total.toLong() else null)
        }
        pager.refresh(); advanceUntilIdle()
        assertEquals(50, pager.state.value.items.size)
        assertEquals(120L, pager.state.value.count)
        assertFalse(pager.state.value.endReached)
        pager.loadMore(); advanceUntilIdle()
        pager.loadMore(); advanceUntilIdle()
        val s = pager.state.value
        assertEquals((0 until 120).toList(), s.items)
        assertTrue(s.endReached)
        assertEquals(120L, s.count)
        assertEquals(listOf(Call(0, 50, true), Call(50, 50, false), Call(100, 50, false)), calls)
        pager.loadMore(); advanceUntilIdle()
        assertEquals(3, calls.size, "no request after the end")
    }

    @Test
    fun exactMultipleNeedsOneEmptyPageToStop() = runTest {
        val pager = Pager(this, pageSize = 50) { skip, top, _, _ -> PageResult((skip until minOf(skip + top, 100)).toList()) }
        pager.refresh(); advanceUntilIdle()
        pager.loadMore(); advanceUntilIdle()
        assertFalse(pager.state.value.endReached)
        pager.loadMore(); advanceUntilIdle()
        assertTrue(pager.state.value.endReached)
        assertEquals(100, pager.state.value.items.size)
    }

    @Test
    fun pageSizeAboveServerCapIsClampedSoTheCapDoesNotEndTheList() = runTest {
        val tops = mutableListOf<Int>()
        // The server returns at most 100 rows whatever $top says.
        val pager = Pager(this, pageSize = 150) { skip, top, _, _ ->
            tops += top
            PageResult((skip until minOf(skip + minOf(top, 100), 250)).toList())
        }
        pager.refresh(); advanceUntilIdle()
        assertEquals(100, pager.effectivePageSize)
        assertFalse(pager.state.value.endReached)
        pager.loadMore(); advanceUntilIdle()
        pager.loadMore(); advanceUntilIdle()
        assertTrue(pager.state.value.endReached)
        assertEquals(250, pager.state.value.items.size)
        assertEquals(listOf(100, 100, 100), tops)
    }

    @Test
    fun emptyFirstPageIsEmptyState() = runTest {
        val pager = Pager<Int>(this) { _, _, _, _ -> PageResult(emptyList(), 0) }
        pager.refresh(); advanceUntilIdle()
        assertTrue(pager.state.value.isEmpty)
    }

    @Test
    fun errorKeepsItemsAndRetryContinues() = runTest {
        var fail = false
        val pager = Pager(this, pageSize = 50) { skip, top, _, _ ->
            if (fail) throw KnessetError.Offline()
            PageResult((skip until skip + top).toList())
        }
        pager.refresh(); advanceUntilIdle()
        fail = true
        pager.loadMore(); advanceUntilIdle()
        assertEquals(50, pager.state.value.items.size)
        assertTrue(pager.state.value.error is KnessetError.Offline)
        fail = false
        pager.retry(); advanceUntilIdle()
        assertNull(pager.state.value.error)
        assertEquals(100, pager.state.value.items.size)
    }

    @Test
    fun distinctByDropsDuplicatesAcrossPages() = runTest {
        // Faction membership rows repeat a person (plan 1.5).
        val rows = listOf(1, 2, 2, 3) + List(46) { 100 + it } + listOf(3, 4)
        val pager = Pager(this, pageSize = 50, distinctBy = { it }) { skip, top, _, _ ->
            PageResult(rows.drop(skip).take(top))
        }
        pager.refresh(); advanceUntilIdle()
        pager.loadMore(); advanceUntilIdle()
        val items = pager.state.value.items
        assertEquals(items.distinct(), items)
        assertTrue(items.containsAll(listOf(1, 2, 3, 4)))
    }

    @Test
    fun offlineFlagFollowsStaleCachePages() = runTest {
        val pager = Pager(this, pageSize = 50) { skip, top, _, _ -> PageResult((skip until skip + 10).toList(), offline = true) }
        pager.refresh(); advanceUntilIdle()
        assertTrue(pager.state.value.offline)
    }
}
