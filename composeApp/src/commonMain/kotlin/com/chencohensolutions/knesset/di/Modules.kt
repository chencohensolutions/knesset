package com.chencohensolutions.knesset.di

import com.chencohensolutions.knesset.core.AppSettings
import com.chencohensolutions.knesset.core.cache.CachedFetcher
import com.chencohensolutions.knesset.core.cache.HttpCacheStore
import com.chencohensolutions.knesset.core.cache.SqlHttpCacheStore
import com.chencohensolutions.knesset.core.odata.ODataClient
import com.chencohensolutions.knesset.core.platform.AppInfo
import com.chencohensolutions.knesset.data.repo.Api
import com.chencohensolutions.knesset.data.repo.KnessetContext
import com.chencohensolutions.knesset.data.repo.LookupRepo
import com.chencohensolutions.knesset.data.repo.PeopleRepo
import com.chencohensolutions.knesset.data.repo.FavoritesRepo
import com.chencohensolutions.knesset.data.repo.RecentSearchRepo
import com.chencohensolutions.knesset.db.KnessetDatabase
import io.ktor.client.HttpClient
import org.koin.dsl.module

/**
 * Platform independent bindings. The platform module provides Settings, HttpClient (engine),
 * SqlDriver, UrlOpener, Sharer and AppInfo.
 */
val commonModule = module {
    single { AppSettings(get()) }
    single { KnessetDatabase(get()) }
    single { ODataClient(ODataClient.configure(get<HttpClient>(), get<AppInfo>().versionName)) }
    single<HttpCacheStore> { SqlHttpCacheStore(get()) }
    single { CachedFetcher(get(), get()) }
    single { Api(get()) }
    single { LookupRepo(get()) }
    single { KnessetContext(get(), get()) }
    single { PeopleRepo(get()) }
    single { FavoritesRepo(get(), get()) }
    single { RecentSearchRepo(get()) }
}
