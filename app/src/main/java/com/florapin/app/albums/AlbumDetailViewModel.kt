package com.florapin.app.albums

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.AlbumEntity
import com.florapin.app.data.AlbumRepository
import com.florapin.app.data.FlowerEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Un album et la grille de ses fleurs ; renommage et retrait (NODE-103). */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AlbumRepository.from(application)
    private val albumId = MutableStateFlow<Long?>(null)

    val album: StateFlow<AlbumEntity?> = albumId
        .filterNotNull()
        .flatMapLatest { id -> repository.observeById(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val flowers: StateFlow<List<FlowerEntity>> = albumId
        .filterNotNull()
        .flatMapLatest { id -> repository.flowersIn(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cible l'album à afficher (idempotent). */
    fun setAlbumId(id: Long) {
        if (albumId.value != id) albumId.value = id
    }

    fun rename(name: String) {
        val current = album.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.rename(current, trimmed) }
    }

    fun removeFlower(flowerLocalId: Long) {
        val id = albumId.value ?: return
        viewModelScope.launch { repository.removeFlower(id, flowerLocalId) }
    }

    fun setCover(flowerLocalId: Long) {
        val current = album.value ?: return
        viewModelScope.launch { repository.setCover(current, flowerLocalId) }
    }
}
