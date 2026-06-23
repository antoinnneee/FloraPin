package com.florapin.app.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.SpeciesApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.SpeciesDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Autocomplétion d'espèce (NODE-150) : interroge /species/search avec un
 * debounce et expose les suggestions. Tolérant aux erreurs réseau (liste vide).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SpeciesPickerViewModel(private val api: SpeciesApi) : ViewModel() {

    private val query = MutableStateFlow("")

    val suggestions: StateFlow<List<SpeciesDto>> = query
        .map { it.trim() }
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { term ->
            flow {
                if (term.length < MIN_QUERY_LENGTH) {
                    emit(emptyList())
                } else {
                    emit(runCatching { api.search(term, SUGGESTION_LIMIT) }
                        .getOrDefault(emptyList()))
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** Met à jour le terme recherché (déclenche l'autocomplétion debouncée). */
    fun onQueryChange(value: String) {
        query.value = value
    }

    /** Vide les suggestions (ex. après sélection). */
    fun clear() {
        query.value = ""
    }

    companion object {
        private const val MIN_QUERY_LENGTH = 2
        private const val SUGGESTION_LIMIT = 8

        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tokenStore = EncryptedTokenStore(context.applicationContext)
                    val apis = NetworkModule.createAuthenticated(tokenStore)
                    return SpeciesPickerViewModel(apis.species) as T
                }
            }
    }
}
