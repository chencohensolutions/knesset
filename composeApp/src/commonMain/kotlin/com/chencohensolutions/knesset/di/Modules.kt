package com.chencohensolutions.knesset.di

import com.chencohensolutions.knesset.core.AppSettings
import org.koin.dsl.module

/** Platform independent bindings. The platform module provides Settings, UrlOpener, Sharer, AppInfo. */
val commonModule = module {
    single { AppSettings(get()) }
}
