package com.chencohensolutions.knesset.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chencohensolutions.knesset.resources.choose_knesset
import com.chencohensolutions.knesset.ui.theme.AppTheme
import com.chencohensolutions.knesset.ui.theme.Radius
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.RowScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chencohensolutions.knesset.data.repo.KnessetContext
import com.chencohensolutions.knesset.resources.Res
import com.chencohensolutions.knesset.resources.about
import com.chencohensolutions.knesset.resources.back
import com.chencohensolutions.knesset.resources.data_explorer
import com.chencohensolutions.knesset.resources.downloads
import com.chencohensolutions.knesset.resources.favorites
import com.chencohensolutions.knesset.resources.knesset_all
import com.chencohensolutions.knesset.resources.knesset_n
import com.chencohensolutions.knesset.resources.more
import com.chencohensolutions.knesset.resources.search
import com.chencohensolutions.knesset.resources.settings
import com.chencohensolutions.knesset.ui.AboutRoute
import com.chencohensolutions.knesset.ui.ExplorerRoute
import com.chencohensolutions.knesset.ui.FavoritesRoute
import com.chencohensolutions.knesset.ui.LocalNav
import com.chencohensolutions.knesset.ui.SearchRoute
import com.chencohensolutions.knesset.ui.SettingsRoute
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun knessetLabel(number: Int?): String =
    if (number == null) stringResource(Res.string.knesset_all) else stringResource(Res.string.knesset_n, number)

/** The global Knesset selector (plan 2.2): a white pill on the header that opens the picker sheet (docs 3.16). */
@Composable
fun KnessetSelectorChip(onGradient: Boolean = true) {
    val ctx = koinInject<KnessetContext>()
    val selection by ctx.selection.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.clip(Radius.pill).background(if (onGradient) Color.White.copy(alpha = 0.22f) else AppTheme.container)
            .clickable { open = true }.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(knessetLabel(selection.selected), maxLines = 1, style = MaterialTheme.typography.labelLarge, color = if (onGradient) Color.White else AppTheme.colors.textPrimary)
        Icon(Icons.Filled.ArrowDropDown, null, tint = if (onGradient) Color.White else AppTheme.colors.textPrimary)
    }
    if (open) {
        AppSheet(stringResource(Res.string.choose_knesset), { open = false }) {
            KnessetGrid(selection.terms, selection.current, selection.selected, knessetLabel(null)) { ctx.select(it); open = false }
        }
    }
}

/** The actions of every root tab header: Knesset pill, search, overflow. */
@Composable
fun RowScope.RootActions() {
    val nav = LocalNav.current
    var menu by remember { mutableStateOf(false) }
    KnessetSelectorChip()
    IconButton(onClick = { nav.navigate(SearchRoute()) }) { Icon(Icons.Filled.Search, stringResource(Res.string.search), tint = Color.White) }
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(Res.string.more), tint = Color.White) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = Radius.tile) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.favorites)) }, onClick = { menu = false; nav.navigate(FavoritesRoute) })
            DropdownMenuItem(text = { Text(stringResource(Res.string.downloads)) }, onClick = { menu = false; nav.navigate(com.chencohensolutions.knesset.ui.DownloadsRoute) })
            DropdownMenuItem(text = { Text(stringResource(Res.string.data_explorer)) }, onClick = { menu = false; nav.navigate(ExplorerRoute) })
            DropdownMenuItem(text = { Text(stringResource(Res.string.settings)) }, onClick = { menu = false; nav.navigate(SettingsRoute) })
            DropdownMenuItem(text = { Text(stringResource(Res.string.about)) }, onClick = { menu = false; nav.navigate(AboutRoute) })
        }
    }
}

/** Top bar of every root tab (kept for screens that do not scroll with a hero header). */
@Composable
fun RootTopBar(title: String) {
    val behavior = TopAppBarDefaults.pinnedScrollBehavior()
    GradientHeader(title, AppTheme.section, behavior, actions = { RootActions() })
}

@Composable
fun DetailTopBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    val nav = LocalNav.current
    val behavior = TopAppBarDefaults.pinnedScrollBehavior()
    GradientHeader(title, AppTheme.section, behavior, onBack = { nav.popBackStack() }, actions = actions)
}
