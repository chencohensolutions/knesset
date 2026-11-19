package com.chencohensolutions.knesset.ui.video

import android.Manifest
import android.app.Application
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Keeps one player per video across full screen and rotation; released when the screen goes away. */
class PlayerHolder(app: Application) : AndroidViewModel(app) {
    private var player: ExoPlayer? = null

    fun player(url: String): ExoPlayer = player ?: ExoPlayer.Builder(getApplication()).build().also { p ->
        val item = MediaItem.Builder().setUri(Uri.parse(url))
        if (url.contains(".m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8)
        p.setMediaItem(item.build())
        p.prepare()
        p.playWhenReady = true
        player = p
    }

    override fun onCleared() {
        player?.release()
        player = null
    }
}

@OptIn(UnstableApi::class)
@Composable
actual fun VideoPlayer(url: String, modifier: Modifier, fullscreen: Boolean, onFullscreenChange: (Boolean) -> Unit, onError: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val holder = viewModel(key = "player:$url") { PlayerHolder(app) }
    val player = remember(holder) { holder.player(url) }
    val onFs by rememberUpdatedState(onFullscreenChange)
    val onErr by rememberUpdatedState(onError)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = onErr()
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                // Media controls (seek bar, time, play) stay left to right, as in every media app.
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                controllerShowTimeoutMs = 2_500
                setShowFastForwardButton(true)
                setShowRewindButton(true)
                setFullscreenButtonClickListener { onFs(it) }
                this.player = player
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { keepScreenOn = isPlaying }
                })
                keepScreenOn = player.isPlaying
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.setFullscreenButtonState(fullscreen)
        },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

@Composable
actual fun ImmersiveMode(enabled: Boolean, onExit: () -> Unit) {
    val activity = LocalActivity.current ?: return
    BackHandler(enabled) { onExit() }
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val oldOrientation = activity.requestedOrientation
        val oldCutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) window.attributes.layoutInDisplayCutoutMode else 0
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        }
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = oldCutout }
            }
            activity.requestedOrientation = oldOrientation.takeIf { it != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE } ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

@Composable
actual fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

