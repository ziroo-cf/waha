package com.waha.domain

/**
 * Arabic "watched x ago" label for an epoch-millis timestamp.
 *
 * Shared by the phone and TV history screens, which previously had to duplicate
 * it (or drop the label) — the phone build owned the only copy.
 */
fun watchedAgoLabel(watchedAtMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val minutes = ((nowMs - watchedAtMs) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "الآن"
        minutes < 60 -> "قبل $minutes دقيقة"
        minutes < 60 * 24 -> {
            val hours = minutes / 60
            "قبل $hours ساعة"
        }
        minutes < 60 * 24 * 30 -> {
            val days = minutes / (60 * 24)
            "قبل $days يوم"
        }
        else -> "قبل فترة طويلة"
    }
}
