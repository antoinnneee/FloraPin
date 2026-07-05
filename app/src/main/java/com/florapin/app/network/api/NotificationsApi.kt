package com.florapin.app.network.api

import com.florapin.app.network.dto.NotificationDto
import com.florapin.app.network.dto.UnreadCountDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Centre de notifications in-app (TÂCHE 2.7). Le backend
 * (`notifications.controller.ts`) expose la liste, le compteur de non-lus et le
 * marquage « lu ». Ces données collaboratives vivent côté serveur : l'API n'est
 * accessible qu'en ligne et authentifié (indépendante de la sync device-first,
 * comme le feed et les commentaires).
 */
interface NotificationsApi {
    /** Les 100 notifications les plus récentes, plus récentes d'abord. */
    @GET("notifications")
    suspend fun list(): List<NotificationDto>

    /** Nombre de notifications non lues (pour le badge de la cloche). */
    @GET("notifications/unread-count")
    suspend fun unreadCount(): UnreadCountDto

    /** Marque une notification comme lue (idempotent côté serveur). */
    @POST("notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String): NotificationDto
}
