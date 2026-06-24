package com.florapin.app.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.auth.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de la vérification d'email via deep link (NODE-117). */
data class EmailVerifyUiState(
    val loading: Boolean = false,
    val verified: Boolean = false,
    val error: String? = null,
)

/**
 * Valide un token de vérification d'email reçu par lien
 * (`florapin.pattounecorp.ovh/verify?token=...`). Endpoint public : aucune session requise.
 */
class EmailVerifyViewModel(
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(EmailVerifyUiState())
    val state: StateFlow<EmailVerifyUiState> = _state.asStateFlow()

    fun verify(token: String) {
        if (token.isBlank()) {
            _state.update { it.copy(error = "Lien de vérification incomplet.") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            _state.value = try {
                session.verifyEmail(token)
                _state.value.copy(loading = false, verified = true)
            } catch (e: Exception) {
                _state.value.copy(
                    loading = false,
                    error = "Lien invalide ou expiré. Renvoyez un email depuis le profil.",
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
                    val session = NetworkModule.sessionManager(apis, tokenStore)
                    return EmailVerifyViewModel(session) as T
                }
            }
    }
}
