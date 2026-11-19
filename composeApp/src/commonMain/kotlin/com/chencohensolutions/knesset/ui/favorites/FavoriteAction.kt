package com.chencohensolutions.knesset.ui.favorites

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.chencohensolutions.knesset.data.repo.FavoritesRepo
import com.chencohensolutions.knesset.resources.Res
import com.chencohensolutions.knesset.resources.add_favorite
import com.chencohensolutions.knesset.resources.remove_favorite
import com.chencohensolutions.knesset.ui.detail.DetailContent
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** The favorite star in a detail top bar. Opening a starred item clears its "updated" dot. */
@Composable
fun FavoriteAction(content: DetailContent) {
    val kind = content.kind ?: return
    val repo = koinInject<FavoritesRepo>()
    val scope = rememberCoroutineScope()
    val starred by remember(kind, content.id) { repo.isFavorite(kind, content.id) }.collectAsState(false)
    LaunchedEffect(kind, content.id, content.lastUpdated) { repo.markSeen(kind, content.id, content.lastUpdated) }
    IconButton(onClick = {
        scope.launch {
            if (starred) repo.remove(kind, content.id)
            else repo.add(kind, content.id, content.title, content.favoriteSubtitle ?: content.subtitle, content.lastUpdated)
        }
    }) {
        if (starred) Icon(Icons.Filled.Star, stringResource(Res.string.remove_favorite))
        else Icon(Icons.Outlined.StarBorder, stringResource(Res.string.add_favorite))
    }
}
