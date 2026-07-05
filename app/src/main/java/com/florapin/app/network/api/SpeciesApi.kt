package com.florapin.app.network.api

import com.florapin.app.network.dto.HerbierDto
import com.florapin.app.network.dto.PaginatedSpeciesDto
import com.florapin.app.network.dto.SpeciesDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Encyclopédie des espèces (NODE-125) : liste, autocomplétion, fiche, herbier. */
interface SpeciesApi {
    @GET("species")
    suspend fun list(
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): PaginatedSpeciesDto

    @GET("species/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int? = null,
    ): List<SpeciesDto>

    /** Herbier de l'utilisateur courant : espèces distinctes par famille (TÂCHE 5.6). */
    @GET("species/herbier")
    suspend fun herbier(): HerbierDto

    @GET("species/{id}")
    suspend fun get(@Path("id") id: String): SpeciesDto
}
