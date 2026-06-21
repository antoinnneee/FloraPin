package com.florapin.app.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.FlowerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Expose la liste des fleurs persistées pour la galerie.
 *
 * [AndroidViewModel] pour obtenir le contexte applicatif (la factory par défaut
 * suffit, pas besoin d'injection).
 */
class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FlowerRepository.from(application)

    val flowers: StateFlow<List<FlowerEntity>> = repository.flowers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}
