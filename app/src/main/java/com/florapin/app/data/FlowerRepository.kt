package com.florapin.app.data

import android.content.Context
import com.florapin.app.location.GeoPoint
import kotlinx.coroutines.flow.Flow

/**
 * Point d'accès unique à la persistance des fleurs : masque Room derrière une
 * API orientée usage.
 */
class FlowerRepository(private val dao: FlowerDao) {

    /** Toutes les fleurs, observées en continu (plus récentes d'abord). */
    val flowers: Flow<List<FlowerEntity>> = dao.observeAll()

    suspend fun getById(id: Long): FlowerEntity? = dao.getById(id)

    /** Observe une fleur en continu (null si supprimée/inexistante). */
    fun observeById(id: Long): Flow<FlowerEntity?> = dao.observeById(id)

    /**
     * Persiste une capture : image + position (optionnelle) + horodatage.
     * @return l'identifiant de la fleur créée.
     */
    suspend fun saveCapture(
        imagePath: String,
        location: GeoPoint?,
        createdAt: Long = System.currentTimeMillis(),
        notes: String = "",
    ): Long = dao.insert(
        FlowerEntity(
            imagePath = imagePath,
            latitude = location?.latitude,
            longitude = location?.longitude,
            accuracyMeters = location?.accuracyMeters,
            createdAt = createdAt,
            notes = notes,
            // Nouvelle capture : à synchroniser.
            syncState = SyncState.PENDING.name,
            updatedAt = createdAt,
        ),
    )

    suspend fun updateNotes(flower: FlowerEntity, notes: String) =
        dao.update(
            flower.copy(
                notes = notes,
                syncState = SyncState.PENDING.name,
                updatedAt = System.currentTimeMillis(),
            ),
        )

    /**
     * Met à jour l'espèce et les étiquettes (les remet en attente de sync).
     *
     * Quand l'espèce provient du référentiel (NODE-128), [speciesId] et le cache
     * d'affichage ([speciesScientificName]/[speciesCommonName]) sont renseignés ;
     * pour une saisie libre, ils valent null et seul le texte [species] subsiste.
     */
    suspend fun updateClassification(
        flower: FlowerEntity,
        species: String?,
        tags: List<String>,
        speciesId: String? = null,
        speciesScientificName: String? = null,
        speciesCommonName: String? = null,
    ) = dao.update(
        flower.copy(
            species = species?.ifBlank { null },
            speciesId = speciesId,
            speciesScientificName = speciesScientificName,
            speciesCommonName = speciesCommonName,
            tags = tags,
            syncState = SyncState.PENDING.name,
            updatedAt = System.currentTimeMillis(),
        ),
    )

    /**
     * Publie ou retire la fleur du flux d'amis (NODE-137) : bascule la visibilité
     * entre 'friends' et 'private' et règle la diffusion GPS, puis remet la fleur
     * en attente de synchronisation.
     */
    suspend fun updateFeedVisibility(
        flower: FlowerEntity,
        published: Boolean,
        includeGps: Boolean,
    ) = dao.update(
        flower.copy(
            visibility = if (published) "friends" else "private",
            feedIncludeGps = includeGps,
            syncState = SyncState.PENDING.name,
            updatedAt = System.currentTimeMillis(),
        ),
    )

    /** Flux des fleurs d'une espèce (rattachées ou texte = nom scientifique). */
    fun observeBySpecies(speciesId: String?, scientificName: String?) =
        dao.observeBySpecies(speciesId, scientificName)

    suspend fun delete(flower: FlowerEntity) = dao.delete(flower)

    /** Purge toutes les fleurs locales (déconnexion / changement de compte). */
    suspend fun deleteAll() = dao.deleteAll()

    // --- Synchronisation (NODE-43) ---

    /** Fleurs locales restant à synchroniser. */
    suspend fun pendingSync(): List<FlowerEntity> = dao.pendingSync()

    /** Marque une fleur comme synchronisée et associe son id serveur. */
    suspend fun markSynced(
        localId: Long,
        serverId: String,
        updatedAt: Long = System.currentTimeMillis(),
    ) = dao.markSynced(localId, serverId, updatedAt)

    suspend fun markFailed(localId: Long) = dao.markFailed(localId)

    /**
     * Renseigne le chemin local après mise en cache d'une image distante : Coil
     * affiche désormais le fichier local, sans dépendre de l'expiration de l'URL
     * présignée. N'altère pas l'état de sync.
     */
    suspend fun cacheImagePath(localId: Long, path: String) =
        dao.setImagePath(localId, path)

    suspend fun findByServerId(serverId: String): FlowerEntity? =
        dao.findByServerId(serverId)

    /** Capture locale (image présente) à une date de capture donnée, ou null. */
    suspend fun findLocalTwin(createdAt: Long): FlowerEntity? =
        dao.findLocalTwin(createdAt)

    /** Insère une fleur distante reçue par sync (autre appareil). */
    suspend fun insert(flower: FlowerEntity): Long = dao.insert(flower)

    /** Réécrit une ligne existante (réconciliation depuis le serveur). */
    suspend fun update(flower: FlowerEntity) = dao.update(flower)

    suspend fun softDeleteByServerId(serverId: String, deletedAt: Long) =
        dao.softDeleteByServerId(serverId, deletedAt)

    companion object {
        /** Construit un repository câblé sur la base singleton. */
        fun from(context: Context): FlowerRepository =
            FlowerRepository(FloraDatabase.getInstance(context).flowerDao())
    }
}
