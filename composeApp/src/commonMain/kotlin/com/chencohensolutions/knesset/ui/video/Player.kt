package com.chencohensolutions.knesset.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chencohensolutions.knesset.core.platform.UrlOpener
import com.chencohensolutions.knesset.resources.Res
import com.chencohensolutions.knesset.resources.back
import com.chencohensolutions.knesset.resources.open_in_browser
import com.chencohensolutions.knesset.resources.video_failed
import com.chencohensolutions.knesset.ui.components.DataText
import com.chencohensolutions.knesset.ui.components.rememberBack
import com.chencohensolutions.knesset.ui.theme.AppTheme
import com.chencohensolutions.knesset.ui.theme.Radius
import com.chencohensolutions.knesset.ui.theme.Space
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Plays a stream or a saved file. The player instance is kept for [url] across full screen and rotation,
 * so playback does not restart. Media controls stay left to right in every language.
 */
@Composable
expect fun VideoPlayer(url: String, modifier: Modifier, fullscreen: Boolean, onFullscreenChange: (Boolean) -> Unit, onError: () -> Unit)

/** Hides the system bars, turns to landscape, keeps the screen on; back leaves it. Restores everything on exit. */
@Composable
expect fun ImmersiveMode(enabled: Boolean, onExit: () -> Unit)

/** Asks for permission to show the download notification, once, when it is first needed. */
@Composable
expect fun rememberNotificationPermissionRequest(): () -> Unit

@Composable
fun VideoPlayerScreen(url: String, title: String, fallbackUrl: String?) {
    val opener = koinInject<UrlOpener>()
    var failed by remember { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val back = rememberBack()
    ImmersiveMode(fullscreen) { fullscreen = false }
    if (fullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            VideoPlayer(url, Modifier.fillMaxSize(), true, { fullscreen = it }) { failed = true }
        }
        return
    }
    Column(Modifier.fillMaxSize().background(AppTheme.colors.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) }
            DataText(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
        Box(
            Modifier.padding(horizontal = Space.gutter).fillMaxWidth().aspectRatio(16f / 9f).clip(Radius.card).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (!failed) VideoPlayer(url, Modifier.fillMaxSize(), false, { fullscreen = it }) { failed = true }
            else Text(stringResource(Res.string.video_failed), Modifier.padding(Space.l), color = Color.White)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(Space.gutter), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            DataText(title, style = MaterialTheme.typography.titleLarge)
            if (url.startsWith("http")) DownloadPanel(url, title)
            OutlinedButton(onClick = { opener.open(fallbackUrl ?: url) }) {
                Icon(Icons.Outlined.OpenInBrowser, null)
                Text("  " + stringResource(Res.string.open_in_browser))
            }
        }
    }
}

