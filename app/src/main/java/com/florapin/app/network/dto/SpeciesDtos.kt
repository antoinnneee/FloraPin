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

// --- Herbier / stats de collection (TÂCHE 5.6) ---

/** Une espèce de l'herbier (GET /species/herbier). */
@JsonClass(generateAdapter = true)
data class HerbierSpeciesDto(
    /** Id du référentiel, ou null pour une espèce en texte libre non rapprochée. */
    val id: String? = null,
    val scientificName: String = "",
    val commonName: String = "",
    val emoji: String? = null,
    val flowerCount: Int = 0,
)

/** Regroupement de l'herbier par famille botanique. */
@JsonClass(generateAdapter = true)
data class HerbierFamilyDto(
    val family: String = "",
    val speciesCount: Int = 0,
    val flowerCount: Int = 0,
    val species: List<HerbierSpeciesDto> = emptyList(),
)

/**
 * Herbier de l'utilisateur (GET /species/herbier, TÂCHE 5.6) : espèces distinctes
 * regroupées par famille. Le regroupement par famille vit côté serveur (la famille
 * est portée par l'espèce), d'où un volet familles partiel hors-ligne (device-first).
 *
 * Tous les champs ont une valeur par défaut : un serveur plus ancien (ou une
 * réponse partielle) ne fait pas planter le parsing.
 */
@JsonClass(generateAdapter = true)
data class HerbierDto(
    val distinctSpecies: Int = 0,
    val totalFlowers: Int = 0,
    val familyCount: Int = 0,
    val families: List<HerbierFamilyDto> = emptyList(),
)
