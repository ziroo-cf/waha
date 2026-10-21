package com.waha.domain

import com.waha.data.VideoEntity
import com.waha.data.VideoRow
import com.waha.data.toVideoDurationText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A video as the UI layers consume it: presentation-ready fields plus the
 * YouTube id both the mobile and TV players need for stream resolution and
 * thumbnail fallbacks.
 */
data class VideoItem(
    val id: String,
    val youtubeId: String,
    val title: String,
    val meta: String,
    val thumbnailUrl: String? = null,
    val categoryKey: String? = null,
    val durationText: String? = null,
    /**
     * The channel the video came from. The library does not carry one yet —
     * everything published is Waha's own channel — but the TV card always shows
     * a channel line, so the slot (and its fallback) exists from the start.
     */
    val channelName: String? = null
)

/** Best available thumbnail, falling back to YouTube's own artwork. */
fun VideoItem.displayThumbnailUrl(): String =
    thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: "https://img.youtube.com/vi/$youtubeId/hqdefault.jpg"

/**
 * Maps a cached row onto the shared UI model.
 *
 * The duration badge text is read from the row, not rebuilt: it is formatted
 * once when the library is written to the cache, so reading the library is a
 * plain field copy for every card.
 */
fun VideoEntity.toVideoItem(): VideoItem = VideoItem(
    id = id,
    youtubeId = id,
    title = title,
    meta = category.orEmpty(),
    thumbnailUrl = thumbnail,
    categoryKey = category,
    durationText = durationText
        ?: durationSeconds.takeIf { it > 0L }?.seconds?.toVideoDurationText()
)

/** Maps a Supabase row onto the shared UI model; every app module reuses this. */
fun VideoRow.toVideoItem(): VideoItem = VideoItem(
    id = id,
    youtubeId = id,
    title = title ?: "بدون عنوان",
    meta = category.orEmpty(),
    thumbnailUrl = thumbnail?.takeIf { it.isNotBlank() },
    categoryKey = category,
    durationText = duration
        ?.takeIf { it > Duration.ZERO }
        ?.toVideoDurationText()
)
