package com.chencohensolutions.knesset

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chencohensolutions.knesset.data.repo.FavoritesRepo
import com.chencohensolutions.knesset.db.KnessetDatabase
import com.chencohensolutions.knesset.ui.Kind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class FavoritesRepoTest {
    private val db = KnessetDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { KnessetDatabase.Schema.create(it) })

    @Test
    fun starredBillIsListedAndGetsAnUpdateDotWhenTheServerChanges() = runTest {
        var serverUpdated = "2026-11-04T09:59:24.443+02:00"
        val api = testApi { req ->
            assertTrue(req.decodedUrl().contains("KNS_Bill?\$filter=Id eq 2229019&\$top=1&\$select=Id,LastUpdatedDate"), req.decodedUrl())
            json("""{"value":[{"Id":2229019,"LastUpdatedDate":"$serverUpdated"}]}""")
        }
        val repo = FavoritesRepo(db, api)
        repo.add(Kind.BILL, 2229019, "חוק נוכחות עורך דין", "ממשלתית", Instant.parse("2026-11-04T07:59:24.443Z"))
        assertTrue(repo.isFavorite(Kind.BILL, 2229019).first())
        val fav = repo.all().first().single()
        assertEquals(Kind.BILL, fav.kind)
        assertFalse(fav.hasUpdate)

        repo.checkForUpdates()
        assertFalse(repo.all().first().single().hasUpdate, "unchanged on the server")

        serverUpdated = "2026-11-19T10:00:00+02:00"
        repo.checkForUpdates()
        assertTrue(repo.all().first().single().hasUpdate, "changed on the server")

        repo.markSeen(Kind.BILL, 2229019, Instant.parse("2026-11-19T08:00:00Z"))
        assertFalse(repo.all().first().single().hasUpdate, "opening it clears the dot")

        repo.remove(Kind.BILL, 2229019)
        assertTrue(repo.all().first().isEmpty())
    }
}
