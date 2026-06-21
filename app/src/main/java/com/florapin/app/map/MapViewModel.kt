package com.florapin.app.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.FlowerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Une fleur géolocalisée, prête à devenir un marqueur. */
data class FlowerMarker(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
)

/** Expose les fleurs disposant d'une position pour la carte. */
class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FlowerRepository.from(application)

    val markers: StateFlow<List<FlowerMarker>> = repository.flowers
        .map { flowers ->
            flowers.mapNotNull { flower ->
                val lat = flower.latitude
                val lng = flower.longitude
                if (lat != null && lng != null) {
                    FlowerMarker(flower.id, lat, lng)
                } else {
                    null
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}
