package com.florapin.app.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.FlowerRepository
import com.florapin.app.network.auth.EncryptedTokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Une fleur géolocalisée, prête à devenir un marqueur. */
data class FlowerMarker(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val emoji: String = FlowerEmoji.DEFAULT,
)

/**
 * Expose les fleurs géolocalisées pour la carte, filtrées par date, espèce et
 * appartenance (mes fleurs / fleurs d'amis).
 */
class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FlowerRepository.from(application)
    private val currentUserId: String? = EncryptedTokenStore(application).userId()

    private val _dateFilter = MutableStateFlow(DateFilter.ALL)
    val dateFilter: StateFlow<DateFilter> = _dateFilter.asStateFlow()

    private val _speciesFilter = MutableStateFlow<String?>(null)
    val speciesFilter: StateFlow<String?> = _speciesFilter.asStateFlow()

    private val _friendsOnly = MutableStateFlow(false)
    val friendsOnly: StateFlow<Boolean> = _friendsOnly.asStateFlow()

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
        ) { flowers, date, species, friendsOnly ->
            flowers.toFilteredMarkers(
                dateFilter = date,
                species = species,
                friendsOnly = friendsOnly,
                currentUserId = currentUserId,
                now = System.currentTimeMillis(),
            )
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

    fun toggleFriendsOnly() {
        _friendsOnly.value = !_friendsOnly.value
    }
}
