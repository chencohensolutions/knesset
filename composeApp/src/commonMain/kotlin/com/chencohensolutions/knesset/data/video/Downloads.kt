package com.chencohensolutions.knesset.data.video

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

enum class DownloadState { QUEUED, RUNNING, DONE, FAILED, CANCELED }

/** Why a download failed, in terms the UI can explain. */
enum class DownloadFailure { NO_SPACE, NETWORK, CONVERSION, STORAGE, UNKNOWN }

@Serializable
data class DownloadItem(
    /** The stream URL is the identity: one download per video (each plenum part is its own). */
    val url: String,
    val title: String,
    val state: DownloadState = DownloadState.QUEUED,
    val progress: Int = 0,
    /** Where the finished MP4 is (a content URI on Android). */
    val fileUri: String? = null,
    val fileName: String? = null,
    val bytes: Long? = null,
    val estimatedBytes: Long? = null,
    val failure: DownloadFailure? = null,
    val createdAt: Long = 0,
)

/**
 * The download queue as a pure state machine: one video at a time, in the order the user asked, never
 * the same video twice. The platform runs the work and reports events back.
 */
object DownloadQueue {
    sealed interface Event {
        data class Enqueue(val url: String, val title: String, val estimatedBytes: Long?, val now: Long) : Event
        data class Started(val url: String) : Event
        data class Progress(val url: String, val percent: Int) : Event
        data class Finished(val url: String, val fileUri: String, val fileName: String, val bytes: Long?) : Event
        data class Failed(val url: String, val failure: DownloadFailure) : Event
        data class Cancel(val url: String) : Event
        data class Remove(val url: String) : Event
        data class Retry(val url: String) : Event
    }

    fun reduce(items: List<DownloadItem>, event: Event): List<DownloadItem> = when (event) {
        is Event.Enqueue -> {
            val existing = items.firstOrNull { it.url == event.url }
            when {
                existing == null -> items + DownloadItem(event.url, event.title, estimatedBytes = event.estimatedBytes, createdAt = event.now)
                existing.state == DownloadState.FAILED || existing.state == DownloadState.CANCELED ->
                    items.map { if (it.url == event.url) it.copy(state = DownloadState.QUEUED, progress = 0, failure = null) else it }
                else -> items // already queued, running or done
            }
        }
        is Event.Started -> items.map { if (it.url == event.url && it.state == DownloadState.QUEUED) it.copy(state = DownloadState.RUNNING, progress = 0) else it }
        is Event.Progress -> items.map { if (it.url == event.url && it.state == DownloadState.RUNNING) it.copy(progress = event.percent.coerceIn(0, 100)) else it }
        is Event.Finished -> items.map {
            if (it.url == event.url) it.copy(state = DownloadState.DONE, progress = 100, fileUri = event.fileUri, fileName = event.fileName, bytes = event.bytes, failure = null) else it
        }
        is Event.Failed -> items.map { if (it.url == event.url && it.state != DownloadState.DONE) it.copy(state = DownloadState.FAILED, failure = event.failure) else it }
        is Event.Cancel -> items.map {
            if (it.url == event.url && (it.state == DownloadState.QUEUED || it.state == DownloadState.RUNNING)) it.copy(state = DownloadState.CANCELED, progress = 0) else it
        }
        is Event.Remove -> items.filterNot { it.url == event.url }
        is Event.Retry -> reduce(items, Event.Enqueue(event.url, "", null, 0))
    }

    /** The next video to work on, or null while one is running or nothing waits. */
    fun next(items: List<DownloadItem>): DownloadItem? =
        if (items.any { it.state == DownloadState.RUNNING }) null else items.filter { it.state == DownloadState.QUEUED }.minByOrNull { it.createdAt }
}

object DownloadNames {
    private val unsafe = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    /** `Knesset - <title>.mp4`, safe for every file system, at most 100 characters. Hebrew is kept. */
    fun fileName(title: String): String {
        val clean = title.replace(unsafe, " ").replace('״', ' ').replace(Regex("\\s+"), " ").trim().trim('.')
        val base = ("Knesset - " + clean.ifBlank { "video" }).take(96).trimEnd()
        return "$base.mp4"
    }
}

/** Size estimate for an HLS stream before downloading: bandwidth of the variant times its duration. */
object HlsEstimate {
    data class Variant(val bandwidth: Long, val uri: String)

    fun variants(master: String): List<Variant> {
        val lines = master.lines().map { it.trim() }
        return lines.mapIndexedNotNull { i, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF")) return@mapIndexedNotNull null
            val bw = Regex("""BANDWIDTH=(\d+)""").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: return@mapIndexedNotNull null
            val uri = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return@mapIndexedNotNull null
            Variant(bw, uri)
        }
    }

    /** Total seconds of a media playlist (sum of #EXTINF). */
    fun durationSeconds(media: String): Double =
        Regex("""#EXTINF:([\d.]+)""").findAll(media).sumOf { it.groupValues[1].toDoubleOrNull() ?: 0.0 }

    fun bytes(bandwidthBitsPerSecond: Long, seconds: Double): Long = (bandwidthBitsPerSecond * seconds / 8).toLong()

    /** Resolves a playlist entry against the URL of the playlist that listed it. */
    fun resolve(base: String, uri: String): String = when {
        uri.startsWith("http://") || uri.startsWith("https://") -> uri
        uri.startsWith("/") -> base.substringBefore("://") + "://" + base.substringAfter("://").substringBefore('/') + uri
        else -> base.substringBeforeLast('/') + "/" + uri
    }
}

/** Platform side of downloads: the queue, the foreground work, and the files. */
interface VideoDownloads {
    val items: StateFlow<List<DownloadItem>>

    /** Estimated size in bytes, or null when it cannot be known. */
    suspend fun estimate(url: String): Long?

    /** True on mobile data or another metered network. */
    fun isMetered(): Boolean

    fun freeBytes(): Long

    fun start(url: String, title: String, estimatedBytes: Long?)

    fun cancel(url: String)

    /** Deletes the file and the record. */
    fun delete(url: String)

    /** Opens the saved MP4 in the gallery or another video app. */
    fun openExternally(url: String): Boolean
}
