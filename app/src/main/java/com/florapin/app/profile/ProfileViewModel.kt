package com.florapin.app.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.LocalSessionDataCleaner
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.IdentificationApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.auth.SessionManager
import com.florapin.app.network.auth.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** État du profil de l'utilisateur courant. */
data class ProfileUiState(
    val displayName: String = "",
    val email: String = "",
    val emailVerified: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val deleting: Boolean = false,
    val deleteError: String? = null,
    /** Email de vérification en cours d'envoi (NODE-117). */
    val verificationSending: Boolean = false,
    /** Message retour de la demande de vérification (succès ou erreur). */
    val verificationMessage: String? = null,
    /** Changement d'adresse en cours (NODE-117). */
    val emailSaving: Boolean = false,
    val emailError: String? = null,
    /** Nombre de mes propositions d'espèce acceptées par des amis, ou null si non chargé. */
    val acceptedProposals: Int? = null,
)

/**
 * Profil de l'utilisateur courant : affiche immédiatement le displayName persisté
 * localement (NODE-95) puis le complète/rafraîchit via le réseau (GET /users/me),
 * notamment pour l'email qui n'est pas stocké en local.
 */
class ProfileViewModel(
    tokenStore: TokenStore,
    private val session: SessionManager,
    private val identification: IdentificationApi,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProfileUiState(displayName = tokenStore.displayName().orEmpty()),
    )
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        refresh()
        loadStats()
    }

    /** Charge les statistiques collaboratives (best-effort, n'altère pas le profil). */
    private fun loadStats() {
        viewModelScope.launch {
            runCatching { identification.proposalStats() }.onSuccess { stats ->
                _state.update { it.copy(acceptedProposals = stats.acceptedProposals) }
            }
        }
    }

    /** Rafraîchit le profil depuis le serveur (fallback /users/me). */
    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            _state.value = try {
                val user = session.fetchCurrentUser()
                _state.value.copy(
                    displayName = user.displayName,
                    email = user.email,
                    emailVerified = user.emailVerified,
                    loading = false,
                )
            } catch (e: Exception) {
                // On conserve le displayName persisté ; seul le rafraîchissement échoue.
                _state.value.copy(
                    loading = false,
                    error = "Impossible de rafraîchir le profil.",
                )
            }
        }
    }

    /**
     * Supprime définitivement le compte (NODE-118). [password] re-authentifie
     * l'utilisateur. En cas de succès, la session locale est purgée et [onDeleted]
     * est invoqué pour naviguer vers Login ; sinon [ProfileUiState.deleteError]
     * est renseigné.
     */
    fun deleteAccount(password: String, onDeleted: () -> Unit) {
        _state.update { it.copy(deleting = true, deleteError = null) }
        viewModelScope.launch {
            try {
                session.deleteAccount(password)
                onDeleted()
            } catch (e: Exception) {
                _state.update {
                    it.copy(deleting = false, deleteError = deleteMessageOf(e))
                }
            }
        }
    }

    private fun deleteMessageOf(error: Throwable): String = when {
        error is HttpException && error.code() == 401 -> "Mot de passe incorrect."
        else -> "Suppression impossible. Réessayez."
    }

    /** Demande l'envoi d'un email de vérification d'adresse (NODE-117). */
    fun requestEmailVerification() {
        _state.update { it.copy(verificationSending = true, verificationMessage = null) }
        viewModelScope.launch {
            _state.value = try {
                session.requestEmailVerification()
                _state.value.copy(
                    verificationSending = false,
                    verificationMessage = "Email de vérification envoyé. Consultez votre boîte.",
                )
            } catch (e: Exception) {
                _state.value.copy(
                    verificationSending = false,
                    verificationMessage = "Envoi impossible. Réessayez.",
                )
            }
        }
    }

    /**
     * Change l'adresse email (NODE-117), autorisé tant qu'elle n'est pas
     * vérifiée. Met à jour l'état avec la nouvelle adresse (non vérifiée).
     */
    fun changeEmail(newEmail: String) {
        _state.update { it.copy(emailSaving = true, emailError = null) }
        viewModelScope.launch {
            _state.value = try {
                val user = session.changeEmail(newEmail)
                _state.value.copy(
                    email = user.email,
                    emailVerified = user.emailVerified,
                    emailSaving = false,
                )
            } catch (e: Exception) {
                _state.value.copy(emailSaving = false, emailError = changeEmailMessageOf(e))
            }
        }
    }

    private fun changeEmailMessageOf(error: Throwable): String = when {
        error is HttpException && error.code() == 409 -> "Cet email est déjà utilisé."
        error is HttpException && error.code() == 403 ->
            "Adresse déjà vérifiée : changement impossible ici."
        error is HttpException && error.code() == 400 -> "Format d'email invalide."
        else -> "Modification impossible. Réessayez."
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
                    return ProfileViewModel(
                        tokenStore,
                        session,
                        apis.identification,
                    ) as T
                }
            }
    }
}
