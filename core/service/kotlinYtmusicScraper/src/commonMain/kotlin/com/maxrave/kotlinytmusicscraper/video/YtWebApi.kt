package com.maxrave.kotlinytmusicscraper.video

import com.maxrave.domain.data.model.video.VideoChannelTab
import com.maxrave.domain.data.model.video.VideoFeedItem
import com.maxrave.domain.data.model.video.VideoPage
import com.maxrave.domain.data.model.video.VideoSearchFilter
import com.maxrave.domain.extension.now
import com.maxrave.kotlinytmusicscraper.utils.parseCookieString
import com.maxrave.kotlinytmusicscraper.utils.sha1
import com.maxrave.ktorext.encoding.brotli
import com.maxrave.ktorext.getEngine
import com.maxrave.logger.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.ExperimentalTime

private const val TAG = "YtWebApi"

/** Who is asking. Read fresh on every call, so a sign-in or a language change applies at once. */
class WebSession(
    val cookie: String?,
    val visitorData: String?,
    val authUser: Int,
    val pageId: String?,
    val hl: String,
    val gl: String,
    val proxy: ProxyConfig?,
)

class VideoApiException(
    val status: Int,
    message: String,
) : Exception(message)

/**
 * YouTube's own website API (`www.youtube.com/youtubei/v1`), the same one the web player uses.
 *
 * Reliefy's music side talks to music.youtube.com; the video side needs the main site, which
 * answers with videos, Shorts, channels and playlists. Nothing here ever asks for or shows ads: the
 * parser drops promoted items by name.
 *
 * Paging: every page carries a `continuation`. It is a plain string with a one-letter prefix that
 * says which endpoint it belongs to (`b` browse, `s` search, `n` next, `t` the topic mix), so the
 * caller only ever hands it back to [more].
 */
@OptIn(ExperimentalTime::class)
class YtWebApi(
    private val session: () -> WebSession,
) {
    private class Config(
        val key: String,
        val version: String,
        val visitorData: String?,
        val signedIn: Boolean,
    )

    private var client: HttpClient? = null
    private var clientProxy: ProxyConfig? = null
    private var config: Config? = null
    private val configMutex = Mutex()

    // ----- public calls -----

    suspend fun home(): VideoPage {
        if (session().cookie != null) {
            val page = prefixed("b", parseBrowse("FEwhat_to_watch"))
            if (page.items.any { it is VideoFeedItem.Video }) return page
            Logger.d(TAG, "personal home came back empty, using the topic mix")
        }
        return topicHome(0)
    }

    suspend fun search(
        query: String,
        filter: VideoSearchFilter,
    ): VideoPage = prefixed("s", rawSearch(query, filter.params))

    suspend fun suggestions(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        return softly(emptyList()) {
            val s = session()
            val raw =
                http().get("https://suggestqueries.google.com/complete/search") {
                    parameter("client", "firefox")
                    parameter("ds", "yt")
                    parameter("hl", s.hl.ifBlank { "en" })
                    parameter("q", query)
                    header(HttpHeaders.UserAgent, USER_AGENT)
                }.bodyAsText()
            YtVideoFeedParser.parseSuggestions(raw)
        }
    }

    suspend fun channel(
        channelId: String,
        tab: VideoChannelTab,
    ): VideoPage = prefixed("b", parseBrowse(channelId, tab.params))

    suspend fun playlist(playlistId: String): VideoPage {
        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        return prefixed("b", parseBrowse(browseId))
    }

    suspend fun related(videoId: String): VideoPage {
        val response = post("next") { put("videoId", videoId) }
        return prefixed("n", YtVideoFeedParser.parseRelated(response))
    }

    /**
     * Comments of [videoId]. [continuation] is null for the first page. A watch page without a
     * comment section (comments turned off, or a video made for kids) answers a disabled page.
     */
    suspend fun comments(
        videoId: String,
        continuation: String?,
    ): com.maxrave.domain.mediaservice.video.VideoCommentsPage {
        val token =
            continuation
                ?: YtCommentsParser.firstPageToken(post("next") { put("videoId", videoId) })
                ?: return com.maxrave.domain.mediaservice.video.VideoCommentsPage(emptyList(), disabled = true)
        return YtCommentsParser.parsePage(post("next") { put("continuation", token) })
    }

    suspend fun more(continuation: String): VideoPage {
        val sep = continuation.indexOf(':')
        if (sep < 1) return VideoPage(emptyList(), null)
        val kind = continuation.substring(0, sep)
        val token = continuation.substring(sep + 1)
        return when (kind) {
            "b" -> prefixed("b", YtVideoFeedParser.parsePage(post("browse") { put("continuation", token) }))
            "s" -> prefixed("s", YtVideoFeedParser.parsePage(post("search") { put("continuation", token) }))
            "n" -> prefixed("n", YtVideoFeedParser.parseRelated(post("next") { put("continuation", token) }))
            "t" -> topicHome(token.toIntOrNull() ?: HOME_TOPICS.size)
            else -> VideoPage(emptyList(), null)
        }
    }

    // ----- pieces -----

    private suspend fun parseBrowse(
        browseId: String,
        params: String? = null,
    ): VideoPage {
        val response =
            post("browse") {
                put("browseId", browseId)
                params?.let { put("params", it) }
            }
        return YtVideoFeedParser.parsePage(response)
    }

    private suspend fun rawSearch(
        query: String,
        params: String?,
    ): VideoPage {
        val response =
            post("search") {
                put("query", query)
                params?.let { put("params", it) }
            }
        return YtVideoFeedParser.parsePage(response)
    }

    /**
     * What the home page shows without an account: a few popular topics searched side by side and
     * dealt out one video at a time, so the first screen is not six videos about the same thing.
     * YouTube's own home is empty for a visitor, which is why this exists.
     */
    private suspend fun topicHome(offset: Int): VideoPage {
        val topics = HOME_TOPICS.drop(offset).take(TOPICS_PER_PAGE)
        if (topics.isEmpty()) return VideoPage(emptyList(), null)
        val lists =
            coroutineScope {
                topics
                    .map { topic ->
                        async {
                            softly(emptyList<VideoFeedItem>()) {
                                rawSearch(topic, VideoSearchFilter.VIDEOS.params)
                                    .items
                                    .filter { it is VideoFeedItem.Video && !it.video.isShort }
                                    .take(PER_TOPIC)
                            }
                        }
                    }.awaitAll()
            }
        val mixed = ArrayList<VideoFeedItem>()
        val seen = HashSet<String>()
        for (round in 0 until PER_TOPIC) {
            for (list in lists) {
                val item = list.getOrNull(round) as? VideoFeedItem.Video ?: continue
                if (seen.add(item.video.videoId)) mixed.add(item)
            }
        }
        val next = offset + TOPICS_PER_PAGE
        return VideoPage(
            items = mixed,
            continuation = if (next < HOME_TOPICS.size && mixed.isNotEmpty()) "t:$next" else null,
        )
    }

    private fun prefixed(
        kind: String,
        page: VideoPage,
    ): VideoPage = if (page.continuation == null) page else page.copy(continuation = "$kind:${page.continuation}")

    // ----- transport -----

    private suspend fun post(
        endpoint: String,
        fill: JsonObjectBuilder.() -> Unit,
    ): JsonElement {
        val s = session()
        val cfg = config()
        val body =
            buildJsonObject {
                put(
                    "context",
                    buildJsonObject {
                        put(
                            "client",
                            buildJsonObject {
                                put("clientName", "WEB")
                                put("clientVersion", cfg.version)
                                put("hl", s.hl.ifBlank { "en" })
                                put("gl", s.gl.ifBlank { "US" })
                                cfg.visitorData?.let { put("visitorData", it) }
                                put("userAgent", USER_AGENT)
                                put("platform", "DESKTOP")
                            },
                        )
                        put("user", buildJsonObject { put("lockedSafetyMode", false) })
                        put("request", buildJsonObject { put("useSsl", true) })
                    },
                )
                fill()
            }
        val response =
            http().post("$ORIGIN/youtubei/v1/$endpoint") {
                parameter("key", cfg.key)
                parameter("prettyPrint", "false")
                applyHeaders(s, cfg)
                setBody(TextContent(body.toString(), ContentType.Application.Json))
            }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            Logger.w(TAG, "$endpoint answered ${response.status.value}")
            if (response.status.value == 401 || response.status.value == 403) {
                Logger.w("Auth", "YouTube: www.youtube.com/youtubei/v1/$endpoint answered ${response.status.value}")
            }
            throw VideoApiException(response.status.value, "YouTube answered ${response.status.value}")
        }
        return Json.parseToJsonElement(text)
    }

    private fun HttpRequestBuilder.applyHeaders(
        s: WebSession,
        cfg: Config,
    ) {
        header("Origin", ORIGIN)
        header("X-Origin", ORIGIN)
        header("Referer", "$ORIGIN/")
        header("X-YouTube-Client-Name", "1")
        header("X-YouTube-Client-Version", cfg.version)
        cfg.visitorData?.let { header("X-Goog-Visitor-Id", it) }
        header(HttpHeaders.UserAgent, USER_AGENT)
        header(HttpHeaders.AcceptLanguage, "${s.hl.ifBlank { "en" }},en;q=0.8")
        val cookie = s.cookie
        if (cookie != null) {
            header("Cookie", cookie)
            header("X-Goog-AuthUser", s.authUser.toString())
            s.pageId?.let { header("X-Goog-PageId", it) }
            authorization(cookie)?.let { header("Authorization", it) }
        } else {
            header("Cookie", "SOCS=CAI; PREF=hl=${s.hl.ifBlank { "en" }}&gl=${s.gl.ifBlank { "US" }}")
        }
    }

    /** `SAPISIDHASH`, computed for www.youtube.com. The music side signs for music.youtube.com. */
    private fun authorization(cookie: String): String? {
        val map = parseCookieString(cookie)
        val sapisid = map["SAPISID"] ?: map["__Secure-3PAPISID"] ?: return null
        val seconds = now().toInstant(TimeZone.currentSystemDefault()).epochSeconds
        return "SAPISIDHASH ${seconds}_${sha1("$seconds $sapisid $ORIGIN")}"
    }

    private fun http(): HttpClient {
        val proxy = session().proxy
        val existing = client
        if (existing != null && clientProxy == proxy) return existing
        existing?.close()
        return HttpClient(getEngine()) {
            expectSuccess = false
            install(ContentEncoding) {
                brotli(1.0F)
                gzip(0.9F)
                deflate(0.8F)
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 40_000
                socketTimeoutMillis = 40_000
            }
            if (proxy != null) {
                engine { this.proxy = proxy }
            }
        }.also {
            client = it
            clientProxy = proxy
        }
    }

    /**
     * The client version and visitor id YouTube's own page hands out. Read from the home page once
     * (again after a sign-in or sign-out), so the version never goes stale in an old build; the
     * values baked in below are only the fallback when that page cannot be read.
     */
    private suspend fun config(): Config {
        val signedIn = session().cookie != null
        config?.takeIf { it.signedIn == signedIn }?.let { return it }
        return configMutex.withLock {
            config?.takeIf { it.signedIn == signedIn } ?: loadConfig(signedIn).also { config = it }
        }
    }

    private suspend fun loadConfig(signedIn: Boolean): Config {
        val s = session()
        val fallback = Config(DEFAULT_KEY, DEFAULT_VERSION, s.visitorData, signedIn)
        val loaded =
            withTimeoutOrNull(12_000) {
                try {
                    val html =
                        http()
                            .get("$ORIGIN/") {
                                header(HttpHeaders.UserAgent, USER_AGENT)
                                header(HttpHeaders.AcceptLanguage, "${s.hl.ifBlank { "en" }},en;q=0.8")
                                header("Cookie", s.cookie ?: "SOCS=CAI; PREF=hl=${s.hl.ifBlank { "en" }}&gl=${s.gl.ifBlank { "US" }}")
                            }.bodyAsText()
                    val key = KEY_REGEX.find(html)?.groupValues?.get(1)
                    val version = VERSION_REGEX.find(html)?.groupValues?.get(1)
                    val visitor = VISITOR_REGEX.find(html)?.groupValues?.get(1)
                    if (key != null && version != null) Config(key, version, visitor ?: s.visitorData, signedIn) else null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.w(TAG, "could not read the YouTube page for its client version: ${e.message}")
                    null
                }
            }
        if (loaded == null) Logger.w(TAG, "using the built-in client version")
        return loaded ?: fallback
    }

    private suspend fun <T> softly(
        default: T,
        block: suspend () -> T,
    ): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(TAG, "request failed: ${e.message}")
            default
        }

    private companion object {
        const val ORIGIN = "https://www.youtube.com"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
        const val DEFAULT_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        const val DEFAULT_VERSION = "2.20261020.05.00"

        val KEY_REGEX = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"")
        val VERSION_REGEX = Regex("\"INNERTUBE_CONTEXT_CLIENT_VERSION\":\"([^\"]+)\"")
        val VISITOR_REGEX = Regex("\"VISITOR_DATA\":\"([^\"]+)\"")

        const val TOPICS_PER_PAGE = 6
        const val PER_TOPIC = 8
        val HOME_TOPICS =
            listOf(
                "trending today",
                "new music videos",
                "gaming highlights",
                "news today",
                "movie trailers",
                "sports highlights",
                "easy recipes",
                "tech reviews",
                "funny moments",
                "science explained",
                "travel vlog",
                "documentary",
            )
    }
}
