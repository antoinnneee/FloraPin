package com.florapin.app.network.api

import com.florapin.app.network.dto.PaginatedSpeciesDto
import com.florapin.app.network.dto.SpeciesDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Encyclopédie des espèces (NODE-125) : liste, autocomplétion, fiche. */
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

    @GET("species/{id}")
    suspend fun get(@Path("id") id: String): SpeciesDto
}
