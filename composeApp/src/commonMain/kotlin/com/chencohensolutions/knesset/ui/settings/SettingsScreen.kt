package com.chencohensolutions.knesset.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chencohensolutions.knesset.core.AppSettings
import com.chencohensolutions.knesset.core.ThemeMode
import com.chencohensolutions.knesset.core.UiLanguage
import com.chencohensolutions.knesset.resources.Res
import com.chencohensolutions.knesset.resources.back
import com.chencohensolutions.knesset.resources.language_en
import com.chencohensolutions.knesset.resources.language_he
import com.chencohensolutions.knesset.resources.settings
import com.chencohensolutions.knesset.resources.settings_language
import com.chencohensolutions.knesset.resources.settings_theme
import com.chencohensolutions.knesset.resources.theme_dark
import com.chencohensolutions.knesset.resources.theme_light
import com.chencohensolutions.knesset.resources.theme_system
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings = koinInject<AppSettings>()
    val language by settings.language.collectAsStateWithLifecycle()
    val theme by settings.theme.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.settings_language), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(language == UiLanguage.HEBREW, { settings.setLanguage(UiLanguage.HEBREW) }, { Text(stringResource(Res.string.language_he)) })
                FilterChip(language == UiLanguage.ENGLISH, { settings.setLanguage(UiLanguage.ENGLISH) }, { Text(stringResource(Res.string.language_en)) })
            }
            Text(stringResource(Res.string.settings_theme), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(theme == ThemeMode.SYSTEM, { settings.setTheme(ThemeMode.SYSTEM) }, { Text(stringResource(Res.string.theme_system)) })
                FilterChip(theme == ThemeMode.LIGHT, { settings.setTheme(ThemeMode.LIGHT) }, { Text(stringResource(Res.string.theme_light)) })
                FilterChip(theme == ThemeMode.DARK, { settings.setTheme(ThemeMode.DARK) }, { Text(stringResource(Res.string.theme_dark)) })
            }
        }
    }
}
