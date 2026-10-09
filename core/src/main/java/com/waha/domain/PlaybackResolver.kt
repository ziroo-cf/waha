package com.waha.domain

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.waha.data.PipedApi
import com.waha.data.PipedPlaybackSource
import com.waha.data.YoutubeExtractor
import kotlinx.coroutines.withTimeoutOrNull

/** How long to wait for on-device extraction before trying Piped. */
private const val EXTRACTION_TIMEOUT_MS = 18_000L

/** How long to wait for Piped when on-device extraction fails. */
private const val PIPED_RESOLVE_TIMEOUT_MS = 6_000L

private val YOUTUBE_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

/** Recovers a YouTube id from a thumbnail URL (`/vi/<id>/...`) or a `v=` query parameter. */
private val YOUTUBE_ID_IN_URL_REGEX = Regex("(?:/vi/|[?&]v=)([A-Za-z0-9_-]{11})")

/**
 * Turns a video into something ExoPlayer can play.
 *
 * On-device extraction runs first, because it uses the device's own network and
 * is not affected by YouTube's bot-blocking of cloud IPs; public Piped
 * instances are only a fallback. Both UI modules share this resolution path.
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
        return withTimeoutOrNull(EXTRACTION_TIMEOUT_MS) {
            YoutubeExtractor.resolvePlaybackSource(youtubeId, maxHeight = maxHeight)
        } ?: withTimeoutOrNull(PIPED_RESOLVE_TIMEOUT_MS) {
            PipedApi.resolvePlaybackSource(youtubeId)
        }
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
