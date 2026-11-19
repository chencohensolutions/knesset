package com.chencohensolutions.knesset.ui

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chencohensolutions.knesset.data.repo.RecentSearchRepo
import com.chencohensolutions.knesset.db.KnessetDatabase

actual class FakeRecent actual constructor() {
    private val db = KnessetDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { KnessetDatabase.Schema.create(it) })
    actual val repo = RecentSearchRepo(db, io = kotlinx.coroutines.Dispatchers.Unconfined)
    actual val saved: List<String> get() = db.knessetQueries.recentAll().executeAsList()
}
