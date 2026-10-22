package com.doomanity.cooley.video.stream

import com.doomanity.cooley.video.VideoHttp
import com.maxrave.domain.mediaservice.video.VideoDetails
import com.maxrave.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "PlainClientLoader"

/** What one of YouTube's own app clients handed back for a video. */
sealed class PlainStream {
    abstract val userAgent: String
    abstract val clientLabel: String

    /** One HLS playlist with picture and sound together (the iPhone app's way). */
    data class Hls(
        val url: String,
        override val userAgent: String,
        override val clientLabel: String,
    ) : PlainStream()

    /** Direct file links: either one file with both, or a picture file plus a sound file. */
    data class Files(
        val videoUrl: String?,
        val audioUrl: String?,
        override val userAgent: String,
        override val clientLabel: String,
    ) : PlainStream()
}

class PlainLoaded(
    val stream: PlainStream,
    val details: VideoDetails?,
)

/**
 * Asks YouTube for a video the way its own phone apps do (iPhone first, then Android).
 *
 * Why this exists: LibreTube's loader talks to YouTube as an Apple Vision headset, and YouTube
 * refuses every "made for kids" video to that client ("This video is not available"; yt-dlp notes
 * the same for its android_vr and visionos clients). The phone apps are allowed to play them.
 * The iPhone client returns a ready HLS playlist (no signatures to solve), the Android client a
 * plain mp4 link. Both are asked with a fresh anonymous visitor id and no cookies.
 */
object PlainClientLoader {
    private class Client(
        val label: String,
        val name: String,
        val version: String,
        val number: Int,
        val userAgent: String,
        val extra: Map<String, Any>,
    )

    // Versions from yt-dlp's INNERTUBE_CLIENTS (2026-07).
    private val IOS =
        Client(
            label = "iPhone",
            name = "IOS",
            version = "21.26.4",
            number = 5,
            userAgent = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            extra = mapOf("deviceMake" to "Apple", "deviceModel" to "iPhone16,2", "osName" to "iPhone", "osVersion" to "18.3.2.22D82"),
        )

    private val ANDROID =
        Client(
            label = "Android",
            name = "ANDROID",
            version = "21.26.364",
            number = 3,
            userAgent = "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip",
            extra = mapOf("androidSdkVersion" to 30, "osName" to "Android", "osVersion" to "11"),
        )

    private val VISITOR_DATA = Regex("^Cg[stuv][A-Za-z0-9_-]{15,}")

    @Volatile
    private var cachedVisitor: Pair<String, Long>? = null

    /** Tries the iPhone client, then the Android one. Null when neither gave anything playable. */
    suspend fun load(videoId: String): PlainLoaded? =
        withContext(Dispatchers.IO) {
            val visitor = visitorData()
            for (client in listOf(IOS, ANDROID)) {
                val response =
                    runCatching { player(client, videoId, visitor) }
                        .onFailure { Logger.w(TAG, "${client.label} request for $videoId failed: ${it.message}") }
                        .getOrNull() ?: continue
                val status = response.obj("playabilityStatus")?.str("status")
                if (status != "OK") {
                    Logger.w(TAG, "${client.label} said $status for $videoId: ${response.obj("playabilityStatus")?.str("reason")}")
                    continue
                }
                val stream = response.obj("streamingData")?.let { pick(it, client) }
                if (stream == null) {
                    Logger.w(TAG, "${client.label} gave no usable stream for $videoId")
                    continue
                }
                Logger.i(TAG, "${client.label} client plays $videoId (${stream::class.simpleName})")
                return@withContext PlainLoaded(stream, details(videoId, response))
            }
            null
        }

    private fun pick(
        data: JsonObject,
        client: Client,
    ): PlainStream? {
        data.str("hlsManifestUrl")?.let { return PlainStream.Hls(it, client.userAgent, client.label) }
        val muxed =
            data
                .arr("formats")
                ?.mapNotNull { it as? JsonObject }
                ?.filter { it.str("url") != null }
                ?.maxByOrNull { it["height"]?.jsonPrimitive?.intOrNull ?: 0 }
        val adaptive = data.arr("adaptiveFormats")?.mapNotNull { it as? JsonObject }?.filter { it.str("url") != null }.orEmpty()
        val videoOnly =
            adaptive
                .filter { it.str("mimeType")?.startsWith("video/mp4") == true }
                .filter { (it["height"]?.jsonPrimitive?.intOrNull ?: 0) <= 1080 }
                .maxByOrNull { it["height"]?.jsonPrimitive?.intOrNull ?: 0 }
        val audioOnly =
            adaptive
                .filter { it.str("mimeType")?.startsWith("audio/") == true }
                .maxByOrNull { it["bitrate"]?.jsonPrimitive?.longOrNull ?: 0 }
        val videoHeight = videoOnly?.get("height")?.jsonPrimitive?.intOrNull ?: 0
        val muxedHeight = muxed?.get("height")?.jsonPrimitive?.intOrNull ?: 0
        return when {
            videoOnly != null && audioOnly != null && videoHeight > muxedHeight ->
                PlainStream.Files(videoOnly.str("url"), audioOnly.str("url"), client.userAgent, client.label)
            muxed != null -> PlainStream.Files(muxed.str("url"), null, client.userAgent, client.label)
            else -> null
        }
    }

    private fun player(
        client: Client,
        videoId: String,
        visitor: String?,
    ): JsonObject {
        val body =
            buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", client.name)
                        put("clientVersion", client.version)
                        put("hl", "en")
                        put("gl", "US")
                        put("userAgent", client.userAgent)
                        if (visitor != null) put("visitorData", visitor)
                        client.extra.forEach { (k, v) ->
                            when (v) {
                                is Int -> put(k, v)
                                else -> put(k, v.toString())
                            }
                        }
                    }
                }
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }
        val request =
            Request
                .Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("User-Agent", client.userAgent)
                .header("X-YouTube-Client-Name", client.number.toString())
                .header("X-YouTube-Client-Version", client.version)
                .apply { if (visitor != null) header("X-Goog-Visitor-Id", visitor) }
                .build()
        return VideoHttp.client.newCall(request).execute().use { response ->
            VideoHttp.json.parseToJsonElement(response.body.string()).jsonObject
        }
    }

    /** A fresh anonymous visitor id from sw.js_data (no cookie sent), kept for 10 minutes. */
    private fun visitorData(): String? {
        cachedVisitor?.let { (value, at) -> if (System.currentTimeMillis() - at < 10 * 60_000) return value }
        return runCatching {
            val request =
                Request
                    .Builder()
                    .url("https://music.youtube.com/sw.js_data")
                    .header("User-Agent", VideoHttp.USER_AGENT)
                    .build()
            val text = VideoHttp.client.newCall(request).execute().use { it.body.string() }
            VideoHttp.json
                .parseToJsonElement(text.substringAfter('\n'))
                .jsonArray[0]
                .jsonArray[2]
                .jsonArray
                .firstNotNullOf { (it as? JsonPrimitive)?.contentOrNull?.takeIf(VISITOR_DATA::containsMatchIn) }
        }.onFailure { Logger.w(TAG, "no visitor id: ${it.message}") }
            .getOrNull()
            ?.also { cachedVisitor = it to System.currentTimeMillis() }
    }

    /** The watch page's basics from the player response, for when the main loader failed. */
    private fun details(
        videoId: String,
        response: JsonObject,
    ): VideoDetails? {
        val vd = response.obj("videoDetails") ?: return null
        val thumbs = vd.obj("thumbnail")?.arr("thumbnails")?.mapNotNull { it as? JsonObject }.orEmpty()
        return VideoDetails(
            videoId = videoId,
            title = vd.str("title") ?: return null,
            description = vd.str("shortDescription").orEmpty(),
            channelId = vd.str("channelId"),
            channelName = vd.str("author"),
            viewCount = vd.str("viewCount")?.toLongOrNull() ?: -1,
            durationSeconds = vd.str("lengthSeconds")?.toLongOrNull() ?: 0,
            isLive = vd["isLive"]?.jsonPrimitive?.contentOrNull == "true",
            thumbnailUrl = thumbs.maxByOrNull { it["width"]?.jsonPrimitive?.intOrNull ?: 0 }?.str("url"),
        )
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
