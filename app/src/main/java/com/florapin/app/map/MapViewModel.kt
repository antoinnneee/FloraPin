package com.florapin.app.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.FlowerRepository
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.FeedApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.fullPhotoUrls
import com.florapin.app.network.dto.previewPhotoUrls
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Une fleur géolocalisée, prête à devenir un marqueur.
 *
 * [navigable] distingue mes fleurs (un tap ouvre leur détail local) des fleurs
 * d'amis venues du flux (pas de détail local : le tap est ignoré).
 */
data class FlowerMarker(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val emoji: String = FlowerEmoji.DEFAULT,
    val navigable: Boolean = true,
    /**
     * URL de la photo, pour les marqueurs non navigables (fleurs d'amis) : faute
     * de détail local à ouvrir, le tap affiche la photo en plein écran.
     */
    val photoUrl: String? = null,
    /**
     * Source de la vignette (fichier local ou URL) affichée dans une bulle
     * reliée à l'emoji d'espèce à fort zoom.
     */
    val thumbnailModel: Any? = null,
)

/**
 * Expose les fleurs géolocalisées pour la carte, filtrées par date, espèce et
 * appartenance (mes fleurs + fleurs d'amis).
 *
 * Mes fleurs viennent de la base locale ; les fleurs d'amis (partages + flux
 * 'friends', NODE-137) sont récupérées via [FeedApi] et n'apparaissent que si
 * l'ami a accepté de diffuser leur position (sinon latitude/longitude sont null).
 */
class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FlowerRepository.from(application)
    private val currentUserId: String? = EncryptedTokenStore(application).userId()
    private val feedApi: FeedApi =
        NetworkModule.createAuthenticated(EncryptedTokenStore(application)).feed

    private val _dateFilter = MutableStateFlow(DateFilter.ALL)
    val dateFilter: StateFlow<DateFilter> = _dateFilter.asStateFlow()

    private val _speciesFilter = MutableStateFlow<String?>(null)
    val speciesFilter: StateFlow<String?> = _speciesFilter.asStateFlow()

    private val _friendsOnly = MutableStateFlow(true)
    val friendsOnly: StateFlow<Boolean> = _friendsOnly.asStateFlow()

    /** Marqueurs des fleurs d'amis géolocalisées (vide tant que « Ami » est off). */
    private val _friendMarkers = MutableStateFlow<List<FlowerMarker>>(emptyList())

    init {
        loadFriendMarkers()
    }

    /** Espèces disponibles pour alimenter le sélecteur du filtre. */
    val availableSpecies: StateFlow<List<String>> =
        repository.flowers.map { it.availableSpecies() }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val markers: StateFlow<List<FlowerMarker>> =
        combine(
            repository.flowers,
            _dateFilter,
            _speciesFilter,
            _friendsOnly,
            _friendMarkers,
        ) { flowers, date, species, friendsOnly, friendMarkers ->
            // Mes fleurs sont toujours affichées (filtrées par date/espèce) ; le
            // chip « Ami » ajoute par-dessus les fleurs d'amis géolocalisées.
            val mine = flowers.toFilteredMarkers(
                dateFilter = date,
                species = species,
                friendsOnly = false,
                currentUserId = currentUserId,
                now = System.currentTimeMillis(),
            )
            if (friendsOnly) mine + friendMarkers else mine
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun setDateFilter(filter: DateFilter) {
        _dateFilter.value = filter
    }

    /** Sélectionne une espèce (null = toutes). */
    fun setSpeciesFilter(species: String?) {
        _speciesFilter.value = species
    }

    /** Bascule l'affichage des fleurs d'amis et (re)charge le flux à l'activation. */
    fun toggleFriendsOnly() {
        val enabled = !_friendsOnly.value
        _friendsOnly.value = enabled
        if (enabled) loadFriendMarkers()
    }

    /**
     * Récupère le flux d'amis et n'en garde que les fleurs géolocalisées. Un
     * échec réseau laisse la liste en l'état (les fleurs d'amis ne s'affichent
     * simplement pas, sans bloquer la carte).
     */
    private fun loadFriendMarkers() {
        viewModelScope.launch {
            runCatching { feedApi.getFeed() }
                .onSuccess { flowers -> _friendMarkers.value = flowers.toFriendMarkers() }
        }
    }
}

/** Projette les fleurs du flux en marqueurs non navigables (celles sans GPS exclues). */
private fun List<FlowerDto>.toFriendMarkers(): List<FlowerMarker> =
    mapNotNull { dto ->
        val lat = dto.latitude
        val lng = dto.longitude
        if (lat != null && lng != null) {
            FlowerMarker(
                // Pas de détail local pour une fleur d'ami : id synthétique stable,
                // non utilisé pour la navigation (navigable = false).
                id = dto.id.hashCode().toLong(),
                latitude = lat,
                longitude = lng,
                emoji = FlowerEmoji.forSpecies(dto.species),
                navigable = false,
                photoUrl = dto.fullPhotoUrls().firstOrNull(),
                thumbnailModel = dto.previewPhotoUrls().firstOrNull(),
            )
        } else {
            null
        }
    }
