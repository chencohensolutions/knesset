package com.chencohensolutions.knesset

import android.app.Application
import android.content.Context
import com.chencohensolutions.knesset.core.platform.AppInfo
import com.chencohensolutions.knesset.core.platform.Sharer
import com.chencohensolutions.knesset.core.platform.UrlOpener
import com.chencohensolutions.knesset.di.commonModule
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.chencohensolutions.knesset.db.KnessetDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class KnessetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@KnessetApplication)
            modules(androidModule, commonModule)
        }
    }
}

val androidModule = module {
    single<Settings> { SharedPreferencesSettings(get<Context>().getSharedPreferences("settings", Context.MODE_PRIVATE)) }
    single { HttpClient(OkHttp) }
    single<SqlDriver> { AndroidSqliteDriver(KnessetDatabase.Schema, get(), "knesset.db") }
    single<UrlOpener> { AndroidUrlOpener(get()) }
    single<Sharer> { AndroidSharer(get()) }
    single<AppInfo> { AndroidAppInfo() }
    single { com.chencohensolutions.knesset.video.AndroidVideoDownloads(get()) }
    single<com.chencohensolutions.knesset.data.video.VideoDownloads> { get<com.chencohensolutions.knesset.video.AndroidVideoDownloads>() }
}
