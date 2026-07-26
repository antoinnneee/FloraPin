package com.florapin.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.florapin.app.network.auth.SessionManager
import com.florapin.app.network.dto.AlbumDto
import com.florapin.app.network.dto.CreateAlbumRequest
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.MyIdentificationRequestDto
import com.florapin.app.network.dto.ProposeSpeciesRequest
import com.florapin.app.network.dto.SetAlbumCoverRequest
import com.florapin.app.network.dto.SpeciesDto
import com.florapin.app.network.dto.UpdateAlbumRequest
import com.florapin.app.network.dto.UpdateFlowerRequest
import com.florapin.app.network.dto.UserDto
import com.florapin.desktop.core.CompanionApis
import com.florapin.desktop.core.DesktopNetwork
import com.florapin.desktop.core.FileTokenStore
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** Sections principales, exposées par le rail de navigation et Ctrl+1…6. */
enum class Section(val label: String) {
    LIBRARY("Photothèque"),
    ALBUMS("Albums"),
    MAP("Carte"),
    IDENTIFY("Identification"),
    SHARED("Partagées avec moi"),
    SETTINGS("Réglages"),
}

/** Critère de tri de la photothèque. */
enum class SortOrder(val label: String) {
    DATE_DESC("Plus récentes"),
    DATE_ASC("Plus anciennes"),
    SPECIES("Espèce"),
}

/**
 * État et actions du compagnon.
 *
 * Contrairement à l'app Android, aucune base locale : le compagnon lit l'API et
 * garde le résultat en mémoire pour la durée de la session. C'est le bon
 * compromis pour un poste fixe — le cache disque des images suffit à rendre la
 * navigation fluide, et l'on évite de dupliquer la logique de synchronisation
 * et de résolution de conflits.
 */
class AppModel(private val scope: CoroutineScope) {

    val tokenStore = FileTokenStore()
    val preferences = Preferences()
    val selection = Selection()

    private var apis: CompanionApis = DesktopNetwork.create(tokenStore)
    private var session: SessionManager = DesktopNetwork.sessionManager(apis, tokenStore)

    // ── Session ────────────────────────────────────────────────────────────
    var user by mutableStateOf<UserDto?>(null)
        private set
    var restoringSession by mutableStateOf(true)
        private set
    var signingIn by mutableStateOf(false)
        private set
    var signInError by mutableStateOf<String?>(null)

    // ── Données ────────────────────────────────────────────────────────────
    var myFlowers by mutableStateOf<List<FlowerDto>>(emptyList())
        private set
    var sharedFlowers by mutableStateOf<List<FlowerDto>>(emptyList())
        private set
    var albums by mutableStateOf<List<AlbumDto>>(emptyList())
        private set
    var toIdentify by mutableStateOf<List<FlowerDto>>(emptyList())
        private set
    var myIdentificationRequests by mutableStateOf<List<MyIdentificationRequestDto>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set

    /** Message transitoire affiché dans la barre d'état (erreur ou confirmation). */
    var status by mutableStateOf<String?>(null)

    // ── Navigation et filtres ──────────────────────────────────────────────
    private var currentSection by mutableStateOf(
        runCatching { Section.valueOf(preferences.lastSection) }.getOrDefault(Section.LIBRARY),
    )

    /** Section affichée ; mémorisée pour le prochain démarrage. */
    var section: Section
        get() = currentSection
        set(value) {
            currentSection = value
            preferences.lastSection = value.name
        }

    var query by mutableStateOf("")
    var sortOrder by mutableStateOf(SortOrder.DATE_DESC)
    var onlyUnidentified by mutableStateOf(false)
    var selectedAlbumId by mutableStateOf<String?>(null)

    /** Fleur ouverte en visionneuse plein écran, ou null. */
    var viewerFlowerId by mutableStateOf<String?>(null)

    /**
     * Photos de la section courante, filtrées et triées. Source unique de la
     * grille, de la carte et des actions groupées : ce que l'utilisateur voit
     * est exactement ce sur quoi Ctrl+A porte.
     */
    val visibleFlowers: List<FlowerDto>
        get() {
            val base = when (section) {
                Section.SHARED -> sharedFlowers
                Section.ALBUMS -> selectedAlbum()?.let { album ->
                    // L'API renvoie les fleurs de l'album, y compris celles des
                    // autres membres d'un album collaboratif.
                    album.flowers.ifEmpty { myFlowers.filter { it.id in album.flowerIds } }
                } ?: emptyList()
                Section.MAP -> myFlowers + sharedFlowers
                else -> myFlowers
            }
            return base.filter(::matchesFilters).sortedWith(comparator())
        }

    /** Toutes les fleurs connues, pour retrouver une fiche par identifiant. */
    fun flowerById(id: String): FlowerDto? =
        myFlowers.firstOrNull { it.id == id }
            ?: sharedFlowers.firstOrNull { it.id == id }
            ?: toIdentify.firstOrNull { it.id == id }
            ?: albums.firstNotNullOfOrNull { album -> album.flowers.firstOrNull { it.id == id } }

    fun selectedAlbum(): AlbumDto? = albums.firstOrNull { it.id == selectedAlbumId }

    /** Vrai si la fleur appartient au compte connecté (actions d'édition). */
    fun isMine(flower: FlowerDto): Boolean = flower.ownerId == user?.id

    private fun matchesFilters(flower: FlowerDto): Boolean {
        if (onlyUnidentified && !flower.needsIdentification) return false
        val q = query.trim()
        if (q.isEmpty()) return true
        return listOfNotNull(
            flower.species,
            flower.speciesRef?.commonName,
            flower.speciesRef?.scientificName,
            flower.notes,
            flower.ownerName,
        ).any { it.contains(q, ignoreCase = true) } ||
            flower.tags.any { it.contains(q, ignoreCase = true) }
    }

    private fun comparator(): Comparator<FlowerDto> = when (sortOrder) {
        SortOrder.DATE_DESC -> compareByDescending { it.takenAt }
        SortOrder.DATE_ASC -> compareBy { it.takenAt }
        SortOrder.SPECIES -> compareBy(
            { (it.speciesRef?.commonName ?: it.species ?: "￿").lowercase() },
            { it.takenAt },
        )
    }

    // ── Cycle de vie ───────────────────────────────────────────────────────

    /** Rétablit la session au lancement si un refresh token est présent. */
    fun restoreSession() {
        scope.launch {
            restoringSession = true
            if (tokenStore.refreshToken() != null) {
                runCatching { apis.auth.me() }
                    .onSuccess {
                        user = it
                        refreshAll()
                    }
                    .onFailure {
                        // Jeton périmé ou serveur injoignable : on retombe sur
                        // l'écran de connexion sans effacer la session, une
                        // panne réseau ne devant pas obliger à se reconnecter.
                        if (it is HttpException && it.code() in listOf(401, 403)) tokenStore.clear()
                    }
            }
            restoringSession = false
        }
    }

    fun signIn(email: String, password: String) {
        scope.launch {
            signingIn = true
            signInError = null
            try {
                user = session.login(email.trim(), password)
                tokenStore.saveLastEmail(email.trim())
                refreshAll()
            } catch (e: Exception) {
                signInError = readableError(e, "Connexion impossible")
            } finally {
                signingIn = false
            }
        }
    }

    fun signOut() {
        scope.launch {
            runCatching { session.logout() }
            user = null
            myFlowers = emptyList()
            sharedFlowers = emptyList()
            albums = emptyList()
            toIdentify = emptyList()
            myIdentificationRequests = emptyList()
            selection.clear()
            section = Section.LIBRARY
        }
    }

    /** Recharge tout (F5). Les blocs sont indépendants : un échec n'annule pas le reste. */
    fun refreshAll() {
        scope.launch {
            loading = true
            runCatching { apis.flowers.list() }
                .onSuccess { myFlowers = it }
                .onFailure { status = readableError(it, "Photos non chargées") }
            runCatching { apis.shares.sharedWithMe() }.onSuccess { sharedFlowers = it }
            runCatching { apis.albums.list() }.onSuccess { albums = it }
            runCatching { apis.identification.listToIdentify() }.onSuccess { toIdentify = it }
            runCatching { apis.identification.listMyRequests() }
                .onSuccess { myIdentificationRequests = it }
            selection.retain(
                (myFlowers + sharedFlowers).mapTo(mutableSetOf()) { it.id },
            )
            loading = false
        }
    }

    // ── Albums ─────────────────────────────────────────────────────────────

    fun createAlbum(name: String) = run("Album « $name » créé") {
        val album = apis.albums.create(
            CreateAlbumRequest(name = name.trim(), clientId = UUID.randomUUID().toString()),
        )
        albums = albums + album
        selectedAlbumId = album.id
    }

    fun renameAlbum(id: String, name: String) = run("Album renommé") {
        val updated = apis.albums.rename(id, UpdateAlbumRequest(name.trim()))
        albums = albums.map { if (it.id == id) updated else it }
    }

    fun deleteAlbum(id: String) = run("Album supprimé") {
        apis.albums.delete(id)
        albums = albums.filterNot { it.id == id }
        if (selectedAlbumId == id) selectedAlbumId = null
    }

    fun addToAlbum(albumId: String, flowerIds: Collection<String>) {
        val album = albums.firstOrNull { it.id == albumId } ?: return
        val toAdd = flowerIds.filterNot { it in album.flowerIds }
        if (toAdd.isEmpty()) {
            status = "Déjà dans « ${album.name} »"
            return
        }
        run("${toAdd.size} photo(s) ajoutée(s) à « ${album.name} »") {
            var latest = album
            toAdd.forEach { flowerId ->
                latest = apis.albums.addFlower(
                    albumId,
                    com.florapin.app.network.dto.AddFlowerToAlbumRequest(flowerId),
                )
            }
            albums = albums.map { if (it.id == albumId) latest else it }
        }
    }

    fun removeFromAlbum(albumId: String, flowerIds: Collection<String>) =
        run("${flowerIds.size} photo(s) retirée(s) de l'album") {
            var latest: AlbumDto? = null
            flowerIds.forEach { latest = apis.albums.removeFlower(albumId, it) }
            latest?.let { updated -> albums = albums.map { if (it.id == albumId) updated else it } }
        }

    fun setAlbumCover(albumId: String, flowerId: String?) = run("Couverture mise à jour") {
        val updated = apis.albums.setCover(albumId, SetAlbumCoverRequest(flowerId))
        albums = albums.map { if (it.id == albumId) updated else it }
    }

    // ── Fleurs ─────────────────────────────────────────────────────────────

    fun deleteFlowers(ids: Collection<String>) = run("${ids.size} photo(s) supprimée(s)") {
        ids.forEach { apis.flowers.delete(it) }
        myFlowers = myFlowers.filterNot { it.id in ids }
        selection.retain(myFlowers.mapTo(mutableSetOf()) { it.id })
    }

    fun updateFlower(id: String, notes: String? = null, species: String? = null) =
        run("Fiche mise à jour") {
            val updated = apis.flowers.update(
                id,
                UpdateFlowerRequest(notes = notes, species = species),
            )
            myFlowers = myFlowers.map { if (it.id == id) updated else it }
        }

    // ── Identification collaborative ───────────────────────────────────────

    fun requestIdentification(ids: Collection<String>) =
        run("Demande d'identification envoyée à vos amis") {
            ids.forEach { apis.identification.request(it) }
            myFlowers = apis.flowers.list()
            myIdentificationRequests = apis.identification.listMyRequests()
        }

    fun cancelIdentification(flowerId: String) = run("Demande annulée") {
        apis.identification.cancel(flowerId)
        myFlowers = apis.flowers.list()
        myIdentificationRequests = apis.identification.listMyRequests()
    }

    fun proposeSpecies(flowerId: String, species: String) = run("Proposition envoyée") {
        apis.identification.propose(flowerId, ProposeSpeciesRequest(species.trim()))
        toIdentify = apis.identification.listToIdentify()
    }

    fun acceptProposal(flowerId: String, proposalId: String) = run("Espèce appliquée") {
        apis.identification.acceptProposal(flowerId, proposalId)
        myFlowers = apis.flowers.list()
        myIdentificationRequests = apis.identification.listMyRequests()
    }

    fun rejectProposal(flowerId: String, proposalId: String) = run("Proposition refusée") {
        apis.identification.rejectProposal(flowerId, proposalId)
        myIdentificationRequests = apis.identification.listMyRequests()
    }

    fun thankProposal(flowerId: String, proposalId: String) = run("Merci envoyé 🌸") {
        apis.identification.thankProposal(flowerId, proposalId)
        myIdentificationRequests = apis.identification.listMyRequests()
    }

    /** Autocomplétion du référentiel d'espèces (champ de proposition). */
    suspend fun searchSpecies(term: String): List<SpeciesDto> =
        if (term.trim().length < 2) {
            emptyList()
        } else {
            runCatching { apis.species.search(term.trim(), limit = 8) }.getOrDefault(emptyList())
        }

    // ── Utilitaires ────────────────────────────────────────────────────────

    /**
     * Exécute une action distante en signalant l'issue dans la barre d'état.
     * Toutes les mutations passent par ici pour que rien n'échoue en silence.
     */
    private fun run(successMessage: String, block: suspend () -> Unit) {
        scope.launch {
            loading = true
            try {
                block()
                status = successMessage
            } catch (e: Exception) {
                status = readableError(e, "Action impossible")
            } finally {
                loading = false
            }
        }
    }

    private fun readableError(error: Throwable, prefix: String): String = when {
        error is IOException -> "$prefix : serveur injoignable. Vérifiez votre connexion."
        error is HttpException && error.code() == 401 ->
            "$prefix : identifiants refusés."
        error is HttpException && error.code() == 403 ->
            "$prefix : action non autorisée."
        error is HttpException && error.code() == 404 ->
            "$prefix : élément introuvable (peut-être déjà supprimé)."
        error is HttpException && error.code() == 409 ->
            "$prefix : trop tôt, réessayez plus tard."
        error is HttpException -> "$prefix (erreur ${error.code()})."
        else -> "$prefix : ${error.message ?: "erreur inattendue"}"
    }
}
