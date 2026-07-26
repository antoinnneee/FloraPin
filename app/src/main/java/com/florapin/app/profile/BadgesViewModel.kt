package com.florapin.app.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.badges.BadgeCalculator
import com.florapin.app.badges.BadgeCatalog
import com.florapin.app.badges.BadgeDef
import com.florapin.app.badges.BadgeSource
import com.florapin.app.data.BadgeEntity
import com.florapin.app.data.BadgeRepository
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.BadgesApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.EntraideBadgeCountsDto
import com.florapin.app.ui.components.BadgeUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de l'onglet Badges (TÂCHE 5.5). */
data class BadgesUiState(
    val loading: Boolean = true,
    /** Familles « collection » (calcul local). */
    val collection: List<BadgeUiState> = emptyList(),
    /** Familles géographiques de la section « Pays » (calcul local). */
    val countries: List<BadgeUiState> = emptyList(),
    /** Familles « entraide » (compteurs serveur, grisées hors-ligne). */
    val entraide: List<BadgeUiState> = emptyList(),
    /** `false` quand les compteurs serveur n'ont pas pu être chargés. */
    val entraideAvailable: Boolean = true,
    /** Nombre total d'étoiles gagnées (toutes familles). */
    val starsUnlocked: Int = 0,
    /** Nombre total d'étoiles possibles (toutes familles). */
    val starsTotal: Int = 0,
    /** Événement de célébration à consommer (haptique) : au moins un palier neuf. */
    val celebrate: Boolean = false,
)

/**
 * ViewModel de l'onglet Badges (TÂCHE 5.5) : fusionne les badges « collection »
 * et « pays » (calcul local, [BadgeRepository]) avec l'« entraide » (compteurs
 * serveur, [BadgesApi]) en listes d'états de cartes.
 *
 * Device-first : la collection s'affiche toujours (hors-ligne inclus) ; les
 * compteurs d'entraide, indisponibles hors-ligne, grisent alors leurs cartes
 * ([BadgesUiState.entraideAvailable] = `false`). Les paliers fraîchement débloqués
 * (non « vus ») déclenchent une célébration (haptique) puis sont marqués vus.
 */
class BadgesViewModel(
    private val repository: BadgeRepository,
    private val badgesApi: BadgesApi,
) : ViewModel() {

    private val _state = MutableStateFlow(BadgesUiState())
    val state: StateFlow<BadgesUiState> = _state.asStateFlow()

    init {
        load()
    }

    /**
     * (Re)charge la grille : recalcule les badges locaux, lit la progression brute,
     * repère les paliers neufs à célébrer, puis tente les compteurs serveur.
     */
    fun load() {
        viewModelScope.launch {
            // Collection locale (toujours disponible).
            repository.recompute()
            val progress = repository.currentProgress()
            val freshIds = repository.unseen().mapTo(mutableSetOf()) { familyIdOf(it) }
            val collection = buildCollectionBadges(progress, freshIds)
            val countries = buildCountryBadges(progress, freshIds)
            val celebrate = freshIds.isNotEmpty()
            if (celebrate) repository.markAllSeen()

            _state.update {
                it.copy(
                    loading = false,
                    collection = collection,
                    countries = countries,
                    celebrate = it.celebrate || celebrate,
                )
            }
            recomputeTotals()

            // Entraide serveur (best-effort : grisé hors-ligne).
            val counts = runCatching { badgesApi.counts() }.getOrNull()
            _state.update {
                it.copy(
                    entraide = buildEntraideBadges(counts),
                    entraideAvailable = counts != null,
                )
            }
            recomputeTotals()
        }
    }

    /** Consomme l'événement de célébration (après le retour haptique). */
    fun celebrationConsumed() {
        _state.update { it.copy(celebrate = false) }
    }

    private fun recomputeTotals() {
        _state.update { s ->
            val all = s.collection + s.countries + s.entraide
            s.copy(
                starsUnlocked = all.sumOf { it.unlockedTiers },
                starsTotal = all.sumOf { it.tiers.size },
            )
        }
    }

    companion object {
        /** Factory câblant le dépôt de badges local + le client serveur authentifié. */
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val apis = NetworkModule.createAuthenticated(
                        EncryptedTokenStore(context.applicationContext),
                    )
                    return BadgesViewModel(
                        BadgeRepository.from(context),
                        apis.badges,
                    ) as T
                }
            }
    }
}

/**
 * Famille d'affichage d'un palier local débloqué : replie les saisons et les
 * badges outre-mer paramétrés sur leurs familles agrégées du catalogue.
 */
internal fun familyIdOf(badge: BadgeEntity): String = when {
    badge.badgeId.startsWith(BadgeCalculator.OUTRE_MER_PREFIX) -> BadgeCatalog.OUTRE_MER
    badge.badgeId in SEASON_BADGE_IDS -> BadgeCatalog.SAISONS
    else -> badge.badgeId
}

private val SEASON_BADGE_IDS = setOf(
    BadgeCalculator.SAISON_PRINTEMPS,
    BadgeCalculator.SAISON_ETE,
    BadgeCalculator.SAISON_AUTOMNE,
    BadgeCalculator.SAISON_HIVER,
    BadgeCalculator.QUATRE_SAISONS,
)

/**
 * Construit les cartes « collection » à partir de la progression brute (fonction
 * pure, testable sans Android). [freshIds] contient les familles avec un palier
 * neuf (liseré de célébration).
 */
internal fun buildCollectionBadges(
    progress: BadgeCalculator.Progress,
    freshIds: Set<String> = emptySet(),
): List<BadgeUiState> = buildLocalBadges(BadgeCatalog.COLLECTION, progress, freshIds)

/** Construit les cartes de la section « Pays » à partir de la progression locale. */
internal fun buildCountryBadges(
    progress: BadgeCalculator.Progress,
    freshIds: Set<String> = emptySet(),
): List<BadgeUiState> = buildLocalBadges(BadgeCatalog.COUNTRIES, progress, freshIds)

private fun buildLocalBadges(
    definitions: List<BadgeDef>,
    progress: BadgeCalculator.Progress,
    freshIds: Set<String>,
): List<BadgeUiState> = definitions.map { def ->
    val value = collectionValueOf(def.id, progress)
    // Valeur < 0 → compteur indisponible (résolveur de régions absent) : grisé.
    def.toUiState(
        currentValue = value.coerceAtLeast(0),
        available = value >= 0,
        isNew = def.id in freshIds,
    )
}

/**
 * Construit les cartes « entraide » à partir des compteurs serveur, ou une
 * version grisée ([BadgeUiState.available] = `false`) quand [counts] est `null`
 * (hors-ligne / non connecté).
 */
internal fun buildEntraideBadges(counts: EntraideBadgeCountsDto?): List<BadgeUiState> =
    BadgeCatalog.ENTRAIDE.map { def ->
        def.toUiState(
            currentValue = counts?.let { entraideValueOf(def.id, it) } ?: 0,
            available = counts != null,
        )
    }

private fun BadgeDef.toUiState(
    currentValue: Int,
    available: Boolean,
    isNew: Boolean = false,
): BadgeUiState = BadgeUiState(
    id = id,
    emoji = emoji,
    title = label,
    description = description,
    tiers = tiers,
    currentValue = currentValue,
    available = available,
    isNew = isNew,
)

/** Numérateur courant d'une famille « collection » (négatif = indisponible). */
private fun collectionValueOf(id: String, p: BadgeCalculator.Progress): Int = when (id) {
    BadgeCalculator.PREMIERE_FLEUR -> if (p.flowerCount >= 1) 1 else 0
    BadgeCalculator.HERBIER -> p.flowerCount
    BadgeCalculator.DIVERSITE -> p.distinctSpeciesCount
    BadgeCatalog.SAISONS -> p.seasonCount
    BadgeCalculator.FRANCE -> p.franceRegionCount // -1 si résolveur absent
    BadgeCalculator.BELGIQUE -> p.belgiumRegionCount // -1 si résolveur absent
    BadgeCalculator.SUISSE -> p.switzerlandRegionCount // -1 si résolveur absent
    BadgeCalculator.ANGLETERRE -> p.englandRegionCount // -1 si résolveur absent
    BadgeCalculator.IRLANDE -> p.irelandRegionCount // -1 si résolveur absent
    BadgeCalculator.ESPAGNE -> p.spainRegionCount // -1 si résolveur absent
    BadgeCalculator.ITALIE -> p.italyRegionCount // -1 si résolveur absent
    BadgeCalculator.JAPON -> p.japanRegionCount // -1 si résolveur absent
    BadgeCalculator.EXPLORATEUR -> p.exploredRegionCount // -1 si résolveur absent
    BadgeCatalog.OUTRE_MER -> p.overseasCount // -1 si résolveur absent
    BadgeCalculator.LIEUX_DISTINCTS -> p.cellCount
    else -> 0
}

/** Numérateur courant d'une famille « entraide » depuis le DTO serveur. */
private fun entraideValueOf(id: String, c: EntraideBadgeCountsDto): Int = when (id) {
    BadgeCatalog.FRIENDS -> c.friends
    BadgeCatalog.PROPOSALS_ACCEPTED -> c.proposalsAccepted
    BadgeCatalog.PROPOSALS_MADE -> c.proposalsMade
    BadgeCatalog.PROPOSALS_ACCEPTED_AS_OWNER -> c.proposalsAcceptedAsOwner
    BadgeCatalog.COMMENTS -> c.comments
    BadgeCatalog.REACTIONS_GIVEN -> c.reactionsGiven
    BadgeCatalog.REACTIONS_RECEIVED -> c.reactionsReceived
    else -> 0
}
