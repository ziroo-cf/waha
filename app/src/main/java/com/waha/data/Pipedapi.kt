package com.waha.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One progressive/adaptive stream entry returned by a Piped API instance. */
@Serializable
data class PipedStream(
    val url: String,
    val format: String? = null,
    val quality: String? = null,
    val mimeType: String? = null,
    val codec: String? = null,
    val videoOnly: Boolean = false,
    val bitrate: Long = 0L,
    val width: Int = 0,
    val height: Int = 0
)

/** Payload of `GET {instance}/streams/{videoId}`. */
@Serializable
data class PipedStreams(
    val title: String? = null,
    val hls: String? = null,
    val dash: String? = null,
    val duration: Long = 0L,
    val videoStreams: List<PipedStream> = emptyList(),
    val audioStreams: List<PipedStream> = emptyList(),
    val error: String? = null
)

/** A playable combination resolved from Piped: a single URL, or separate video + audio. */
sealed interface PipedPlaybackSource {
    data class Progressive(val url: String) : PipedPlaybackSource
    data class Merged(val videoUrl: String, val audioUrl: String) : PipedPlaybackSource
}

/**
 * Resolves YouTube video ids into playable stream URLs through public Piped API
 * instances. Instances are tried in order, and the public instance list is used
 * as a fallback when all known ones fail.
 */
object PipedApi {

    private val instanceBaseUrls = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://api.piped.private.coffee",
        "https://pipedapi.reallyaweso.me",
        "https://pipedapi.drgns.space"
    )

    private const val INSTANCES_LIST_URL = "https://piped-instances.kavin.rocks/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient by lazy {
        HttpClient(Android) {
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 8_000
                socketTimeoutMillis = 8_000
            }
        }
    }

    @Serializable
    private data class PipedInstance(val api_url: String)

    /** Resolves a YouTube video id into a playable source, or null when unavailable. */
    suspend fun resolvePlaybackSource(videoId: String): PipedPlaybackSource? {
        val streams = fetchStreams(videoId) ?: return null
        return pickPlaybackSource(streams)
    }

    private suspend fun fetchStreams(videoId: String): PipedStreams? {
        val bases = instanceBaseUrls + discoverInstances()
        for (base in bases) {
            val url = "${base.trimEnd('/')}/streams/$videoId"
            try {
                val response = httpClient.get(url)
                if (!response.status.isSuccess()) continue
                val streams = json.decodeFromString<PipedStreams>(response.bodyAsText())
                val hasStreams = streams.videoStreams.isNotEmpty() || streams.audioStreams.isNotEmpty()
                if (streams.error.isNullOrBlank() && hasStreams) return streams
            } catch (_: Exception) {
                // Instance unavailable or returned unexpected data: try the next one.
            }
        }
        return null
    }

    private suspend fun discoverInstances(): List<String> = try {
        val response = httpClient.get(INSTANCES_LIST_URL)
        if (response.status.isSuccess()) {
            json.decodeFromString<List<PipedInstance>>(response.bodyAsText()).map { it.api_url }
        } else {
            emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Prefers a muxed (audio + video) stream so a single URL can be played.
     * Falls back to merging the best video-only stream with the best audio stream.
     */
    private fun pickPlaybackSource(streams: PipedStreams): PipedPlaybackSource? {
        fun rank(stream: PipedStream): Long = stream.height * 100_000L + stream.bitrate

        val muxed = streams.videoStreams
            .filter { !it.videoOnly && it.url.isNotBlank() }
            .maxByOrNull(::rank)
        if (muxed != null) return PipedPlaybackSource.Progressive(muxed.url)

        val video = streams.videoStreams
            .filter { it.url.isNotBlank() }
            .maxByOrNull(::rank)
        val audio = streams.audioStreams
            .filter { it.url.isNotBlank() }
            .maxByOrNull { it.bitrate }

        return when {
            video != null && audio != null -> PipedPlaybackSource.Merged(video.url, audio.url)
            video != null -> PipedPlaybackSource.Progressive(video.url)
            audio != null -> PipedPlaybackSource.Progressive(audio.url)
            else -> null
        }
    }
}
