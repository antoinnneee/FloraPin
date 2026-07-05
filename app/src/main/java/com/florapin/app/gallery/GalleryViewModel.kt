package com.florapin.app.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.FlowerRepository
import com.florapin.app.friends.FriendsBadgeStore
import com.florapin.app.identify.IdentifyBadgeStore
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.sync.SyncScheduler
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ordre de tri de la galerie (NODE-120). */
enum class GallerySort(val label: String) {
    DATE_DESC("Plus récentes"),
    DATE_ASC("Plus anciennes"),
    SPECIES("Espèce"),
}

/**
 * Expose la liste des fleurs persistées pour la galerie, filtrée par la
 * recherche courante et triée selon [sort] (NODE-120). Le filtrage/tri est fait
 * en mémoire — suffisant tant que le volume reste modeste (pagination : NODE-121).
 *
 * [AndroidViewModel] pour obtenir le contexte applicatif (la factory par défaut
 * suffit, pas besoin d'injection).
 */
class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FlowerRepository.from(application)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(GallerySort.DATE_DESC)
    val sort: StateFlow<GallerySort> = _sort.asStateFlow()

    val flowers: StateFlow<List<FlowerEntity>> =
        combine(repository.flowers, _query, _sort) { flowers, query, sort ->
            flowers.filterByQuery(query).sortedByOrder(sort)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setSort(sort: GallerySort) {
        _sort.value = sort
    }

    // --- Multi-sélection par appui long (TÂCHE 6.6) ---

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    /**
     * Identifiants locaux des fleurs actuellement sélectionnées. Un ensemble non
     * vide signale le « mode sélection » (barre d'actions contextuelle) ; on entre
     * dans ce mode par un appui long sur une vignette et on en sort dès qu'il
     * redevient vide.
     */
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    /** Bascule la présence d'une fleur dans la sélection (appui long / tap en mode sélection). */
    fun toggleSelection(id: Long) {
        _selectedIds.update { it.toggled(id) }
    }

    /** Sélectionne toutes les fleurs actuellement affichées (après filtre/tri). */
    fun selectAll() {
        _selectedIds.value = flowers.value.map { it.id }.toSet()
    }

    /** Quitte le mode sélection (vide la sélection). */
    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /**
     * Supprime en lot les fleurs sélectionnées. Device-first : chaque suppression
     * passe par [FlowerRepository.delete], qui pose un soft delete (deletedAt +
     * PENDING) pour les fleurs déjà connues du serveur — la sync propagera puis
     * purgera — et supprime physiquement celles jamais synchronisées. La sélection
     * est vidée une fois les suppressions demandées.
     */
    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id -> repository.getById(id)?.let { repository.delete(it) } }
            clearSelection()
        }
    }

    // --- Badges de nouveautés (demandes d'identification & d'amis non vues) ---

    private val apis by lazy {
        NetworkModule.createAuthenticated(EncryptedTokenStore(application))
    }
    private val identifyBadgeStore = IdentifyBadgeStore(application)
    private val friendsBadgeStore = FriendsBadgeStore(application)

    private val _identifyBadge = MutableStateFlow(0)
    /** Nombre de demandes d'identification d'amis non encore vues (0 = pas de badge). */
    val identifyBadge: StateFlow<Int> = _identifyBadge.asStateFlow()

    private val _friendsBadge = MutableStateFlow(0)
    /** Nombre de demandes d'amis entrantes non encore vues (0 = pas de badge). */
    val friendsBadge: StateFlow<Int> = _friendsBadge.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    /** Tirage manuel (pull-to-refresh) en cours (TÂCHE 1.3). */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Recalcule les badges : récupère les demandes courantes (identification et
     * amitiés entrantes) et compte celles non encore vues. Appelé à l'affichage de
     * la galerie (lancement et retour depuis ces écrans, qui auront marqué leurs
     * demandes comme vues). Silencieux hors-ligne / non connecté (on conserve la
     * dernière valeur connue).
     */
    fun refreshBadges() {
        viewModelScope.launch { loadBadges() }
    }

    /**
     * Pull-to-refresh de la galerie (TÂCHE 1.3). Device-first : la liste vient de
     * Room (Flow réactif, déjà à jour) — ce geste relance seulement une passe de
     * synchronisation cloud **si elle est activée** (jamais forcée : hors-ligne /
     * sync OFF restent silencieux) et rafraîchit les badges de nouveautés.
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // No-op si la sync auto est désactivée (device-first) : n'exige
                // jamais le réseau.
                SyncScheduler.syncNow(getApplication())
                loadBadges()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Recharge en parallèle les deux compteurs de badges, en attendant leur fin. */
    private suspend fun loadBadges() = coroutineScope {
        launch {
            try {
                val ids = apis.identification.listToIdentify().map { it.id }
                _identifyBadge.value = identifyBadgeStore.unseenCount(ids)
            } catch (_: Exception) {
                // hors-ligne / non connecté : on garde la valeur actuelle
            }
        }
        launch {
            try {
                val incomingIds = apis.friendships.list()
                    .filter { it.status == "pending" && it.direction == "incoming" }
                    .map { it.id }
                _friendsBadge.value = friendsBadgeStore.unseenCount(incomingIds)
            } catch (_: Exception) {
                // hors-ligne / non connecté : on garde la valeur actuelle
            }
        }
    }
}

/**
 * Filtre les fleurs dont l'espèce, les notes ou une étiquette contiennent
 * [query] (insensible à la casse). Requête vide ⇒ liste inchangée.
 */
internal fun List<FlowerEntity>.filterByQuery(query: String): List<FlowerEntity> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return this
    return filter { flower ->
        flower.species?.lowercase()?.contains(needle) == true ||
            flower.notes.lowercase().contains(needle) ||
            flower.tags.any { it.lowercase().contains(needle) }
    }
}

/** Ajoute ou retire [id] de l'ensemble de sélection (bascule idempotente). */
internal fun Set<Long>.toggled(id: Long): Set<Long> =
    if (id in this) this - id else this + id

/** Trie les fleurs selon [sort] ; les espèces vides passent en dernier. */
internal fun List<FlowerEntity>.sortedByOrder(sort: GallerySort): List<FlowerEntity> =
    when (sort) {
        GallerySort.DATE_DESC -> sortedByDescending { it.createdAt }
        GallerySort.DATE_ASC -> sortedBy { it.createdAt }
        GallerySort.SPECIES -> sortedWith(
            compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { flower ->
                flower.species?.takeIf { it.isNotBlank() }
            },
        )
    }
