package com.chencohensolutions.knesset

import android.app.Application
import android.content.Context
import com.chencohensolutions.knesset.core.platform.AppInfo
import com.chencohensolutions.knesset.core.platform.Sharer
import com.chencohensolutions.knesset.core.platform.UrlOpener
import com.chencohensolutions.knesset.di.commonModule
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
    single<UrlOpener> { AndroidUrlOpener(get()) }
    single<Sharer> { AndroidSharer(get()) }
    single<AppInfo> { AndroidAppInfo() }
}
