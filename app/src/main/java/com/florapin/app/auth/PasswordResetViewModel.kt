package com.florapin.app.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.auth.SessionManager
import com.florapin.app.ui.components.networkErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État du flux « mot de passe oublié » / réinitialisation (NODE-116). */
data class PasswordResetUiState(
    val loading: Boolean = false,
    /** Email de réinitialisation demandé (écran « mot de passe oublié »). */
    val requestSent: Boolean = false,
    /** Mot de passe réinitialisé avec succès (écran de reset). */
    val resetDone: Boolean = false,
    val error: String? = null,
)

/**
 * Pilote les deux étapes du reset de mot de passe (NODE-116) : demande d'email
 * (`forgotPassword`) et application du nouveau mot de passe via le token reçu
 * (`resetPassword`). Endpoints publics : aucune session requise.
 */
class PasswordResetViewModel(
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(PasswordResetUiState())
    val state: StateFlow<PasswordResetUiState> = _state.asStateFlow()

    /** Demande l'envoi du lien de réinitialisation à [email]. */
    fun requestReset(email: String) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            _state.value = try {
                session.forgotPassword(email)
                _state.value.copy(loading = false, requestSent = true)
            } catch (e: Exception) {
                _state.value.copy(loading = false, error = messageOf(e))
            }
        }
    }

    /** Applique [newPassword] via le [token] reçu par email. */
    fun resetPassword(token: String, newPassword: String) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            _state.value = try {
                session.resetPassword(token, newPassword)
                _state.value.copy(loading = false, resetDone = true)
            } catch (e: Exception) {
                _state.value.copy(loading = false, error = messageOf(e))
            }
        }
    }

    // Mapping réseau commun (TÂCHE 6.16) avec surcharge des 4xx propres au reset.
    private fun messageOf(error: Throwable): String = networkErrorMessage(error) { code ->
        when (code) {
            401 -> "Lien invalide ou expiré. Refaites une demande."
            400 -> "Vérifiez le format (mot de passe : 8 caractères minimum)."
            else -> null
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
                    return PasswordResetViewModel(session) as T
                }
            }
    }
}
