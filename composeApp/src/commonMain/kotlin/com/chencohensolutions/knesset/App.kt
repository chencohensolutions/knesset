package com.chencohensolutions.knesset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chencohensolutions.knesset.core.AppSettings
import com.chencohensolutions.knesset.core.UiLanguage
import com.chencohensolutions.knesset.core.platform.LocalAppLocale
import com.chencohensolutions.knesset.ui.AppNavHost
import com.chencohensolutions.knesset.ui.theme.KnessetTheme
import org.koin.compose.koinInject

/** Root composable: language, layout direction, theme and navigation. */
@Composable
fun App() {
    val settings = koinInject<AppSettings>()
    val language by settings.language.collectAsStateWithLifecycle()
    val theme by settings.theme.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalAppLocale provides language.tag) {
        key(language) {
            val direction = if (language == UiLanguage.HEBREW) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                KnessetTheme(theme) {
                    AppNavHost()
                }
            }
        }
    }
}
