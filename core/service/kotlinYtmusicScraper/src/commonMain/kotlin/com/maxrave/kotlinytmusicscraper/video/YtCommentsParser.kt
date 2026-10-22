package com.maxrave.kotlinytmusicscraper.video

import com.maxrave.domain.mediaservice.video.VideoComment
import com.maxrave.domain.mediaservice.video.VideoCommentsPage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Reads comments from YouTube's website responses. A comment thread only holds keys now; the text,
 * author and counts live in `frameworkUpdates.entityBatchUpdate.mutations` as entity payloads.
 */
object YtCommentsParser {
    /** The token that loads the first page of comments, from a watch page (`next`) response. */
    fun firstPageToken(next: JsonElement): String? {
        val section = findSection(next) ?: return null
        return section.findFirst("continuationCommand").child("token").asStr()
    }

    private fun findSection(node: JsonElement?): JsonElement? {
        when (node) {
            is JsonObject -> {
                if (node["sectionIdentifier"].asStr() == "comment-item-section") return node
                for ((_, v) in node) findSection(v)?.let { return it }
            }
            is kotlinx.serialization.json.JsonArray -> for (v in node) findSection(v)?.let { return it }
            else -> {}
        }
        return null
    }

    fun parsePage(response: JsonElement): VideoCommentsPage {
        val entities = HashMap<String, JsonObject>()
        val hearted = HashSet<String>()
        response
            .at("frameworkUpdates", "entityBatchUpdate", "mutations")
            .asArr()
            ?.forEach { mutation ->
                val payload = mutation.child("payload").asObj() ?: return@forEach
                payload["commentEntityPayload"].asObj()?.let { e ->
                    (e["key"].asStr() ?: mutation.child("entityKey").asStr())?.let { entities[it] = e }
                }
                payload["engagementToolbarStateEntityPayload"].asObj()?.let { e ->
                    if (e["heartState"].asStr() == "TOOLBAR_HEART_STATE_HEARTED") e["key"].asStr()?.let(hearted::add)
                }
            }

        val comments = ArrayList<VideoComment>()
        var total = -1L
        var next: String? = null
        response.child("onResponseReceivedEndpoints").asArr()?.forEach { endpoint ->
            val action =
                endpoint.child("reloadContinuationItemsCommand") ?: endpoint.child("appendContinuationItemsAction")
            action.child("continuationItems").asArr()?.forEach { item ->
                item.child("commentsHeaderRenderer")?.let { header ->
                    parseCount(header.child("countText").text())?.let { total = it }
                }
                item.child("commentThreadRenderer")?.let { thread ->
                    val vm = thread.at("commentViewModel", "commentViewModel")
                    val key = vm.child("commentKey").asStr()
                    val entity = key?.let { entities[it] }
                    if (entity != null) {
                        val toolbarKey = vm.child("toolbarStateKey").asStr()
                        comments += entity.toComment(
                            pinned = vm.child("pinnedText") != null,
                            hearted = toolbarKey != null && toolbarKey in hearted,
                        )
                    }
                }
                item.at("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token").asStr()?.let {
                    next = it
                }
                // the "show more" button form of the same thing
                item.at("continuationItemRenderer", "button", "buttonRenderer", "command", "continuationCommand", "token").asStr()?.let {
                    next = it
                }
            }
        }
        return VideoCommentsPage(comments = comments, nextPage = next, totalCount = total)
    }

    private fun JsonObject.toComment(
        pinned: Boolean,
        hearted: Boolean,
    ): VideoComment {
        val props = this["properties"]
        val author = this["author"]
        val toolbar = this["toolbar"]
        return VideoComment(
            id = props.child("commentId").asStr() ?: this["key"].asStr().orEmpty(),
            author = author.child("displayName").asStr().orEmpty(),
            authorAvatarUrl = fixUrl(author.child("avatarThumbnailUrl").asStr()),
            text = props.at("content", "content").asStr().orEmpty(),
            publishedText = props.child("publishedTime").asStr(),
            likeCount = parseCount(toolbar.child("likeCountNotliked").asStr()) ?: 0,
            replyCount = (parseCount(toolbar.child("replyCount").asStr()) ?: 0).toInt(),
            isPinned = pinned,
            isHeartedByUploader = hearted,
            isByUploader = author.child("isCreator").asBool() ?: false,
        )
    }

    /** "2,460,229" -> 2460229, "568K" -> 568000, "1.2M" -> 1200000, "" -> null. */
    fun parseCount(text: String?): Long? {
        val t = text?.trim()?.replace(" ", " ") ?: return null
        val m = Regex("""([\d.,]+)\s*([KkMmBb])?""").find(t) ?: return null
        val number = m.groupValues[1]
        val suffix = m.groupValues[2].uppercase()
        return if (suffix.isEmpty()) {
            number.filter { it.isDigit() }.toLongOrNull()
        } else {
            val value = number.replace(",", ".").toDoubleOrNull() ?: return null
            val mult =
                when (suffix) {
                    "K" -> 1_000.0
                    "M" -> 1_000_000.0
                    else -> 1_000_000_000.0
                }
            (value * mult).toLong()
        }
    }
}
