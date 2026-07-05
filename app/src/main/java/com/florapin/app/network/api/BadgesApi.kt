package com.florapin.app.network.api

import com.florapin.app.network.dto.EntraideBadgeCountsDto
import retrofit2.http.GET

/**
 * Badges « entraide » calculés côté serveur (TÂCHE 5.4). Le backend
 * (`badges.controller.ts`) agrège les compteurs collaboratifs (amis, propositions,
 * demandes, commentaires, réactions) à la volée. Comme le feed et les
 * commentaires, ces données ne sont accessibles qu'en ligne et authentifié
 * (indépendantes de la sync device-first) : hors-ligne, l'app affiche la dernière
 * valeur connue ou grise les badges concernés.
 */
interface BadgesApi {
    /** Compteurs d'entraide de l'utilisateur courant. */
    @GET("me/badges")
    suspend fun counts(): EntraideBadgeCountsDto
}
