package com.florapin.app.herbier

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.FloraDatabase
import com.florapin.app.data.FlowerDao
import com.florapin.app.data.LocalSpeciesCount
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.SpeciesApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.HerbierFamilyDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * État de l'écran « Mon herbier » (TÂCHE 5.6).
 *
 * Deux sources, dans l'esprit device-first :
 *  - les compteurs d'en-tête ([distinctSpecies], [totalFlowers]) et la liste à
 *    plat des espèces ([localSpecies]) viennent de Room → **toujours disponibles**,
 *    y compris hors-ligne ;
 *  - le regroupement par familles ([families]) vient du serveur (la famille est
 *    portée par l'espèce, cf. GET /species/herbier) → **partiel hors-ligne**
 *    ([familiesAvailable] = `false`), on retombe alors sur [localSpecies].
 */
data class HerbierUiState(
    val loading: Boolean = true,
    /** Espèces distinctes (calcul local, toujours disponible). */
    val distinctSpecies: Int = 0,
    /** Fleurs actives rattachées à une espèce (calcul local). */
    val totalFlowers: Int = 0,
    /** Familles botaniques (regroupement serveur). */
    val families: List<HerbierFamilyDto> = emptyList(),
    /** `false` quand le regroupement par familles n'a pas pu être chargé. */
    val familiesAvailable: Boolean = true,
    /** Repli à plat : espèces distinctes locales (affiché hors-ligne). */
    val localSpecies: List<LocalSpeciesCount> = emptyList(),
)

/**
 * ViewModel de l'écran « Mon herbier » (TÂCHE 5.6) : combine les agrégats locaux
 * ([FlowerDao]) et le regroupement par familles du serveur ([SpeciesApi]).
 *
 * La collection locale s'affiche immédiatement (hors-ligne inclus) ; le volet
 * familles, indisponible hors-ligne, grise alors sa section
 * ([HerbierUiState.familiesAvailable] = `false`) et l'app retombe sur la liste
 * locale à plat (pattern de dégradation propre `CommentsLockedNotice`).
 */
class HerbierViewModel(
    private val flowerDao: FlowerDao,
    private val speciesApi: SpeciesApi,
) : ViewModel() {

    private val _state = MutableStateFlow(HerbierUiState())
    val state: StateFlow<HerbierUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** (Re)charge l'herbier : agrégats locaux d'abord, puis familles serveur. */
    fun load() {
        viewModelScope.launch {
            // Local (toujours disponible).
            val flowers = flowerDao.countActive()
            val distinct = flowerDao.countDistinctSpecies()
            val local = flowerDao.speciesCounts()
            _state.update {
                it.copy(
                    loading = false,
                    totalFlowers = flowers,
                    distinctSpecies = distinct,
                    localSpecies = local,
                )
            }

            // Familles serveur (best-effort : grisé hors-ligne).
            val herbier = runCatching { speciesApi.herbier() }.getOrNull()
            _state.update {
                it.copy(
                    families = herbier?.families ?: emptyList(),
                    familiesAvailable = herbier != null,
                )
            }
        }
    }

    companion object {
        /** Factory câblant le DAO local + le client serveur authentifié partagé. */
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val apis = NetworkModule.createAuthenticated(
                        EncryptedTokenStore(context.applicationContext),
                    )
                    return HerbierViewModel(
                        FloraDatabase.getInstance(context).flowerDao(),
                        apis.species,
                    ) as T
                }
            }
    }
}
