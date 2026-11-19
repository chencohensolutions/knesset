package com.chencohensolutions.knesset.ui.components

import androidx.compose.foundation.layout.Box
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

/** The global Knesset selector chip (plan 2.2). */
@Composable
fun KnessetSelectorChip() {
    val ctx = koinInject<KnessetContext>()
    val selection by ctx.selection.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(knessetLabel(selection.selected), maxLines = 1) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(knessetLabel(null)) }, onClick = { ctx.select(null); open = false })
            selection.terms.forEach { n ->
                DropdownMenuItem(text = { Text(knessetLabel(n)) }, onClick = { ctx.select(n); open = false })
            }
        }
    }
}

/** Top bar of every root tab: title, Knesset selector, search, overflow. */
@Composable
fun RootTopBar(title: String) {
    val nav = LocalNav.current
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
            KnessetSelectorChip()
            IconButton(onClick = { nav.navigate(SearchRoute()) }) { Icon(Icons.Filled.Search, stringResource(Res.string.search)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(Res.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(Res.string.favorites)) }, onClick = { menu = false; nav.navigate(FavoritesRoute) })
                    DropdownMenuItem(text = { Text(stringResource(Res.string.data_explorer)) }, onClick = { menu = false; nav.navigate(ExplorerRoute) })
                    DropdownMenuItem(text = { Text(stringResource(Res.string.settings)) }, onClick = { menu = false; nav.navigate(SettingsRoute) })
                    DropdownMenuItem(text = { Text(stringResource(Res.string.about)) }, onClick = { menu = false; nav.navigate(AboutRoute) })
                }
            }
        },
    )
}

@Composable
fun DetailTopBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    val nav = LocalNav.current
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
            }
        },
        actions = actions,
    )
}
