package com.waha.domain

import com.waha.data.VideoRow
import com.waha.data.toVideoDurationText
import kotlin.time.Duration

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
    val durationText: String? = null
)

/** Best available thumbnail, falling back to YouTube's own artwork. */
fun VideoItem.displayThumbnailUrl(): String =
    thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: "https://img.youtube.com/vi/$youtubeId/hqdefault.jpg"

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
