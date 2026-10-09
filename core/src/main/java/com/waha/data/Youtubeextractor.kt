package com.waha.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream

/**
 * Resolves YouTube video ids into playable stream URLs by extracting them
 * directly on the device with NewPipeExtractor.
 *
 * Unlike public instances (Piped/Invidious), extraction runs from the device's
 * own network, so it is not affected by YouTube's bot-blocking of cloud IPs.
 * The result reuses [PipedPlaybackSource] so the player can treat both the same.
 */
object YoutubeExtractor {

    private const val TAG = "YoutubeExtractor"
    private const val TIMEOUT_MS = 15_000
    private const val WATCH_URL = "https://www.youtube.com/watch?v="

    private val initialized = AtomicBoolean(false)

    /** `NewPipe.init` installs a process-wide downloader, so it must run exactly once. */
    private fun ensureInitialized() {
        if (!initialized.compareAndSet(false, true)) return
        val locale = Locale.getDefault()
        val country = locale.country.takeIf { it.isNotBlank() } ?: "US"
        val language = locale.language.takeIf { it.isNotBlank() } ?: "en"
        NewPipe.init(DownloaderImpl(), Localization(language, country), ContentCountry(country))
    }

    /**
     * Extracts a playable source for [videoId], or null when unavailable.
     *
     * [maxHeight] caps the video resolution (e.g. 360/720/1080) so the player can
     * offer a quality choice; null means "best available".
     */
    suspend fun resolvePlaybackSource(videoId: String, maxHeight: Int? = null): PipedPlaybackSource? =
        withContext(Dispatchers.IO) {
            ensureInitialized()
            try {
                val info = StreamInfo.getInfo(ServiceList.YouTube, WATCH_URL + videoId)
                pickPlaybackSource(info, maxHeight)
            } catch (e: Exception) {
                // Age/region restrictions, bot checks, parse failures: caller falls back.
                Log.w(TAG, "On-device extraction failed for $videoId", e)
                null
            }
        }

    /**
     * Prefers a muxed (video + audio) progressive stream within the cap; otherwise
     * merges the best video-only stream within the cap with the best audio stream.
     * When the cap matches nothing, it falls back to the best available stream.
     */
    private fun pickPlaybackSource(info: StreamInfo, maxHeight: Int?): PipedPlaybackSource? {
        val muxedStreams = info.videoStreams.filter { it.isUsable() && !it.isVideoOnly() }
        val videoOnlyStreams = info.videoOnlyStreams.filter { it.isUsable() }
        val audio = info.audioStreams.filter { it.isUsable() }.maxByOrNull { it.averageBitrate }

        fun bestWithin(streams: List<VideoStream>, cap: Int?): VideoStream? =
            streams.filter { cap == null || resolutionHeight(it.getResolution()) <= cap }
                .maxByOrNull { resolutionHeight(it.getResolution()) }

        bestWithin(muxedStreams, maxHeight)?.let { return PipedPlaybackSource.Progressive(it.content) }

        bestWithin(videoOnlyStreams, maxHeight)?.let { video ->
            if (audio != null) return PipedPlaybackSource.Merged(video.content, audio.content)
        }

        // The cap excluded everything playable: fall back to the best available.
        bestWithin(muxedStreams, null)?.let { return PipedPlaybackSource.Progressive(it.content) }

        val video = bestWithin(videoOnlyStreams, null)
        return when {
            video != null && audio != null -> PipedPlaybackSource.Merged(video.content, audio.content)
            video != null -> PipedPlaybackSource.Progressive(video.content)
            audio != null -> PipedPlaybackSource.Progressive(audio.content)
            else -> null
        }
    }

    /** Only streams with a direct URL that ExoPlayer can play as progressive media. */
    private fun Stream.isUsable(): Boolean =
        isUrl() && content.isNotBlank() && deliveryMethod != DeliveryMethod.HLS

    private fun resolutionHeight(resolution: String?): Int =
        resolution?.substringBefore('p')?.trim()?.toIntOrNull() ?: 0

    /** Minimal [Downloader] implementation so the extractor can reach the network. */
    private class DownloaderImpl : Downloader() {

        override fun execute(request: Request): Response {
            val connection = (URL(request.url()).openConnection() as HttpURLConnection).apply {
                requestMethod = request.httpMethod()
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                useCaches = false
            }

            request.headers()?.forEach { (name, values) ->
                if (values.isNotEmpty()) {
                    connection.setRequestProperty(name, values.joinToString(", "))
                }
            }

            val payload = request.dataToSend()
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Length", payload.size.toString())
            }

            try {
                if (payload != null) {
                    connection.outputStream.use { it.write(payload) }
                }
                val responseCode = connection.responseCode
                if (responseCode == 429) {
                    throw ReCaptchaException("ReCaptcha challenge requested", request.url())
                }
                val headers = HashMap<String, List<String>>()
                connection.headerFields?.forEach { (name, values) ->
                    if (name != null && values != null) headers[name] = values
                }
                return Response(
                    responseCode,
                    connection.responseMessage ?: "",
                    headers,
                    readBody(connection, responseCode),
                    connection.url.toString()
                )
            } finally {
                connection.disconnect()
            }
        }

        private fun readBody(connection: HttpURLConnection, responseCode: Int): String {
            val raw = (if (responseCode >= 400) connection.errorStream else connection.inputStream)
                ?: return ""
            val encoding = connection.contentEncoding
            val stream = if (encoding != null && encoding.contains("gzip", ignoreCase = true)) {
                GZIPInputStream(raw)
            } else {
                raw
            }
            return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }
}
