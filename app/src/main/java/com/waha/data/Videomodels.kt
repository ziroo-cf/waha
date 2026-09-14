package com.waha.data

import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

/** Matches "m:ss", "h:mm:ss", or Postgres interval text like "1 day 04:30:00". */
private val CLOCK_DURATION_REGEX =
    Regex("""^(?:(\d+)\s+days?\s+)?(\d+):([0-5]?\d)(?::([0-5]?\d))?$""")

/** Matches ISO-8601 durations like "PT4M30S" or "P1DT2H". */
private val ISO_DURATION_REGEX =
    Regex("""^P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?$""")

/**
 * Parses a video duration from JSON content in any of the shapes a Supabase
 * column may store it in: plain seconds ("270"), "m:ss", "h:mm:ss",
 * Postgres interval text, or ISO-8601 ("PT4M30S"). Returns ZERO when unparseable.
 */
internal fun parseVideoDuration(raw: String?): Duration {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return Duration.ZERO

    text.toLongOrNull()?.let { return it.seconds }

    CLOCK_DURATION_REGEX.matchEntire(text)?.let { match ->
        val days = match.groupValues[1].toLongOrNull() ?: 0L
        val secondsGroup = match.groupValues[4].toLongOrNull()
        val totalSeconds = if (secondsGroup == null) {
            // "m:ss"
            match.groupValues[2].toLong() * 60 + match.groupValues[3].toLong()
        } else {
            // "h:mm:ss"
            match.groupValues[2].toLong() * 3_600 +
                match.groupValues[3].toLong() * 60 +
                secondsGroup
        }
        return (days * 86_400 + totalSeconds).seconds
    }

    ISO_DURATION_REGEX.matchEntire(text)?.let { match ->
        val days = match.groupValues[1].toLongOrNull() ?: 0L
        val hours = match.groupValues[2].toLongOrNull() ?: 0L
        val minutes = match.groupValues[3].toLongOrNull() ?: 0L
        val seconds = (match.groupValues[4].toDoubleOrNull() ?: 0.0).toLong()
        return ((days * 86_400) + (hours * 3_600) + (minutes * 60) + seconds).seconds
    }

    return Duration.ZERO
}

/**
 * Deserializes a duration stored either as a JSON number (seconds) or a string
 * ("m:ss" / "h:mm:ss" / ISO-8601), and serializes it back as plain seconds.
 */
object FlexibleDurationSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("WahaVideoDuration", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Duration {
        val content = when (decoder) {
            is JsonDecoder -> {
                val element = decoder.decodeJsonElement()
                (element as? JsonPrimitive)?.content ?: return Duration.ZERO
            }
            else -> decoder.decodeString()
        }
        return parseVideoDuration(content)
    }

    override fun serialize(encoder: Encoder, value: Duration) {
        encoder.encodeLong(value.inWholeSeconds)
    }
}

@Serializable
data class VideoRow(
    val id: String,
    @SerialName("channel_id") val channelId: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val category: String? = null,
    val status: String = "pending",
    // Accepts seconds (number) or "m:ss"/"h:mm:ss"/ISO text; null/absent hides the badge.
    @Serializable(with = FlexibleDurationSerializer::class)
    val duration: Duration? = null
)

/** Formats a duration as "m:ss" (or "h:mm:ss" past one hour) for thumbnail badges. */
fun Duration.toVideoDurationText(): String {
    val totalSeconds = inWholeSeconds
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
