package com.waha.domain

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.waha.data.PipedApi
import com.waha.data.PipedPlaybackSource
import com.waha.data.PlaybackCache
import com.waha.data.YoutubeExtractor
import com.waha.data.firstNonNull
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long on-device extraction may take.
 *
 * Not shortened: extraction is the only lane that works when the public Piped
 * instances are down, and on a slow network it regularly needs 12-15s. Cutting
 * this to ten seconds made PlaybackResolver give up on videos it used to play;
 * the win has to come from racing the backup lane, not from starving the primary.
 */
private const val EXTRACTION_TIMEOUT_MS = 18_000L

/** How long the Piped lane may take (its instances are raced in parallel). */
private const val PIPED_RESOLVE_TIMEOUT_MS = 6_000L

/**
 * How long extraction runs alone before Piped joins the race. Extraction is the
 * reliable path, so the first second and a half belongs to it; a fast failure
 * starts Piped immediately instead of waiting this out.
 */
private const val PIPED_HEAD_START_MS = 1_500L

/** Resolve diagnostics: one line per player open, cheap and useful on a device. */
private const val TAG = "PlaybackResolver"

private val YOUTUBE_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

/** Recovers a YouTube id from a thumbnail URL (`/vi/<id>/...`) or a `v=` query parameter. */
private val YOUTUBE_ID_IN_URL_REGEX = Regex("(?:/vi/|[?&]v=)([A-Za-z0-9_-]{11})")

/**
 * Turns a video into something ExoPlayer can play.
 *
 * On-device extraction is the primary lane, because it uses the device's own
 * network and is not affected by YouTube's bot-blocking of cloud IPs; public
 * Piped instances are a hedged backup that joins the race shortly after. A URL
 * that was already resolved for the same (video, quality) is served from the
 * in-memory cache, which makes reopening a video or switching back to a quality
 * instant.
 *
 * Both UI modules share this resolution path.
 */
object PlaybackResolver {

    /**
     * Resolves the YouTube id to hand to the extractor. Uses the explicit id when
     * it looks like a YouTube id, otherwise recovers it from the thumbnail URL.
     */
    fun extractYouTubeId(video: VideoItem): String? {
        video.youtubeId.takeIf { YOUTUBE_ID_REGEX.matches(it) }?.let { return it }
        return YOUTUBE_ID_IN_URL_REGEX.find(video.thumbnailUrl.orEmpty())?.groupValues?.get(1)
    }

    /**
     * Resolves [video] into a playable source, or null when nothing is playable.
     * [maxHeight] caps the resolution so the player can offer a quality choice.
     */
    suspend fun resolve(video: VideoItem, maxHeight: Int? = null): PipedPlaybackSource? {
        val youtubeId = extractYouTubeId(video) ?: return null

        PlaybackCache.get(youtubeId, maxHeight)?.let {
            Log.d(TAG, "cache hit $youtubeId (maxHeight=$maxHeight)")
            return it
        }

        val startedAt = System.nanoTime()
        val source = firstNonNull(
            primary = {
                withTimeoutOrNull(EXTRACTION_TIMEOUT_MS) {
                    YoutubeExtractor.resolvePlaybackSource(youtubeId, maxHeight = maxHeight)
                }
            },
            backup = {
                withTimeoutOrNull(PIPED_RESOLVE_TIMEOUT_MS) {
                    PipedApi.resolvePlaybackSource(youtubeId)
                }
            },
            backupDelayMs = PIPED_HEAD_START_MS
        )

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        if (source != null) {
            PlaybackCache.put(youtubeId, maxHeight, source)
            Log.d(TAG, "resolved $youtubeId in ${elapsedMs}ms (maxHeight=$maxHeight)")
        } else {
            Log.w(TAG, "no playable source for $youtubeId after ${elapsedMs}ms")
        }
        return source
    }
}

/** Builds the Media3 source for a resolved stream (merging video + audio when split). */
fun PipedPlaybackSource.toMediaSource(context: Context): MediaSource {
    val dataSourceFactory = DefaultDataSource.Factory(context)
    return when (this) {
        is PipedPlaybackSource.Progressive ->
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url))

        is PipedPlaybackSource.Merged -> MergingMediaSource(
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(videoUrl)),
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(audioUrl))
        )
    }
}

/**
 * Shared ExoPlayer setup. The UI modules own the player's lifecycle (create,
 * release) and its surface; the configuration lives here so phone and TV play
 * identically.
 */
object WahaPlayer {
    fun create(context: Context): ExoPlayer = ExoPlayer.Builder(context).build()
}
