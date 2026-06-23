package com.florapin.app.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.FlowerRepository
import com.florapin.app.data.PhotoEntity
import com.florapin.app.data.PhotoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Photos additionnelles d'une fleur (NODE-108) : observation, ajout, suppression
 * et choix de la couverture (échange local du fichier image principal).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotosViewModel(application: Application) : AndroidViewModel(application) {

    private val photoRepo = PhotoRepository.from(application)
    private val flowerRepo = FlowerRepository.from(application)
    private val flowerId = MutableStateFlow<Long?>(null)

    val photos: StateFlow<List<PhotoEntity>> = flowerId
        .filterNotNull()
        .flatMapLatest { id -> photoRepo.photosOf(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFlowerId(id: Long) {
        if (flowerId.value != id) flowerId.value = id
    }

    fun addPhoto(imagePath: String) {
        val id = flowerId.value ?: return
        viewModelScope.launch { photoRepo.addLocalPhoto(id, imagePath) }
    }

    fun deletePhoto(photo: PhotoEntity) {
        viewModelScope.launch { photoRepo.delete(photo) }
    }

    /**
     * Définit une photo (locale) comme couverture : échange son fichier avec
     * l'image principale de la fleur. Sans effet pour une photo distante (sans
     * fichier local). Échange purement local (la couverture serveur est inchangée).
     */
    fun makeCover(photo: PhotoEntity) {
        val id = flowerId.value ?: return
        if (photo.imagePath.isEmpty()) return
        viewModelScope.launch {
            val flower = flowerRepo.getById(id) ?: return@launch
            val oldCover = flower.imagePath
            flowerRepo.update(flower.copy(imagePath = photo.imagePath))
            photoRepo.update(photo.copy(imagePath = oldCover, remoteUrl = null))
        }
    }
}
