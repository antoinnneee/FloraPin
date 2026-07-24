package com.florapin.app.gallery

import com.florapin.app.network.dto.NotificationDto
import org.junit.Assert.assertEquals
import org.junit.Test

class UnreadCommentNotificationsTest {

    @Test
    fun `groups unread comment notifications by flower`() {
        val notifications = listOf(
            notification("n1", "flower_commented", "flower-1"),
            notification("n2", "comment_mention", "flower-1"),
            notification("n3", "flower_commented", "flower-2"),
        )

        assertEquals(
            mapOf(
                "flower-1" to listOf("n1", "n2"),
                "flower-2" to listOf("n3"),
            ),
            notifications.unreadCommentNotificationIdsByFlower(),
        )
    }

    @Test
    fun `ignores read unrelated and unbound notifications`() {
        val notifications = listOf(
            notification("read", "flower_commented", "flower-1", readAt = "2026-07-24"),
            notification("like", "flower_liked", "flower-1"),
            notification("missing-flower", "comment_mention", null),
        )

        assertEquals(
            emptyMap<String, List<String>>(),
            notifications.unreadCommentNotificationIdsByFlower(),
        )
    }

    private fun notification(
        id: String,
        type: String,
        flowerId: String?,
        readAt: String? = null,
    ) = NotificationDto(
        id = id,
        type = type,
        data = mapOf("flowerId" to flowerId),
        readAt = readAt,
        createdAt = "2026-07-24T12:00:00Z",
    )
}
