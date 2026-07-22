package com.florapin.app.profile

import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
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
    val userId: String = "",
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
    /** Modification du nom d'affichage en cours (TÂCHE 1.7). */
    val nameSaving: Boolean = false,
    val nameError: String? = null,
    /** Message de succès après modification du nom d'affichage. */
    val nameMessage: String? = null,
    /** Changement de mot de passe en cours (TÂCHE 1.6). */
    val passwordSaving: Boolean = false,
    val passwordError: String? = null,
    /** Message de succès après un changement de mot de passe. */
    val passwordMessage: String? = null,
    /** Nombre de mes propositions d'espèce acceptées par des amis, ou null si non chargé. */
    val acceptedProposals: Int? = null,
    /** Export/import de sauvegarde locale en cours (TÂCHE 1.5). */
    val backupRunning: Boolean = false,
    /** Message de résultat de la dernière sauvegarde/restauration (succès ou échec). */
    val backupMessage: String? = null,
    /** URL présignée de l'avatar (TÂCHE 5.1), ou null si l'utilisateur n'en a pas. */
    val avatarUrl: String? = null,
    /** Upload d'avatar en cours (TÂCHE 5.1). */
    val avatarUploading: Boolean = false,
    /** Message d'échec de l'upload d'avatar (succès = simple mise à jour de l'image). */
    val avatarError: String? = null,
    /** Nombre de badges (paliers) débloqués localement (TÂCHE 5.1), ou null si non chargé. */
    val badgeCount: Int? = null,
    /** Aperçu des dernières fleurs capturées, plus récentes d'abord (TÂCHE 5.1). */
    val recentFlowers: List<RecentFlower> = emptyList(),
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
    private val backup: ProfileBackup = ProfileBackup.NOOP,
    private val avatar: ProfileAvatar = ProfileAvatar.NOOP,
    private val collection: ProfileCollection = ProfileCollection.NOOP,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProfileUiState(
            userId = tokenStore.userId().orEmpty(),
            displayName = tokenStore.displayName().orEmpty(),
        ),
    )
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        refresh()
        loadStats()
        loadCollection()
    }

    /** Charge les statistiques collaboratives (best-effort, n'altère pas le profil). */
    private fun loadStats() {
        viewModelScope.launch {
            runCatching { identification.proposalStats() }.onSuccess { stats ->
                _state.update { it.copy(acceptedProposals = stats.acceptedProposals) }
            }
        }
    }

    /**
     * Charge l'aperçu « collection » local de l'onglet ① Profil (TÂCHE 5.1) :
     * nombre de badges débloqués + dernières fleurs. Best-effort (device-first,
     * n'altère pas l'identité affichée en cas d'échec).
     */
    private fun loadCollection() {
        viewModelScope.launch {
            val count = runCatching { collection.badgeCount() }.getOrNull()
            val recent = runCatching { collection.recentFlowers(RECENT_FLOWERS_LIMIT) }
                .getOrDefault(emptyList())
            _state.update { it.copy(badgeCount = count, recentFlowers = recent) }
        }
    }

    /** Rafraîchit le profil depuis le serveur (fallback /users/me). */
    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            _state.value = try {
                val user = session.fetchCurrentUser()
                _state.value.copy(
                    userId = user.id,
                    displayName = user.displayName,
                    email = user.email,
                    emailVerified = user.emailVerified,
                    avatarUrl = user.avatarUrl ?: _state.value.avatarUrl,
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

    /**
     * Modifie le nom d'affichage (TÂCHE 1.7). Trim + longueur (1..80) validés
     * côté serveur ; en cas de succès le nouveau nom est reflété dans l'état et
     * persisté localement. [onSuccess] est invoqué en cas de réussite.
     */
    fun updateDisplayName(displayName: String, onSuccess: () -> Unit = {}) {
        val trimmed = displayName.trim()
        _state.update { it.copy(nameSaving = true, nameError = null, nameMessage = null) }
        viewModelScope.launch {
            try {
                val user = session.updateDisplayName(trimmed)
                _state.update {
                    it.copy(
                        displayName = user.displayName,
                        nameSaving = false,
                        nameMessage = "Nom mis à jour.",
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                _state.update {
                    it.copy(nameSaving = false, nameError = updateNameMessageOf(e))
                }
            }
        }
    }

    private fun updateNameMessageOf(error: Throwable): String = when {
        error is HttpException && error.code() == 400 ->
            "Le nom doit comporter entre 1 et 80 caractères."
        else -> "Modification impossible. Réessayez."
    }

    /** Réinitialise les retours du formulaire de nom (fermeture du dialogue). */
    fun clearNameFeedback() {
        _state.update { it.copy(nameSaving = false, nameError = null, nameMessage = null) }
    }

    /**
     * Change le mot de passe (TÂCHE 1.6) : vérifie l'ancien côté serveur, réémet
     * les jetons de l'appareil courant et coupe les autres sessions. [onSuccess]
     * est invoqué en cas de réussite (ex. fermeture du dialogue).
     */
    fun changePassword(
        oldPassword: String,
        newPassword: String,
        onSuccess: () -> Unit,
    ) {
        _state.update {
            it.copy(passwordSaving = true, passwordError = null, passwordMessage = null)
        }
        viewModelScope.launch {
            try {
                session.changePassword(oldPassword, newPassword)
                _state.update {
                    it.copy(
                        passwordSaving = false,
                        passwordMessage = "Mot de passe modifié.",
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                _state.update {
                    it.copy(passwordSaving = false, passwordError = changePasswordMessageOf(e))
                }
            }
        }
    }

    /** Réinitialise les retours du formulaire de mot de passe (fermeture du dialogue). */
    fun clearPasswordFeedback() {
        _state.update {
            it.copy(passwordSaving = false, passwordError = null, passwordMessage = null)
        }
    }

    private fun changePasswordMessageOf(error: Throwable): String = when {
        error is HttpException && error.code() == 401 -> "Mot de passe actuel incorrect."
        error is HttpException && error.code() == 400 ->
            "Le nouveau mot de passe doit comporter au moins 8 caractères."
        error is HttpException && error.code() == 429 ->
            "Trop de tentatives. Réessayez dans un instant."
        else -> "Modification impossible. Réessayez."
    }

    private fun changeEmailMessageOf(error: Throwable): String = when {
        error is HttpException && error.code() == 409 -> "Cet email est déjà utilisé."
        error is HttpException && error.code() == 403 ->
            "Adresse déjà vérifiée : changement impossible ici."
        error is HttpException && error.code() == 400 -> "Format d'email invalide."
        else -> "Modification impossible. Réessayez."
    }

    /**
     * Exporte la bibliothèque locale (données + photos) dans le document ZIP
     * choisi par l'utilisateur (TÂCHE 1.5). Filet de sécurité du mode 100 %
     * local : n'exige aucun réseau.
     */
    fun exportBackup(destination: Uri) {
        _state.update { it.copy(backupRunning = true, backupMessage = null) }
        viewModelScope.launch {
            _state.value = try {
                val result = backup.export(destination)
                _state.value.copy(
                    backupRunning = false,
                    backupMessage = "Sauvegarde créée : ${result.flowers} fleur(s), " +
                        "${result.imageFiles} photo(s).",
                )
            } catch (e: Exception) {
                _state.value.copy(
                    backupRunning = false,
                    backupMessage = "Échec de la sauvegarde. Réessayez.",
                )
            }
        }
    }

    /**
     * Restaure une sauvegarde ZIP dans la base locale (fusion idempotente, sans
     * écrasement — TÂCHE 1.5).
     */
    fun importBackup(source: Uri) {
        _state.update { it.copy(backupRunning = true, backupMessage = null) }
        viewModelScope.launch {
            _state.value = try {
                val result = backup.import(source)
                _state.value.copy(
                    backupRunning = false,
                    backupMessage = "Restauration terminée : ${result.flowersAdded} " +
                        "fleur(s) ajoutée(s), ${result.flowersSkipped} déjà présente(s).",
                )
            } catch (e: Exception) {
                _state.value.copy(
                    backupRunning = false,
                    backupMessage = "Échec de la restauration : archive illisible ?",
                )
            }
        }
    }

    /**
     * Téléverse une nouvelle image d'avatar (TÂCHE 5.1). En cas de succès, l'URL
     * présignée renvoyée remplace l'affichage courant ; sinon [ProfileUiState.avatarError]
     * est renseigné. Nécessite le réseau (fonctionnalité sociale) : dégrade
     * proprement hors-ligne via le message d'erreur.
     */
    fun uploadAvatar(source: Uri) {
        _state.update { it.copy(avatarUploading = true, avatarError = null) }
        viewModelScope.launch {
            _state.value = try {
                val url = avatar.upload(source)
                _state.value.copy(
                    avatarUploading = false,
                    avatarUrl = url ?: _state.value.avatarUrl,
                )
            } catch (e: Exception) {
                _state.value.copy(
                    avatarUploading = false,
                    avatarError = "Échec de la mise à jour de la photo. Réessayez.",
                )
            }
        }
    }

    /** Téléverse l'un des compagnons botaniques préinstallés comme avatar. */
    fun uploadDefaultAvatar(@DrawableRes resourceId: Int) {
        _state.update { it.copy(avatarUploading = true, avatarError = null) }
        viewModelScope.launch {
            _state.value = try {
                val url = avatar.uploadDefault(resourceId)
                _state.value.copy(
                    avatarUploading = false,
                    avatarUrl = url ?: _state.value.avatarUrl,
                )
            } catch (e: Exception) {
                _state.value.copy(
                    avatarUploading = false,
                    avatarError = "Échec de la mise à jour de la photo. Réessayez.",
                )
            }
        }
    }

    companion object {
        /** Nombre de fleurs affichées dans l'aperçu « Dernières fleurs » (TÂCHE 5.1). */
        private const val RECENT_FLOWERS_LIMIT = 6

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
                        ProfileBackup.from(context),
                        ProfileAvatar.from(context, session),
                        ProfileCollection.from(context),
                    ) as T
                }
            }
    }
}
