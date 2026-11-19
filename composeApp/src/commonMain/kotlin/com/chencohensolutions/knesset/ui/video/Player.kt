package com.chencohensolutions.knesset.ui.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chencohensolutions.knesset.core.platform.UrlOpener
import com.chencohensolutions.knesset.resources.Res
import com.chencohensolutions.knesset.resources.open_in_browser
import com.chencohensolutions.knesset.resources.video_failed
import com.chencohensolutions.knesset.ui.components.DataText
import com.chencohensolutions.knesset.ui.components.DetailTopBar
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.stringResource

/** Plays an HLS stream in the app (Media3 on Android). Calls [onError] when the stream cannot play. */
@Composable
expect fun VideoPlayer(url: String, modifier: Modifier, onError: () -> Unit)

@Composable
fun VideoPlayerScreen(url: String, title: String, fallbackUrl: String?) {
    val opener = koinInject<UrlOpener>()
    var failed by remember { mutableStateOf(false) }
    Scaffold(topBar = { DetailTopBar(title) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!failed) VideoPlayer(url, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { failed = true }
            else Text(stringResource(Res.string.video_failed), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            DataText(title, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { opener.open(fallbackUrl ?: url) }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(Res.string.open_in_browser))
            }
        }
    }
}
