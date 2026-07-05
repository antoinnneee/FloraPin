package com.florapin.app.albums

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.AlbumEntity
import com.florapin.app.data.AlbumRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Liste des albums + création/renommage/suppression (NODE-103). */
class AlbumsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AlbumRepository.from(application)

    val albums: StateFlow<List<AlbumEntity>> = repository.albums.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun create(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.create(trimmed) }
    }

    fun rename(album: AlbumEntity, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.rename(album, trimmed) }
    }

    fun delete(album: AlbumEntity) {
        viewModelScope.launch { repository.delete(album) }
    }

    /** Ajoute une fleur (id local) à un album. */
    fun addFlowerToAlbum(albumId: Long, flowerLocalId: Long) {
        viewModelScope.launch { repository.addFlower(albumId, flowerLocalId) }
    }

    /**
     * Ajoute un lot de fleurs (ids locaux) à un album, en une seule coroutine
     * (multi-sélection de la galerie — TÂCHE 6.6). Les appartenances déjà
     * présentes sont ignorées (INSERT OR IGNORE côté DAO).
     */
    fun addFlowersToAlbum(albumId: Long, flowerLocalIds: Collection<Long>) {
        if (flowerLocalIds.isEmpty()) return
        viewModelScope.launch {
            flowerLocalIds.forEach { repository.addFlower(albumId, it) }
        }
    }
}
