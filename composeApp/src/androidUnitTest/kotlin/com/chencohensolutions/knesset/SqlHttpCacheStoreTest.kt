package com.chencohensolutions.knesset

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chencohensolutions.knesset.core.cache.SqlHttpCacheStore
import com.chencohensolutions.knesset.db.KnessetDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlHttpCacheStoreTest {
    private fun db() = KnessetDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { KnessetDatabase.Schema.create(it) })

    @Test
    fun putGetReplaceClear() = runTest {
        val store = SqlHttpCacheStore(db())
        assertNull(store.get("a"))
        store.put("a", "{\"v\":1}", 10)
        store.put("a", "{\"v\":2}", 20)
        assertEquals("{\"v\":2}" to 20L, store.get("a"))
        store.clear()
        assertNull(store.get("a"))
    }

    @Test
    fun trimDeletesOldestUntilUnderLimit() = runTest {
        val database = db()
        val store = SqlHttpCacheStore(database, maxBytes = 1_000)
        repeat(10) { i -> store.put("u$i", "x".repeat(200), i.toLong()) }
        store.trim()
        val total = database.knessetQueries.cacheTotalSize().executeAsOne()
        assertTrue(total <= 1_000, "total $total")
        assertNull(store.get("u0"), "oldest goes first")
        assertEquals(200, store.get("u9")!!.first.length)
    }
}
