package com.florapin.app.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.FlowerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Une fleur géolocalisée, prête à devenir un marqueur. */
data class FlowerMarker(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
)

/** Expose les fleurs géolocalisées pour la carte, filtrées par date. */
class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FlowerRepository.from(application)

    private val _dateFilter = MutableStateFlow(DateFilter.ALL)
    val dateFilter: StateFlow<DateFilter> = _dateFilter.asStateFlow()

    val markers: StateFlow<List<FlowerMarker>> =
        combine(repository.flowers, _dateFilter) { flowers, filter ->
            val threshold = filter.minTimestamp(System.currentTimeMillis())
            flowers.asSequence()
                .filter { threshold == null || it.createdAt >= threshold }
                .mapNotNull { flower ->
                    val lat = flower.latitude
                    val lng = flower.longitude
                    if (lat != null && lng != null) {
                        FlowerMarker(flower.id, lat, lng)
                    } else {
                        null
                    }
                }
                .toList()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun setDateFilter(filter: DateFilter) {
        _dateFilter.value = filter
    }
}
