package com.maxrave.kotlinytmusicscraper.video

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Small helpers for reading YouTube's web JSON without a data class per renderer.
 *
 * YouTube reshuffles these trees all the time (old renderers get replaced by "view models" and
 * back), so the video side reads them by shape and never throws: every helper answers null when
 * the thing it looks for is not there, and the parsers skip an item they cannot read.
 */

internal fun JsonElement?.asObj(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArr(): JsonArray? = this as? JsonArray

/** The text of a primitive, or null for anything else (including JSON null). */
internal fun JsonElement?.asStr(): String? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.content
}

internal fun JsonElement?.asBool(): Boolean? = asStr()?.toBooleanStrictOrNull()

/** One step down: [key] on an object, null on anything else. */
internal fun JsonElement?.child(key: String): JsonElement? = (this as? JsonObject)?.get(key)

/**
 * Follows [steps] down the tree. A String step is an object key, an Int step is an array index.
 * Returns null the moment a step does not fit.
 */
internal fun JsonElement?.at(vararg steps: Any): JsonElement? {
    var cur: JsonElement? = this
    for (step in steps) {
        cur =
            when (step) {
                is String -> (cur as? JsonObject)?.get(step)
                is Int -> (cur as? JsonArray)?.getOrNull(step)
                else -> null
            }
        if (cur == null) return null
    }
    return cur
}

/**
 * The readable text of any of YouTube's text shapes: `{"simpleText": ...}`, `{"runs": [...]}` and
 * the view-model form `{"content": ...}`. A bare string is returned as it is.
 */
internal fun JsonElement?.text(): String? {
    if (this == null) return null
    if (this is JsonPrimitive) return asStr()
    val obj = this as? JsonObject ?: return null
    obj["simpleText"].asStr()?.let { return it }
    obj["content"].asStr()?.let { return it }
    val runs = obj["runs"].asArr()
    if (runs != null) {
        val joined = runs.joinToString("") { it.child("text").asStr().orEmpty() }
        return joined.ifEmpty { null }
    }
    return null
}

/** Depth-first search for the first value stored under [key], skipping the subtrees in [skip]. */
internal fun JsonElement?.findFirst(
    key: String,
    skip: Set<String> = emptySet(),
): JsonElement? {
    when (this) {
        is JsonObject -> {
            for ((k, v) in this) {
                if (k in skip) continue
                if (k == key) return v
                v.findFirst(key, skip)?.let { return it }
            }
        }

        is JsonArray -> {
            for (v in this) {
                v.findFirst(key, skip)?.let { return it }
            }
        }

        else -> {}
    }
    return null
}

/** Every value stored under [key] anywhere below, in document order. */
internal fun JsonElement?.findAll(
    key: String,
    skip: Set<String> = emptySet(),
    out: MutableList<JsonElement> = mutableListOf(),
): MutableList<JsonElement> {
    when (this) {
        is JsonObject -> {
            for ((k, v) in this) {
                if (k in skip) continue
                if (k == key) out.add(v)
                v.findAll(key, skip, out)
            }
        }

        is JsonArray -> {
            for (v in this) v.findAll(key, skip, out)
        }

        else -> {}
    }
    return out
}

/** Makes a protocol-relative image URL (`//yt3.ggpht.com/...`) usable. */
internal fun fixUrl(url: String?): String? =
    when {
        url.isNullOrBlank() -> null
        url.startsWith("//") -> "https:$url"
        else -> url
    }

/**
 * The widest image in a list of `{url, width, height}` entries (`thumbnails` on the old renderers,
 * `sources` on the view models). The widest one is the sharpest, and the cards scale it down.
 */
internal fun bestImage(list: JsonElement?): String? {
    val arr = list.asArr() ?: return null
    var bestUrl: String? = null
    var bestWidth = -1
    for (entry in arr) {
        val url = fixUrl(entry.child("url").asStr()) ?: continue
        val width = entry.child("width").asStr()?.toIntOrNull() ?: 0
        if (width >= bestWidth) {
            bestWidth = width
            bestUrl = url
        }
    }
    return bestUrl
}

/** The first image list (`thumbnails` or `sources`) found anywhere below this element. */
internal fun firstImage(
    el: JsonElement?,
    skip: Set<String> = IMAGE_SKIP,
): String? {
    val fromSources = bestImage(el.findFirst("sources", skip))
    if (fromSources != null) return fromSources
    return bestImage(el.findFirst("thumbnails", skip))
}

private val IMAGE_SKIP = setOf("menu", "avatar", "attachmentRuns")

/** Fallback thumbnail when a card carries none. 16:9, and present for every video. */
internal fun fallbackThumbnail(videoId: String): String = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
