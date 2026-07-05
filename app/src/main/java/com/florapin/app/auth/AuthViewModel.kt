package com.florapin.app.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.LocalSessionDataCleaner
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.auth.SessionManager
import com.florapin.app.network.dto.UserDto
import com.florapin.app.ui.components.networkErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** État de l'UI d'authentification. */
sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Success(val user: UserDto) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

/** ViewModel partagé des écrans Login/Register. */
class AuthViewModel(private val session: SessionManager) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun login(email: String, password: String) {
        run { session.login(email, password) }
    }

    fun register(email: String, password: String, displayName: String) {
        run { session.register(email, password, displayName) }
    }

    /**
     * Déconnecte l'utilisateur : révoque le refresh côté serveur (best-effort) et
     * purge les tokens locaux, puis invoque [onComplete] (sur le main dispatcher)
     * pour laisser l'appelant naviguer vers Login. La purge locale est garantie
     * même si l'appel réseau échoue.
     */
    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            session.logout()
            _state.value = AuthUiState.Idle
            onComplete()
        }
    }

    /** Réinitialise un état d'erreur (ex. quand l'utilisateur modifie un champ). */
    fun clearError() {
        if (_state.value is AuthUiState.Error) _state.value = AuthUiState.Idle
    }

    private fun run(action: suspend () -> UserDto) {
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                AuthUiState.Success(action())
            } catch (e: Exception) {
                AuthUiState.Error(messageOf(e))
            }
        }
    }

    // Mapping réseau commun (TÂCHE 6.16) : distingue hors-ligne / serveur
    // injoignable, avec surcharge des 4xx spécifiques à l'auth.
    private fun messageOf(error: Exception): String = networkErrorMessage(error) { code ->
        when (code) {
            401 -> "Identifiants invalides."
            409 -> "Un compte existe déjà avec cet email."
            else -> null
        }
    }

    companion object {
        /** Factory câblant le stockage chiffré + les services authentifiés. */
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tokenStore = EncryptedTokenStore(context.applicationContext)
                    val apis = NetworkModule.createAuthenticated(tokenStore)
                    val cleaner = LocalSessionDataCleaner.from(context)
                    val session =
                        NetworkModule.sessionManager(apis, tokenStore, cleaner)
                    return AuthViewModel(session) as T
                }
            }
    }
}
