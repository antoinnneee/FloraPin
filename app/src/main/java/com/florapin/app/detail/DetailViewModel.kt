package com.florapin.app.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.FlowerRepository
import com.florapin.app.network.dto.SpeciesDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Détail d'une fleur : observation, édition des notes, suppression. */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FlowerRepository.from(application)
    private val flowerId = MutableStateFlow<Long?>(null)

    val flower: StateFlow<FlowerEntity?> = flowerId
        .filterNotNull()
        .flatMapLatest { id -> repository.observeById(id) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** Cible la fleur à afficher (idempotent). */
    fun setFlowerId(id: Long) {
        if (flowerId.value != id) flowerId.value = id
    }

    fun saveNotes(notes: String) {
        val current = flower.value ?: return
        viewModelScope.launch { repository.updateNotes(current, notes) }
    }

    /**
     * Enregistre l'espèce et les étiquettes éditées. [selected] est la fiche du
     * référentiel choisie via l'autocomplétion (NODE-150), ou null pour une
     * saisie libre (le species_id et le cache sont alors effacés).
     */
    fun saveClassification(
        species: String,
        tags: List<String>,
        selected: SpeciesDto?,
    ) {
        val current = flower.value ?: return
        viewModelScope.launch {
            repository.updateClassification(
                flower = current,
                species = species,
                tags = tags,
                speciesId = selected?.id,
                speciesScientificName = selected?.scientificName,
                speciesCommonName = selected?.commonName,
            )
        }
    }

    /**
     * Publie ou retire la fleur du flux d'amis (NODE-137). [includeGps] règle la
     * diffusion de la position quand la fleur est publiée.
     */
    fun setFeedPublication(published: Boolean, includeGps: Boolean) {
        val current = flower.value ?: return
        viewModelScope.launch {
            repository.updateFeedVisibility(current, published, includeGps)
        }
    }

    /**
     * Supprime la fleur de façon annulable (TÂCHE 6.13) : pose un soft-delete
     * immédiat (la fleur disparaît des listes) sans finaliser ni synchroniser, ce
     * qui laisse une fenêtre d'annulation. [onDeleted] reçoit l'id supprimé pour
     * que l'écran suivant (galerie) propose l'annulation via un snackbar ; c'est
     * lui qui finalisera (purge/propagation) ou restaurera. On ne relance PAS de
     * sync ici : tant que la passe n'a pas tourné, la suppression reste locale et
     * annulable (pas de course).
     */
    fun delete(onDeleted: (Long) -> Unit) {
        val current = flower.value ?: return
        viewModelScope.launch {
            repository.softDelete(current)
            onDeleted(current.id)
        }
    }
}
