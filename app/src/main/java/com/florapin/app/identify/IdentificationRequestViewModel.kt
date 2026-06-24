package com.florapin.app.identify

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.IdentificationApi
import com.florapin.app.network.auth.EncryptedTokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** État du bouton « Demander une identification » (NODE-134). */
sealed interface IdentificationRequestState {
    data object Idle : IdentificationRequestState
    data object Sending : IdentificationRequestState
    data object Sent : IdentificationRequestState
    data class Error(val message: String) : IdentificationRequestState
}

/**
 * Sollicite les amis pour identifier une fleur non identifiée (côté propriétaire,
 * NODE-134). Appelle POST flowers/{serverId}/identification-requests.
 */
class IdentificationRequestViewModel(
    private val api: IdentificationApi,
) : ViewModel() {

    private val _state =
        MutableStateFlow<IdentificationRequestState>(IdentificationRequestState.Idle)
    val state: StateFlow<IdentificationRequestState> = _state.asStateFlow()

    /** Envoie la demande pour la fleur identifiée par son UUID serveur. */
    fun request(flowerServerId: String) {
        if (_state.value is IdentificationRequestState.Sending) return
        _state.value = IdentificationRequestState.Sending
        viewModelScope.launch {
            _state.value = try {
                val response = api.request(flowerServerId)
                if (response.isSuccessful) {
                    IdentificationRequestState.Sent
                } else {
                    IdentificationRequestState.Error("Échec (${response.code()}).")
                }
            } catch (e: Exception) {
                IdentificationRequestState.Error(
                    e.message ?: "Impossible d'envoyer la demande.",
                )
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tokenStore = EncryptedTokenStore(context.applicationContext)
                    val apis = NetworkModule.createAuthenticated(tokenStore)
                    return IdentificationRequestViewModel(apis.identification) as T
                }
            }
    }
}
