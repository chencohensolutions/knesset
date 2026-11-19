package com.chencohensolutions.knesset.ui.favorites

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.chencohensolutions.knesset.data.repo.FavoritesRepo
import com.chencohensolutions.knesset.resources.Res
import com.chencohensolutions.knesset.resources.favorites
import com.chencohensolutions.knesset.resources.favorites_empty
import com.chencohensolutions.knesset.resources.updated
import com.chencohensolutions.knesset.ui.components.DetailTopBar
import com.chencohensolutions.knesset.ui.components.ItemRow
import com.chencohensolutions.knesset.ui.components.kindLabel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Favorites grouped by kind, with an "updated" dot (plan 2.8). Opening the screen checks for updates. */
@Composable
fun FavoritesScreen() {
    val repo = koinInject<FavoritesRepo>()
    val favorites by remember { repo.all() }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { repo.checkForUpdates() }
    Scaffold(topBar = { DetailTopBar(stringResource(Res.string.favorites)) }) { padding ->
        PullToRefreshBox(refreshing, onRefresh = { scope.launch { refreshing = true; repo.checkForUpdates(); refreshing = false } }, modifier = Modifier.padding(padding).fillMaxSize()) {
            if (favorites.isEmpty()) {
                Text(stringResource(Res.string.favorites_empty), Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
            } else {
                val updatedLabel = stringResource(Res.string.updated)
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    favorites.groupBy { it.kind }.forEach { (kind, list) ->
                        item(key = "h$kind") {
                            Text(stringResource(kindLabel(kind)), Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        items(list, key = { "${it.kind}${it.id}" }) { f ->
                            ItemRow(
                                f.title, f.subtitle, status = if (f.hasUpdate) updatedLabel else null, route = f.kind.route(f.id),
                                trailing = if (f.hasUpdate) { { Icon(Icons.Filled.Circle, updatedLabel, Modifier.padding(4.dp), tint = MaterialTheme.colorScheme.primary) } } else null,
                            )
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}
