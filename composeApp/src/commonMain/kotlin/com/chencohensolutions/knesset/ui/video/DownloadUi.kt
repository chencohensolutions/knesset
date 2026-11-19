package com.chencohensolutions.knesset.ui.video

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chencohensolutions.knesset.data.video.DownloadFailure
import com.chencohensolutions.knesset.data.video.DownloadItem
import com.chencohensolutions.knesset.data.video.DownloadState
import com.chencohensolutions.knesset.data.video.VideoDownloads
import com.chencohensolutions.knesset.resources.*
import com.chencohensolutions.knesset.ui.LocalNav
import com.chencohensolutions.knesset.ui.VideoPlayerRoute
import com.chencohensolutions.knesset.ui.components.AppCard
import com.chencohensolutions.knesset.ui.components.DataText
import com.chencohensolutions.knesset.ui.components.DetailTopBar
import com.chencohensolutions.knesset.ui.components.EmptyIllustration
import com.chencohensolutions.knesset.ui.components.VideoThumb
import com.chencohensolutions.knesset.ui.theme.AppTheme
import com.chencohensolutions.knesset.ui.theme.Space
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> ((bytes / 100_000_000) / 10.0).toString() + " GB"
    bytes >= 1_000_000 -> (bytes / 1_000_000).toString() + " MB"
    else -> ((bytes / 1_000).coerceAtLeast(1)).toString() + " KB"
}

@Composable
fun failureText(f: DownloadFailure?): String = when (f) {
    DownloadFailure.NO_SPACE -> stringResource(Res.string.dl_fail_space)
    DownloadFailure.NETWORK -> stringResource(Res.string.dl_fail_network)
    DownloadFailure.CONVERSION -> stringResource(Res.string.dl_fail_conversion)
    DownloadFailure.STORAGE -> stringResource(Res.string.dl_fail_storage)
    else -> stringResource(Res.string.dl_fail_unknown)
}

/** Asks before downloading: shows the estimated size, warns on mobile data and when space is short. */
@Composable
private fun rememberDownloadStarter(url: String, title: String): Pair<() -> Unit, @Composable () -> Unit> {
    val downloads = koinInject<VideoDownloads>()
    val askNotifications = rememberNotificationPermissionRequest()
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Long?>(null) }
    var showConfirm by remember { mutableStateOf(false) }
    val start: () -> Unit = {
        askNotifications()
        checking = true
        scope.launch {
            confirm = downloads.estimate(url)
            checking = false
            showConfirm = true
        }
    }
    val dialog: @Composable () -> Unit = {
        if (checking) {
            AlertDialog(onDismissRequest = {}, confirmButton = {}, text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    CircularProgressIndicator(Modifier.size(24.dp)); Text(stringResource(Res.string.dl_checking))
                }
            })
        }
        if (showConfirm) {
            val size = confirm
            val metered = downloads.isMetered()
            val short = size != null && downloads.freeBytes() < size * 2
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                title = { Text(stringResource(Res.string.dl_confirm_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        DataText(title, style = MaterialTheme.typography.bodyMedium)
                        Text(if (size != null) stringResource(Res.string.dl_size, formatBytes(size)) else stringResource(Res.string.dl_size_unknown))
                        Text(stringResource(Res.string.dl_where), color = AppTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        if (metered) Text(stringResource(Res.string.dl_metered), color = AppTheme.colors.caution)
                        if (short) Text(stringResource(Res.string.dl_fail_space), color = AppTheme.colors.negative)
                    }
                },
                confirmButton = {
                    Button(enabled = !short, onClick = { showConfirm = false; downloads.start(url, title, size) }) { Text(stringResource(Res.string.dl_start)) }
                },
                dismissButton = { TextButton(onClick = { showConfirm = false }) { Text(stringResource(Res.string.cancel)) } },
            )
        }
    }
    return start to dialog
}

/** Download controls under the player: start, progress with cancel, done, or failed with retry. */
@Composable
fun DownloadPanel(url: String, title: String) {
    val downloads = koinInject<VideoDownloads>()
    val items by downloads.items.collectAsStateWithLifecycle()
    val item = items.firstOrNull { it.url == url }
    val (start, dialog) = rememberDownloadStarter(url, title)
    dialog()
    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        when (item?.state) {
            null, DownloadState.CANCELED -> FilledTonalButton(onClick = start) { Icon(Icons.Outlined.Download, null); Text("  " + stringResource(Res.string.dl_download)) }
            DownloadState.QUEUED, DownloadState.RUNNING -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (item.state == DownloadState.QUEUED) stringResource(Res.string.dl_queued) else stringResource(Res.string.dl_progress, item.progress),
                        Modifier.weight(1f),
                    )
                    IconButton(onClick = { downloads.cancel(url) }) { Icon(Icons.Outlined.Close, stringResource(Res.string.cancel)) }
                }
                LinearProgressIndicator({ item.progress / 100f }, Modifier.fillMaxWidth(), color = AppTheme.accent)
            }
            DownloadState.DONE -> Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DownloadDone, null, tint = AppTheme.colors.positive)
                Text(stringResource(Res.string.dl_done), Modifier.weight(1f))
                TextButton(onClick = { downloads.openExternally(url) }) { Text(stringResource(Res.string.dl_open_gallery)) }
            }
            DownloadState.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = AppTheme.colors.negative)
                Text("  " + failureText(item.failure), Modifier.weight(1f))
                TextButton(onClick = start) { Text(stringResource(Res.string.retry)) }
            }
        }
    }
}

/** A compact download button for video rows; shows a check when the video is saved. */
@Composable
fun DownloadIconButton(url: String, title: String) {
    val downloads = koinInject<VideoDownloads>()
    val items by downloads.items.collectAsStateWithLifecycle()
    val item = items.firstOrNull { it.url == url }
    val (start, dialog) = rememberDownloadStarter(url, title)
    dialog()
    when (item?.state) {
        DownloadState.DONE -> Icon(Icons.Outlined.DownloadDone, stringResource(Res.string.dl_done), tint = AppTheme.colors.positive)
        DownloadState.RUNNING, DownloadState.QUEUED -> CircularProgressIndicator({ item.progress / 100f }, Modifier.size(24.dp), color = AppTheme.accent)
        else -> IconButton(onClick = start) { Icon(Icons.Outlined.Download, stringResource(Res.string.dl_download), tint = AppTheme.accent) }
    }
}

/** The Downloads list: play offline, open in the gallery, cancel or delete. */
@Composable
fun DownloadsScreen() {
    val downloads = koinInject<VideoDownloads>()
    val items by downloads.items.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    Scaffold(topBar = { DetailTopBar(stringResource(Res.string.downloads)) }, containerColor = AppTheme.colors.background) { padding ->
        if (items.isEmpty()) {
            EmptyIllustration(Modifier.padding(padding).fillMaxSize(), title = stringResource(Res.string.downloads_empty), line = stringResource(Res.string.downloads_empty_hint))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(Space.gutter), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            items(items.sortedByDescending { it.createdAt }, key = { it.url }) { item -> DownloadCard(item, downloads) { nav.navigate(VideoPlayerRoute(it, item.title)) } }
        }
    }
}

@Composable
private fun DownloadCard(item: DownloadItem, downloads: VideoDownloads, play: (String) -> Unit) {
    AppCard(Modifier.fillMaxWidth().animateContentSize(), onClick = item.fileUri?.takeIf { item.state == DownloadState.DONE }?.let { { play(it) } }) {
        Row(Modifier.padding(Space.m), horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.CenterVertically) {
            VideoThumb(Modifier.size(width = 96.dp, height = 54.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DataText(item.title, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                val c = AppTheme.colors
                when (item.state) {
                    DownloadState.DONE -> Text(listOfNotNull(stringResource(Res.string.dl_done), item.bytes?.let(::formatBytes)).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = c.positive)
                    DownloadState.RUNNING -> LinearProgressIndicator({ item.progress / 100f }, Modifier.fillMaxWidth(), color = AppTheme.accent)
                    DownloadState.QUEUED -> Text(stringResource(Res.string.dl_queued), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                    DownloadState.FAILED -> Text(failureText(item.failure), style = MaterialTheme.typography.labelMedium, color = c.negative)
                    DownloadState.CANCELED -> Text(stringResource(Res.string.dl_canceled), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                }
            }
            when (item.state) {
                DownloadState.DONE -> {
                    IconButton(onClick = { item.fileUri?.let(play) }) { Icon(Icons.Outlined.PlayArrow, stringResource(Res.string.play)) }
                    IconButton(onClick = { downloads.openExternally(item.url) }) { Icon(Icons.Outlined.PhotoLibrary, stringResource(Res.string.dl_open_gallery)) }
                    IconButton(onClick = { downloads.delete(item.url) }) { Icon(Icons.Outlined.Delete, stringResource(Res.string.delete)) }
                }
                DownloadState.RUNNING, DownloadState.QUEUED -> IconButton(onClick = { downloads.cancel(item.url) }) { Icon(Icons.Outlined.Close, stringResource(Res.string.cancel)) }
                else -> {
                    IconButton(onClick = { downloads.start(item.url, item.title, item.estimatedBytes) }) { Icon(Icons.Outlined.Refresh, stringResource(Res.string.retry)) }
                    IconButton(onClick = { downloads.delete(item.url) }) { Icon(Icons.Outlined.Delete, stringResource(Res.string.delete)) }
                }
            }
        }
    }
}
