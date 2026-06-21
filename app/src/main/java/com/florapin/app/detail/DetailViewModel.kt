package com.florapin.app.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.FlowerRepository
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

    fun delete(onDeleted: () -> Unit) {
        val current = flower.value ?: return
        viewModelScope.launch {
            repository.delete(current)
            onDeleted()
        }
    }
}
