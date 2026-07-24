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
import com.florapin.app.network.dto.NotificationDto
import com.florapin.app.sync.SyncPreferences
import com.florapin.app.sync.SyncScheduler
import com.florapin.app.util.formatMonthLabel
import com.florapin.app.util.monthKey
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

    init {
        // Filet de sécurité pour l'annulation de suppression (TÂCHE 6.13) : purge
        // les soft-deletes locaux dont la fenêtre d'annulation est écoulée mais
        // qui n'ont jamais été finalisés (app tuée pendant la fenêtre, détail
        // ouvert hors galerie…). Sans ça, ces fleurs jamais synchronisées
        // resteraient masquées indéfiniment.
        viewModelScope.launch {
            repository.purgeExpiredLocalDeletions(
                System.currentTimeMillis() - UNDO_WINDOW_MS,
            )
        }
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(GallerySort.DATE_DESC)
    val sort: StateFlow<GallerySort> = _sort.asStateFlow()

    // Densité de la grille (TÂCHE 6.8) : store dédié, persistée par appareil.
    private val densityStore = GalleryDensityStore(application)
    private val _density = MutableStateFlow(densityStore.density())
    /** Palier de densité de la grille courant (persisté). */
    val density: StateFlow<GalleryDensity> = _density.asStateFlow()

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

    /** Change la densité de la grille et persiste le choix (TÂCHE 6.8). */
    fun setDensity(density: GalleryDensity) {
        _density.value = density
        densityStore.setDensity(density)
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

    // --- Annuler la suppression d'une fleur (TÂCHE 6.13) ---

    /**
     * Annule la suppression d'une fleur (tap sur « Annuler » du snackbar) : lève
     * le soft-delete posé depuis le détail. La fleur réapparaît immédiatement dans
     * la galerie (flux Room réactif). Aucune sync n'ayant été déclenchée entre
     * temps, rien n'a été propagé au serveur : la restauration reste purement
     * locale.
     */
    fun undoDelete(id: Long) {
        viewModelScope.launch { repository.restore(id) }
    }

    /**
     * Finalise la suppression d'une fleur quand la fenêtre d'annulation s'est
     * écoulée sans annulation (snackbar fermé/expiré). Purge physique si la fleur
     * n'a jamais été synchronisée ; sinon, propage le soft-delete au serveur via
     * une passe de sync (no-op si la sync est désactivée). No-op si la fleur a été
     * restaurée entre-temps.
     */
    fun finalizeDelete(id: Long) {
        viewModelScope.launch {
            val flower = repository.getById(id) ?: return@launch
            if (flower.deletedAt == null) return@launch
            repository.finalizeDelete(flower)
            SyncScheduler.syncNow(getApplication())
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

    private var unreadCommentNotificationIdsByFlower: Map<String, List<String>> = emptyMap()
    private val _unreadCommentFlowerIds = MutableStateFlow<Set<String>>(emptySet())
    /** Identifiants serveur des fleurs ayant au moins un commentaire non lu. */
    val unreadCommentFlowerIds: StateFlow<Set<String>> = _unreadCommentFlowerIds.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    /** Tirage manuel (pull-to-refresh) en cours (TÂCHE 1.3). */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val syncPrefs = SyncPreferences(application)
    private val _syncEnabled = MutableStateFlow(syncPrefs.isEnabled())
    /**
     * Synchronisation automatique activée (TÂCHE 6.14). Pilote l'affichage du
     * badge « en attente » sur les vignettes : device-first, une fleur PENDING est
     * l'état de repos normal quand la sync est OFF — on ne la signale donc que si
     * la sync auto est active (sinon le badge serait faux et permanent). Réévalué
     * au (ré)affichage de la galerie via [refreshBadges].
     */
    val syncEnabled: StateFlow<Boolean> = _syncEnabled.asStateFlow()

    /**
     * Recalcule les badges : récupère les demandes courantes (identification et
     * amitiés entrantes) et compte celles non encore vues. Appelé à l'affichage de
     * la galerie (lancement et retour depuis ces écrans, qui auront marqué leurs
     * demandes comme vues). Silencieux hors-ligne / non connecté (on conserve la
     * dernière valeur connue).
     */
    fun refreshBadges() {
        // Réévalue aussi l'état de la sync auto : il a pu changer dans Profil
        // pendant qu'on était hors de la galerie (pilote le badge « en attente »).
        _syncEnabled.value = syncPrefs.isEnabled()
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

    /**
     * Retire immédiatement le marqueur d'une fleur ouverte, puis marque côté
     * serveur toutes ses notifications de commentaire comme lues.
     */
    fun markCommentNotificationsRead(flowerServerId: String?) {
        val serverId = flowerServerId ?: return
        val notificationIds = unreadCommentNotificationIdsByFlower[serverId].orEmpty()
        unreadCommentNotificationIdsByFlower -= serverId
        _unreadCommentFlowerIds.value = unreadCommentNotificationIdsByFlower.keys
        if (notificationIds.isEmpty()) return

        viewModelScope.launch {
            notificationIds.forEach { id ->
                runCatching { apis.notifications.markRead(id) }
            }
        }
    }

    /** Recharge en parallèle les compteurs et marqueurs, en attendant leur fin. */
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
        launch {
            try {
                val unreadByFlower = apis.notifications.list()
                    .unreadCommentNotificationIdsByFlower()
                unreadCommentNotificationIdsByFlower = unreadByFlower
                _unreadCommentFlowerIds.value = unreadByFlower.keys
            } catch (_: Exception) {
                // Hors-ligne / non connecté : on garde les marqueurs actuels.
            }
        }
    }

    companion object {
        /**
         * Fenêtre d'annulation d'une suppression (TÂCHE 6.13). Sert de seuil au
         * balayage de sécurité : un soft-delete local plus vieux que ce délai et
         * jamais finalisé est purgé au prochain affichage de la galerie. Pris large
         * (bien au-delà de la durée d'un snackbar) pour ne jamais purger une fleur
         * qu'un snackbar encore visible pourrait restaurer.
         */
        private const val UNDO_WINDOW_MS = 60_000L
    }
}

private val COMMENT_NOTIFICATION_TYPES = setOf("flower_commented", "comment_mention")

/** Regroupe les notifications de commentaire non lues par identifiant serveur de fleur. */
internal fun List<NotificationDto>.unreadCommentNotificationIdsByFlower(): Map<String, List<String>> =
    asSequence()
        .filter { it.readAt == null && it.type in COMMENT_NOTIFICATION_TYPES }
        .mapNotNull { notification ->
            notification.flowerServerId?.let { it to notification.id }
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })

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

/**
 * Une ligne de la galerie une fois aplatie (TÂCHE 6.7) : soit un en-tête de mois
 * (occupant toute la largeur de la grille), soit une vignette de fleur. Aplatir
 * en une seule liste garantit que l'index d'une ligne coïncide avec son index
 * dans la [androidx.compose.foundation.lazy.grid.LazyGridState] — ce que le fast
 * scroller exploite pour se positionner.
 */
sealed interface GalleryRow {
    /** Clé de mois "yyyy-MM" de la ligne. */
    val monthKey: String

    /** En-tête introduisant un mois de capture. */
    data class MonthHeader(override val monthKey: String, val label: String) : GalleryRow

    /** Vignette d'une fleur, rattachée à son mois de capture. */
    data class Flower(val flower: FlowerEntity, override val monthKey: String) : GalleryRow
}

/**
 * Aplati la liste (supposée déjà triée par date) en lignes groupées par mois de
 * **capture** ([FlowerEntity.createdAt] = date de prise de vue), en insérant un
 * en-tête à chaque changement de mois. L'ordre des fleurs est conservé tel quel.
 */
internal fun List<FlowerEntity>.groupedByMonth(): List<GalleryRow> {
    val rows = ArrayList<GalleryRow>(size + 8)
    var currentKey: String? = null
    for (flower in this) {
        val key = monthKey(flower.createdAt)
        if (key != currentKey) {
            rows += GalleryRow.MonthHeader(key, formatMonthLabel(flower.createdAt))
            currentKey = key
        }
        rows += GalleryRow.Flower(flower, key)
    }
    return rows
}

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
