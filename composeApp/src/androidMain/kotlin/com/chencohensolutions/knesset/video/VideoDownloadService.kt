package com.chencohensolutions.knesset.video

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.chencohensolutions.knesset.MainActivity
import com.chencohensolutions.knesset.R
import com.chencohensolutions.knesset.data.video.DownloadFailure
import com.chencohensolutions.knesset.data.video.DownloadNames
import com.chencohensolutions.knesset.data.video.DownloadQueue
import org.koin.core.context.GlobalContext
import java.io.File
import java.io.IOException

/**
 * Remuxes one HLS stream at a time into an MP4 with Media3 Transformer (no re-encoding), then publishes
 * it to Movies/Knesset. Runs in the foreground with a progress notification, so it survives leaving the
 * screen. The stream is read at normal playback-download speed, one video at a time.
 */
@OptIn(UnstableApi::class)
class VideoDownloadService : Service() {
    private val downloads by lazy { GlobalContext.get().get<AndroidVideoDownloads>() }
    private val handler = Handler(Looper.getMainLooper())
    private var transformer: Transformer? = null
    private var currentUrl: String? = null
    private var temp: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    /** Notification text in the app's own language, which can differ from the phone's. */
    private val strings by lazy {
        val tag = getSharedPreferences("settings", MODE_PRIVATE).getString("language", "he") ?: "he"
        val config = android.content.res.Configuration(resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(tag)) }
        createConfigurationContext(config)
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, strings.getString(R.string.downloads_channel), NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val url = intent.getStringExtra(EXTRA_URL)
            if (url != null && url == currentUrl) stopCurrent()
            if (currentUrl == null) startNext()
            return START_NOT_STICKY
        }
        goForeground(null, 0)
        if (currentUrl == null) startNext()
        return START_NOT_STICKY
    }

    private fun goForeground(title: String?, percent: Int) {
        val n = notification(title, percent)
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, n,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
    }

    private fun notification(title: String?, percent: Int): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(strings.getString(R.string.downloading_video))
            .setContentText(title ?: "")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(open)
            .setProgress(100, percent, percent == 0)
        currentUrl?.let { url ->
            val cancel = PendingIntent.getService(
                this, 1, Intent(this, VideoDownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_URL, url),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(0, strings.getString(R.string.cancel_download), cancel)
        }
        return b.build()
    }

    private fun startNext() {
        val item = DownloadQueue.next(downloads.items.value)
        if (item == null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        // Check space against the estimate (twice: the temporary file and the published copy).
        val need = (item.estimatedBytes ?: 0L) * 2
        if (need > 0 && downloads.freeBytes() < need) {
            downloads.dispatch(DownloadQueue.Event.Failed(item.url, DownloadFailure.NO_SPACE))
            startNext()
            return
        }
        currentUrl = item.url
        downloads.dispatch(DownloadQueue.Event.Started(item.url))
        goForeground(item.title, 0)
        val out = File(cacheDir, "download-${System.currentTimeMillis()}.mp4")
        temp = out
        val t = Transformer.Builder(this)
            .setLooper(Looper.getMainLooper())
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) = finish(item.url, item.title, out)
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) = fail(item.url, exportException)
            })
            .build()
        transformer = t
        val media = MediaItem.Builder().setUri(item.url).setMimeType(MimeTypes.APPLICATION_M3U8).build()
        t.start(media, out.absolutePath)
        pollProgress(item.url, item.title)
    }

    private fun pollProgress(url: String, title: String) {
        val holder = ProgressHolder()
        handler.postDelayed(object : Runnable {
            override fun run() {
                val t = transformer ?: return
                if (currentUrl != url) return
                if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    downloads.dispatch(DownloadQueue.Event.Progress(url, holder.progress))
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title, holder.progress))
                }
                handler.postDelayed(this, 1_000)
            }
        }, 1_000)
    }

    private fun finish(url: String, title: String, out: File) {
        transformer = null
        try {
            val name = DownloadNames.fileName(title)
            val uri = downloads.publish(out, name)
            downloads.dispatch(DownloadQueue.Event.Finished(url, uri.toString(), name, out.length()))
        } catch (e: IOException) {
            downloads.dispatch(DownloadQueue.Event.Failed(url, if (isNoSpace(e)) DownloadFailure.NO_SPACE else DownloadFailure.STORAGE))
        } catch (e: Exception) {
            downloads.dispatch(DownloadQueue.Event.Failed(url, DownloadFailure.STORAGE))
        } finally {
            out.delete()
            currentUrl = null
            startNext()
        }
    }

    private fun fail(url: String, e: ExportException) {
        transformer = null
        temp?.delete()
        val failure = when {
            isNoSpace(e) -> DownloadFailure.NO_SPACE
            e.errorCode in 2000..2999 -> DownloadFailure.NETWORK
            e.errorCode in 3000..4999 -> DownloadFailure.CONVERSION
            else -> DownloadFailure.UNKNOWN
        }
        downloads.dispatch(DownloadQueue.Event.Failed(url, failure))
        currentUrl = null
        startNext()
    }

    private fun stopCurrent() {
        transformer?.cancel()
        transformer = null
        temp?.delete()
        currentUrl = null
    }

    private fun isNoSpace(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            if (t.message?.contains("ENOSPC") == true || t.message?.contains("No space", ignoreCase = true) == true) return true
            t = t.cause
        }
        return false
    }

    override fun onDestroy() {
        stopCurrent()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "downloads"
        const val NOTIFICATION_ID = 41
        const val ACTION_CANCEL = "cancel"
        const val EXTRA_URL = "url"
    }
}
