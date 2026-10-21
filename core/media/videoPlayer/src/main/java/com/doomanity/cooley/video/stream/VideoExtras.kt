package com.doomanity.cooley.video.stream

import com.doomanity.cooley.video.VideoHttp
import com.maxrave.domain.mediaservice.video.SkipSegment
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder
import java.security.MessageDigest

/** SponsorBlock and Return YouTube Dislike, as LibreTube calls them. */
object VideoExtras {
    /** What gets skipped: sponsors, self promotion, "like and subscribe" bits, intros and outros. */
    val SKIP_CATEGORIES = listOf("sponsor", "selfpromo", "interaction", "intro", "outro", "music_offtopic")

    /**
     * SponsorBlock segments to skip. Asks by the first 4 characters of the id's SHA-256 (as
     * LibreTube does) so the server never learns which video is being watched.
     */
    suspend fun sponsorSegments(videoId: String): List<SkipSegment> {
        val prefix = sha256(videoId).take(4)
        val categories = URLEncoder.encode(JsonArray(SKIP_CATEGORIES.map { kotlinx.serialization.json.JsonPrimitive(it) }).toString(), "UTF-8")
        val actions = URLEncoder.encode("[\"skip\"]", "UTF-8")
        val body =
            VideoHttp.getOrNull("https://sponsor.ajay.app/api/skipSegments/$prefix?categories=$categories&actionTypes=$actions")
                ?: return emptyList()
        return runCatching {
            VideoHttp.json
                .parseToJsonElement(body)
                .jsonArray
                .map { it.jsonObject }
                .firstOrNull { it["videoID"]?.jsonPrimitive?.contentOrNull == videoId }
                ?.get("segments")
                ?.jsonArray
                ?.mapNotNull { element ->
                    val segment = element.jsonObject
                    val range = segment["segment"]?.jsonArray ?: return@mapNotNull null
                    val start = range.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    val end = range.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                    if (end - start < 0.5) return@mapNotNull null
                    SkipSegment(
                        startMs = (start * 1000).toLong(),
                        endMs = (end * 1000).toLong(),
                        category = segment["category"]?.jsonPrimitive?.contentOrNull ?: "sponsor",
                    )
                }.orEmpty()
                .sortedBy { it.startMs }
        }.getOrDefault(emptyList())
    }

    /** Dislike count from Return YouTube Dislike, or -1 when it cannot be read. */
    suspend fun dislikes(videoId: String): Long {
        val body = VideoHttp.getOrNull("https://returnyoutubedislikeapi.com/votes?videoId=$videoId") ?: return -1
        return runCatching {
            (VideoHttp.json.parseToJsonElement(body) as JsonObject)["dislikes"]?.jsonPrimitive?.longOrNull ?: -1
        }.getOrDefault(-1)
    }

    private fun sha256(text: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
