package com.florapin.app.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.auth.EncryptedTokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Compte les fleurs du feed « Partagées » non encore vues, pour le badge de
 * nouveautés sur l'onglet 🖼️ de la bottom bar (visible depuis tous les onglets).
 *
 * Le badge se calcule à partir des fleurs courantes du feed comparées à
 * l'ensemble « vu » de [FeedBadgeStore] (marqué par [SharedFeedViewModel] à
 * l'ouverture de l'onglet). [AndroidViewModel] pour le contexte applicatif : la
 * factory par défaut suffit (pas d'injection).
 */
class FeedBadgeViewModel(application: Application) : AndroidViewModel(application) {

    private val apis by lazy {
        NetworkModule.createAuthenticated(EncryptedTokenStore(application))
    }
    private val badgeStore = FeedBadgeStore(application)

    private val _badge = MutableStateFlow(0)
    /** Nombre de fleurs du feed non encore vues (0 = pas de badge). */
    val badge: StateFlow<Int> = _badge.asStateFlow()

    /**
     * Recalcule le badge : récupère la première page du feed et compte les fleurs
     * non encore vues. Appelé à chaque changement d'onglet (l'ouverture de l'onglet
     * Partagées aura marqué ses fleurs comme vues → 0). Silencieux hors-ligne / non
     * connecté (on conserve la dernière valeur connue).
     */
    fun refresh() {
        viewModelScope.launch {
            try {
                val ids = apis.feed.getFeed(sort = "date", limit = PAGE_SIZE).map { it.id }
                _badge.value = badgeStore.unseenCount(ids)
            } catch (_: Exception) {
                // hors-ligne / non connecté : on garde la valeur actuelle
            }
        }
    }

    private companion object {
        /** Fenêtre de fleurs prise en compte pour le badge (aligne sur la 1re page du feed). */
        const val PAGE_SIZE = 20
    }
}
