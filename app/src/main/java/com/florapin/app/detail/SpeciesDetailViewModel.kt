package com.florapin.app.detail

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.FlowerRepository
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.SpeciesApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.SpeciesDto
import com.florapin.app.ui.components.networkErrorMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** État de l'écran fiche Espèce (NODE-151). */
data class SpeciesDetailUiState(
    val loading: Boolean = true,
    val species: SpeciesDto? = null,
    val flowers: List<FlowerEntity> = emptyList(),
    val error: String? = null,
)

/**
 * Fiche d'une espèce : charge la fiche distante (GET /species/{id}) et observe
 * localement « mes fleurs de cette espèce » (par species_id ou nom scientifique).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpeciesDetailViewModel(
    application: Application,
    private val api: SpeciesApi,
    private val speciesId: String,
) : AndroidViewModel(application) {

    private val repository = FlowerRepository.from(application)
    private val species = MutableStateFlow<SpeciesDto?>(null)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<SpeciesDetailUiState> = species
        .flatMapLatest { sp ->
            repository.observeBySpecies(speciesId, sp?.scientificName)
                .combine(error) { flowers, err ->
                    SpeciesDetailUiState(
                        loading = sp == null && err == null,
                        species = sp,
                        flowers = flowers,
                        error = err,
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SpeciesDetailUiState(),
        )

    init {
        load()
    }

    fun load() {
        error.value = null
        viewModelScope.launch {
            try {
                species.value = api.get(speciesId)
            } catch (e: Exception) {
                error.value = networkErrorMessage(e)
            }
        }
    }

    companion object {
        fun factory(context: Context, speciesId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(
                    modelClass: Class<T>,
                ): T {
                    val app = context.applicationContext as Application
                    val apis = NetworkModule.createAuthenticated(EncryptedTokenStore(app))
                    return SpeciesDetailViewModel(app, apis.species, speciesId) as T
                }
            }
    }
}
