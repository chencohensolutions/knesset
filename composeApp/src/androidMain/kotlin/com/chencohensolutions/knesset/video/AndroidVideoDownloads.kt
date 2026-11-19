package com.chencohensolutions.knesset.video

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.chencohensolutions.knesset.data.video.DownloadItem
import com.chencohensolutions.knesset.data.video.DownloadQueue
import com.chencohensolutions.knesset.data.video.DownloadState
import com.chencohensolutions.knesset.data.video.HlsEstimate
import com.chencohensolutions.knesset.data.video.VideoDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads chosen by the user, one at a time, as MP4 files in Movies/Knesset. The queue is kept in
 * SharedPreferences; [VideoDownloadService] does the work in the foreground.
 */
class AndroidVideoDownloads(private val context: Context) : VideoDownloads {
    private val prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(DownloadItem.serializer())

    private val _items = MutableStateFlow(load())
    override val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private fun load(): List<DownloadItem> = runCatching {
        json.decodeFromString(serializer, prefs.getString(KEY, "[]")!!)
    }.getOrDefault(emptyList()).map {
        // A download that was running when the app died is queued again.
        if (it.state == DownloadState.RUNNING) it.copy(state = DownloadState.QUEUED, progress = 0) else it
    }

    @Synchronized
    fun dispatch(event: DownloadQueue.Event) {
        val next = DownloadQueue.reduce(_items.value, event)
        _items.value = next
        prefs.edit().putString(KEY, json.encodeToString(serializer, next)).apply()
    }

    fun item(url: String) = _items.value.firstOrNull { it.url == url }

    override suspend fun estimate(url: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val master = get(url)
            val variants = HlsEstimate.variants(master)
            if (variants.isEmpty()) {
                // A media playlist without variants: no bandwidth to multiply.
                return@runCatching null
            }
            val best = variants.maxBy { it.bandwidth }
            val media = get(HlsEstimate.resolve(url, best.uri))
            HlsEstimate.bytes(best.bandwidth, HlsEstimate.durationSeconds(media)).takeIf { it > 0 }
        }.getOrNull()
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "KnessetApp")
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    override fun isMetered(): Boolean =
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered

    override fun freeBytes(): Long = runCatching { StatFs(context.cacheDir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)

    override fun start(url: String, title: String, estimatedBytes: Long?) {
        dispatch(DownloadQueue.Event.Enqueue(url, title, estimatedBytes, System.currentTimeMillis()))
        ContextCompat.startForegroundService(context, Intent(context, VideoDownloadService::class.java))
    }

    override fun cancel(url: String) {
        context.startService(Intent(context, VideoDownloadService::class.java).setAction(VideoDownloadService.ACTION_CANCEL).putExtra(VideoDownloadService.EXTRA_URL, url))
        dispatch(DownloadQueue.Event.Cancel(url))
    }

    override fun delete(url: String) {
        item(url)?.fileUri?.let { uri -> runCatching { deleteFile(Uri.parse(uri)) } }
        dispatch(DownloadQueue.Event.Remove(url))
    }

    override fun openExternally(url: String): Boolean {
        val uri = item(url)?.fileUri?.let(Uri::parse) ?: return false
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(view); true }.getOrDefault(false)
    }

    /** Copies a finished MP4 into Movies/Knesset (the gallery and Files app see it) and returns its URI. */
    fun publish(temp: File, fileName: String): Uri {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Knesset")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: error("MediaStore refused the file")
            resolver.openOutputStream(uri)!!.use { out -> temp.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            return uri
        }
        // Before Android 10 a shared folder needs a storage permission, so the file stays in the app's own Movies folder.
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Knesset").apply { mkdirs() }
        val target = File(dir, fileName)
        temp.copyTo(target, overwrite = true)
        return FileProvider.getUriForFile(context, context.packageName + ".files", target)
    }

    private fun deleteFile(uri: Uri) {
        if (uri.authority == context.packageName + ".files") {
            File(File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Knesset"), uri.lastPathSegment ?: return).delete()
        } else {
            context.contentResolver.delete(uri, null, null)
        }
    }

    private companion object {
        const val KEY = "items"
    }
}
