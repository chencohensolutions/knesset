package com.chencohensolutions.knesset.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chencohensolutions.knesset.core.AppSettings
import com.chencohensolutions.knesset.core.ThemeMode
import com.chencohensolutions.knesset.core.UiLanguage
import com.chencohensolutions.knesset.core.cache.CachedFetcher
import com.chencohensolutions.knesset.core.odata.IsraelTime
import com.chencohensolutions.knesset.core.odata.ODataQuery
import com.chencohensolutions.knesset.core.platform.AppInfo
import com.chencohensolutions.knesset.data.repo.KnessetContext
import com.chencohensolutions.knesset.resources.*
import com.chencohensolutions.knesset.ui.components.DetailTopBar
import com.chencohensolutions.knesset.ui.components.knessetLabel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Instant

@Composable
private fun Heading(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings = koinInject<AppSettings>()
    val ctx = koinInject<KnessetContext>()
    val fetcher = koinInject<CachedFetcher>()
    val language by settings.language.collectAsStateWithLifecycle()
    val theme by settings.theme.collectAsStateWithLifecycle()
    val defaultKnesset by settings.defaultKnesset.collectAsStateWithLifecycle()
    val selection by ctx.selection.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val cleared = stringResource(Res.string.cache_cleared)
    var knessetMenu by remember { mutableStateOf(false) }
    Scaffold(topBar = { DetailTopBar(stringResource(Res.string.settings)) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Heading(stringResource(Res.string.settings_language))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(language == UiLanguage.HEBREW, { settings.setLanguage(UiLanguage.HEBREW) }, { Text(stringResource(Res.string.language_he)) })
                FilterChip(language == UiLanguage.ENGLISH, { settings.setLanguage(UiLanguage.ENGLISH) }, { Text(stringResource(Res.string.language_en)) })
            }
            Heading(stringResource(Res.string.settings_theme))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(theme == ThemeMode.SYSTEM, { settings.setTheme(ThemeMode.SYSTEM) }, { Text(stringResource(Res.string.theme_system)) })
                FilterChip(theme == ThemeMode.LIGHT, { settings.setTheme(ThemeMode.LIGHT) }, { Text(stringResource(Res.string.theme_light)) })
                FilterChip(theme == ThemeMode.DARK, { settings.setTheme(ThemeMode.DARK) }, { Text(stringResource(Res.string.theme_dark)) })
            }
            Heading(stringResource(Res.string.settings_default_knesset))
            Box {
                OutlinedButton(onClick = { knessetMenu = true }) {
                    Text(if (defaultKnesset == null) stringResource(Res.string.default_knesset_current) else knessetLabel(defaultKnesset))
                }
                DropdownMenu(knessetMenu, { knessetMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(Res.string.default_knesset_current)) }, onClick = { settings.setDefaultKnesset(null); knessetMenu = false })
                    selection.terms.forEach { n ->
                        DropdownMenuItem(text = { Text(knessetLabel(n)) }, onClick = { settings.setDefaultKnesset(n); ctx.select(n); knessetMenu = false })
                    }
                }
            }
            HorizontalDivider()
            OutlinedButton(onClick = { scope.launch { fetcher.clear(); snackbar.showSnackbar(cleared) } }) { Text(stringResource(Res.string.clear_cache)) }
        }
    }
}

@Composable
fun AboutScreen() {
    val settings = koinInject<AppSettings>()
    val info = koinInject<AppInfo>()
    val lastSync by settings.lastSync.collectAsStateWithLifecycle()
    Scaffold(topBar = { DetailTopBar(stringResource(Res.string.about)) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(Res.string.about_text), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(Res.string.about_source, com.chencohensolutions.knesset.domain.Bidi.ltr(ODataQuery.BASE_URL)), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.about_privacy), style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            Text(stringResource(Res.string.about_version, info.versionName), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(Res.string.about_last_sync, lastSync?.let { IsraelTime.dateTime(Instant.fromEpochMilliseconds(it)) } ?: "—"),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
