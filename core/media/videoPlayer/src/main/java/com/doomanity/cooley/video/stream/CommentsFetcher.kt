package com.doomanity.cooley.video.stream

import androidx.core.text.HtmlCompat
import com.maxrave.domain.mediaservice.video.VideoComment
import com.maxrave.domain.mediaservice.video.VideoCommentsPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Comments the way LibreTube reads them: NewPipe Extractor's comments, page by page. */
object CommentsFetcher {
    /** NewPipe pages are objects; screens hold a short token for each instead. */
    private val pages = ConcurrentHashMap<String, Page>()

    suspend fun load(
        videoId: String,
        pageToken: String?,
    ): VideoCommentsPage =
        withContext(Dispatchers.IO) {
            StreamFetcher.ensureReady()
            val url = "https://www.youtube.com/watch?v=$videoId"
            if (pageToken == null) {
                val info = CommentsInfo.getInfo(url)
                VideoCommentsPage(
                    comments = info.relatedItems.map { it.toComment() },
                    nextPage = info.nextPage?.let { remember(it) },
                    totalCount = info.commentsCount.toLong().takeIf { it > 0 } ?: -1,
                    disabled = info.isCommentsDisabled,
                )
            } else {
                val page = pages.remove(pageToken) ?: return@withContext VideoCommentsPage(emptyList())
                val more = CommentsInfo.getMoreItems(ServiceList.YouTube, url, page)
                VideoCommentsPage(
                    comments = more.items.map { it.toComment() },
                    nextPage = more.nextPage?.let { remember(it) },
                )
            }
        }

    private fun remember(page: Page): String {
        if (pages.size > 200) pages.clear()
        val token = UUID.randomUUID().toString()
        pages[token] = page
        return token
    }
}

private fun CommentsInfoItem.toComment(): VideoComment =
    VideoComment(
        id = commentId ?: UUID.randomUUID().toString(),
        author = uploaderName.orEmpty(),
        authorAvatarUrl = uploaderAvatars.maxByOrNull { it.height }?.url,
        text =
            commentText.let {
                if (it?.type == Description.Type.HTML) {
                    HtmlCompat.fromHtml(it.content, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
                } else {
                    it?.content.orEmpty().trim()
                }
            },
        publishedText = textualUploadDate,
        likeCount = likeCount.toLong().coerceAtLeast(0),
        replyCount = replyCount.coerceAtLeast(0),
        isPinned = isPinned,
        isHeartedByUploader = isHeartedByUploader,
        isByUploader = isChannelOwner,
    )

/** "3 months ago" from a date. Used where YouTube only gives a full date. */
fun relativeTime(date: OffsetDateTime): String {
    val d = Duration.between(date, OffsetDateTime.now())
    val minutes = d.toMinutes().coerceAtLeast(0)
    fun unit(
        n: Long,
        name: String,
    ) = if (n == 1L) "1 $name ago" else "$n ${name}s ago"
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> unit(minutes, "minute")
        minutes < 60 * 24 -> unit(minutes / 60, "hour")
        minutes < 60 * 24 * 7 -> unit(minutes / (60 * 24), "day")
        minutes < 60 * 24 * 30 -> unit(minutes / (60 * 24 * 7), "week")
        minutes < 60 * 24 * 365 -> unit(minutes / (60 * 24 * 30), "month")
        else -> unit(minutes / (60 * 24 * 365), "year")
    }
}
