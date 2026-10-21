package com.doomanity.cooley.video

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The few plain HTTP calls the video side makes (BotGuard, SponsorBlock, Return YouTube Dislike). */
object VideoHttp {
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"

    /** Same key LibreTube and NewPipe send to YouTube's BotGuard service. */
    private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"

    val client: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    /** POSTs [data] as a JSON array to BotGuard's [url] and returns the raw response body. */
    suspend fun botguardRequest(
        url: String,
        data: List<String>,
    ): String =
        withContext(Dispatchers.IO) {
            val body = JsonArray(data.map { JsonPrimitive(it) }).toString()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .post(body.toRequestBody("application/json+protobuf".toMediaType()))
                    .header("Accept", "application/json")
                    .header("x-goog-api-key", GOOGLE_API_KEY)
                    .header("x-user-agent", "grpc-web-javascript/0.1")
                    .header("User-Agent", USER_AGENT)
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("BotGuard request failed: ${response.code}")
                response.body.string()
            }
        }

    /** GETs [url] and returns the body, or null on any failure. */
    suspend fun getOrNull(url: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body.string() else null
                }
            }.getOrNull()
        }
}
