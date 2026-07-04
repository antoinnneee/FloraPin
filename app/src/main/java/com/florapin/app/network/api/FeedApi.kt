package com.florapin.app.network.api

import com.florapin.app.network.dto.FlowerDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Flux d'amis (NODE-72/137) : réunit les partages ciblés et les fleurs publiées
 * par mes amis (visibility='friends'), déjà triées par date côté serveur.
 */
interface FeedApi {
    @GET("feed")
    suspend fun getFeed(
        @Query("since") since: String? = null,
        @Query("limit") limit: Int? = null,
        /** 'date' (défaut) ou 'likes' (meilleures photos, NODE-140). */
        @Query("sort") sort: String? = null,
        /**
         * Curseur de pagination keyset (TÂCHE 1.2), format `<createdAt>_<id>`
         * construit depuis la dernière fleur reçue. Réservé au tri par date :
         * incompatible avec sort=likes (400 côté serveur).
         */
        @Query("before") before: String? = null,
    ): List<FlowerDto>
}
