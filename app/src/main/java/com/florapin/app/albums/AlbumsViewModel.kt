package com.florapin.app.albums

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.AlbumEntity
import com.florapin.app.data.AlbumRepository
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.toEntity
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.CreateAlbumRequest
import com.florapin.app.ui.components.networkErrorMessage
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Liste des albums + création/renommage/suppression (NODE-103). */
data class AlbumSummary(
    val album: AlbumEntity,
    val flowers: List<FlowerEntity>,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AlbumsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AlbumRepository.from(application)
    // Albums collaboratifs (TÂCHE 7.1) : leur création crée un groupe côté serveur,
    // c'est donc une opération RÉSEAU (device-first : dégrade proprement hors-ligne).
    private val albumsApi by lazy {
        NetworkModule.createAuthenticated(
            EncryptedTokenStore(application.applicationContext),
        ).albums
    }

    val albums: StateFlow<List<AlbumEntity>> = repository.albums.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /**
     * Couvertures vivantes : chaque album transporte ses trois captures les plus
     * récentes et son nombre total, sans dupliquer l'état dans l'UI.
     */
    val summaries: StateFlow<List<AlbumSummary>> = repository.albums
        .flatMapLatest { albums ->
            if (albums.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    albums.map { album ->
                        repository.flowersIn(album.id).map { flowers ->
                            AlbumSummary(album, flowers)
                        }
                    },
                ) { it.toList() }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** Message transitoire (erreur réseau à la création collaborative). */
    val message: MutableStateFlow<String?> = MutableStateFlow(null)

    fun clearMessage() {
        message.value = null
    }

    fun create(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.create(trimmed) }
    }

    /**
     * Crée un album COLLABORATIF (TÂCHE 7.1) : le serveur crée le groupe et
     * rattache l'album. Nécessite le réseau ; l'album revient déjà synchronisé et
     * est inséré localement. En cas d'échec, un message est exposé (device-first :
     * un album collaboratif ne peut pas naître hors-ligne).
     */
    fun createCollaborative(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                val dto = albumsApi.create(
                    CreateAlbumRequest(
                        name = trimmed,
                        clientId = UUID.randomUUID().toString(),
                        collaborative = true,
                    ),
                )
                repository.insert(dto.toEntity())
            } catch (e: Exception) {
                message.value = networkErrorMessage(e)
            }
        }
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
