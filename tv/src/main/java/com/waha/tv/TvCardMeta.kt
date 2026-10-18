package com.waha.tv

import com.waha.domain.VideoItem

/**
 * The detail a TV card always shows beneath its artwork: the title (drawn by the
 * card itself), the category it belongs to, and how long it runs.
 *
 * The category is what a viewer browses by on a shelf, so it is the line under
 * the title — the library's `category` column, which is also what the home
 * screen's rows are built from. The lookup is pure so the "never a blank line"
 * promise can be unit tested without a device.
 */
object TvCardMeta {

    /**
     * Shown when a row carries no category at all. The video still appears in the
     * "الكل" row and in search, so the card says why it has no category row.
     */
    const val NO_CATEGORY = "بدون تصنيف"

    /** The video's category, trimmed, falling back to [NO_CATEGORY]. */
    fun categoryLabel(video: VideoItem): String =
        video.categoryKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: video.meta.trim().takeIf { it.isNotEmpty() }
            ?: NO_CATEGORY

    /** The video's length ("4:30"), or null when the library did not record one. */
    fun timeLabel(video: VideoItem): String? =
        video.durationText?.trim()?.takeIf { it.isNotEmpty() }
}
