package com.waha.data

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean

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
 * instances.
 *
 * Every instance is tried **in parallel** with a short per-instance timeout, and
 * the first usable answer wins. Instances used to be tried one after another,
 * each with an eight-second budget, so a bad day for the public instances could
 * cost thirty seconds before the player even started. The public directory is
 * refreshed in the background once every few hours — it used to be fetched on
 * every resolve, *before* the known instances were even tried, adding a full
 * round trip to the common case.
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

    private const val TAG = "PipedApi"

    /** Per-instance budget: an instance that cannot answer in this window is useless to the player. */
    private const val INSTANCE_TIMEOUT_MS = 4_000L

    /** How long the discovered instance list stays fresh. */
    private const val INSTANCE_LIST_TTL_MS = 6L * 60L * 60L * 1_000L

    /** Retry the directory sooner when it failed, so a restart does not pin a bad list for hours. */
    private const val INSTANCE_LIST_RETRY_MS = 15L * 60L * 1_000L

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient by lazy {
        HttpClient(Android) {
            install(HttpTimeout) {
                connectTimeoutMillis = INSTANCE_TIMEOUT_MS
                requestTimeoutMillis = INSTANCE_TIMEOUT_MS
                socketTimeoutMillis = INSTANCE_TIMEOUT_MS
            }
        }
    }

    private val discoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val discoveryInFlight = AtomicBoolean(false)

    @Volatile
    private var discovered: List<String> = emptyList()

    @Volatile
    private var discoveredAtMs: Long = 0L

    @Serializable
    private data class PipedInstance(val api_url: String)

    /** Resolves a YouTube video id into a playable source, or null when unavailable. */
    suspend fun resolvePlaybackSource(videoId: String): PipedPlaybackSource? {
        refreshInstancesIfStale()
        val streams = fetchStreams(videoId) ?: return null
        return pickPlaybackSource(streams)
    }

    /**
     * Races every instance and returns the first usable payload.
     *
     * The remaining requests are cancelled as soon as one wins, so a resolve
     * costs one round trip, not one per instance.
     */
    private suspend fun fetchStreams(videoId: String): PipedStreams? = coroutineScope {
        val instances = PipedInstances.order(
            known = instanceBaseUrls,
            discovered = discovered,
            health = PipedInstanceHealth::peek
        )
        if (instances.isEmpty()) return@coroutineScope null

        val results = Channel<Pair<String, PipedStreams?>>(Channel.UNLIMITED)
        val jobs = instances.map { base ->
            launch {
                val startedAt = System.nanoTime()
                val streams = attempt(base, videoId)
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                val health = PipedInstanceHealth.of(base)
                if (streams != null) health.recordSuccess(elapsedMs) else health.recordFailure()
                results.send(base to streams)
            }
        }

        var remaining = jobs.size
        var winner: PipedStreams? = null
        var winnerBase: String? = null
        while (remaining > 0 && winner == null) {
            val (base, streams) = results.receive()
            remaining--
            if (streams != null) {
                winner = streams
                winnerBase = base
            }
        }
        Log.d(TAG, "$videoId answered by ${winnerBase ?: "no instance"} (${jobs.size} tried)")

        jobs.forEach { it.cancel() }
        winner
    }

    /** One `GET /streams/{id}` against one instance; null for any failure. */
    private suspend fun attempt(base: String, videoId: String): PipedStreams? = try {
        val response = httpClient.get("$base/streams/$videoId")
        if (!response.status.isSuccess()) {
            null
        } else {
            val streams = json.decodeFromString<PipedStreams>(response.bodyAsText())
            val hasStreams = streams.videoStreams.isNotEmpty() || streams.audioStreams.isNotEmpty()
            if (streams.error.isNullOrBlank() && hasStreams) streams else null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Instance unavailable or returned unexpected data: another one may still answer.
        Log.d(TAG, "$base failed: ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    /**
     * Refreshes the public directory in the background; the current list is used
     * meanwhile, so a resolve never waits for discovery.
     */
    private fun refreshInstancesIfStale() {
        val now = System.currentTimeMillis()
        if (now - discoveredAtMs < INSTANCE_LIST_TTL_MS) return
        if (!discoveryInFlight.compareAndSet(false, true)) return
        discoveryScope.launch {
            try {
                val found = discoverInstances()
                discovered = if (found.isNotEmpty()) found else discovered
                discoveredAtMs = if (found.isNotEmpty()) {
                    System.currentTimeMillis()
                } else {
                    // Keep the old list and retry sooner than a full TTL.
                    System.currentTimeMillis() - INSTANCE_LIST_TTL_MS + INSTANCE_LIST_RETRY_MS
                }
            } finally {
                discoveryInFlight.set(false)
            }
        }
    }

    private suspend fun discoverInstances(): List<String> = try {
        withTimeoutOrNull(INSTANCE_TIMEOUT_MS * 2) {
            val response = httpClient.get(INSTANCES_LIST_URL)
            if (response.status.isSuccess()) {
                json.decodeFromString<List<PipedInstance>>(response.bodyAsText())
                    .map { it.api_url }
                    .let(PipedInstances::distinct)
            } else {
                emptyList()
            }
        } ?: emptyList()
    } catch (e: CancellationException) {
        throw e
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
