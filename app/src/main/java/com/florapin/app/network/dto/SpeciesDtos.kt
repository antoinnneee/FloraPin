package com.florapin.app.network.dto

import com.squareup.moshi.JsonClass

/** DTOs du référentiel d'espèces (NODE-124/125), alignés sur l'API REST. */

/** Fiche d'espèce complète (encyclopédie). */
@JsonClass(generateAdapter = true)
data class SpeciesDto(
    val id: String,
    val scientificName: String,
    val commonName: String,
    val family: String,
    val description: String = "",
    val emoji: String? = null,
)

/** Espèce résolue rattachée à une fleur (forme allégée dans [FlowerDto]). */
@JsonClass(generateAdapter = true)
data class SpeciesRefDto(
    val id: String,
    val scientificName: String,
    val commonName: String,
)

/** Page de résultats de l'encyclopédie (GET /species). */
@JsonClass(generateAdapter = true)
data class PaginatedSpeciesDto(
    val items: List<SpeciesDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 50,
)
