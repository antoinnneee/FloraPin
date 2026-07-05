package com.florapin.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Point d'accès unique à la persistance des albums (NODE-102) : CRUD local
 * orienté usage + helpers de synchronisation. Toute mutation utilisateur repasse
 * l'album en attente de sync (PENDING).
 */
class AlbumRepository(
    private val dao: AlbumDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    /** Albums non supprimés, observés en continu (plus récents d'abord). */
    val albums: Flow<List<AlbumEntity>> = dao.observeAll()

    suspend fun getById(id: Long): AlbumEntity? = dao.getById(id)

    fun observeById(id: Long): Flow<AlbumEntity?> = dao.observeById(id)

    fun flowersIn(albumId: Long): Flow<List<FlowerEntity>> =
        dao.observeFlowersInAlbum(albumId)

    suspend fun memberFlowerIds(albumId: Long): List<Long> =
        dao.memberFlowerIds(albumId)

    // --- Mutations utilisateur ---

    /** Crée un album local (à synchroniser). */
    suspend fun create(name: String): Long {
        val ts = now()
        return dao.insert(
            AlbumEntity(
                clientId = java.util.UUID.randomUUID().toString(),
                name = name,
                createdAt = ts,
                updatedAt = ts,
                syncState = SyncState.PENDING.name,
            ),
        )
    }

    suspend fun rename(album: AlbumEntity, name: String) = dao.update(
        album.copy(
            name = name,
            syncState = SyncState.PENDING.name,
            updatedAt = now(),
        ),
    )

    /** Marque l'album supprimé (soft-delete propagé au prochain push). */
    suspend fun delete(album: AlbumEntity) {
        val ts = now()
        if (album.serverId == null) {
            // Jamais synchronisé : suppression directe.
            dao.clearMembers(album.id)
            dao.deleteById(album.id)
        } else {
            dao.update(
                album.copy(
                    deletedAt = ts,
                    updatedAt = ts,
                    syncState = SyncState.PENDING.name,
                ),
            )
        }
    }

    suspend fun addFlower(albumId: Long, flowerLocalId: Long) {
        dao.addCrossRef(FlowerAlbumCrossRef(albumId, flowerLocalId))
        touch(albumId)
    }

    suspend fun removeFlower(albumId: Long, flowerLocalId: Long) {
        dao.removeMember(albumId, flowerLocalId)
        touch(albumId)
    }

    /** Repasse l'album en attente de sync après un changement d'appartenance. */
    private suspend fun touch(albumId: Long) {
        val album = dao.getById(albumId) ?: return
        dao.update(album.copy(syncState = SyncState.PENDING.name, updatedAt = now()))
    }

    // --- Helpers de synchronisation (AlbumSyncEngine) ---

    suspend fun pendingSync(): List<AlbumEntity> = dao.pendingSync()

    suspend fun allActive(): List<AlbumEntity> = dao.allActive()

    /** Toutes les appartenances fleur ↔ album (dump de sauvegarde locale). */
    suspend fun allCrossRefsForBackup(): List<FlowerAlbumCrossRef> = dao.allCrossRefs()

    /**
     * Recrée une appartenance à l'import d'une sauvegarde, sans repasser l'album
     * en attente de sync (contrairement à [addFlower]) : la sauvegarde restaure
     * un état déjà synchronisé, on ne veut pas le re-pousser.
     */
    suspend fun addCrossRefForBackup(albumId: Long, flowerLocalId: Long) =
        dao.addCrossRef(FlowerAlbumCrossRef(albumId, flowerLocalId))

    suspend fun findByServerId(serverId: String): AlbumEntity? =
        dao.findByServerId(serverId)

    suspend fun findByClientId(clientId: String): AlbumEntity? =
        dao.findByClientId(clientId)

    suspend fun insert(album: AlbumEntity): Long = dao.insert(album)

    suspend fun update(album: AlbumEntity) = dao.update(album)

    suspend fun markSynced(localId: Long, serverId: String, updatedAt: Long = now()) =
        dao.markSynced(localId, serverId, updatedAt)

    suspend fun hardDelete(album: AlbumEntity) {
        dao.clearMembers(album.id)
        dao.deleteById(album.id)
    }

    suspend fun memberFlowerServerIds(albumId: Long): List<String> =
        dao.memberFlowerServerIds(albumId)

    /** Remplace l'appartenance locale d'un album par l'ensemble fourni. */
    suspend fun setMembers(albumId: Long, flowerLocalIds: List<Long>) {
        dao.clearMembers(albumId)
        flowerLocalIds.forEach { dao.addCrossRef(FlowerAlbumCrossRef(albumId, it)) }
    }

    /** Purge tous les albums et appartenances (déconnexion — NODE-93). */
    suspend fun deleteAll() {
        dao.deleteAllCrossRefs()
        dao.deleteAllAlbums()
    }

    companion object {
        fun from(context: Context): AlbumRepository =
            AlbumRepository(FloraDatabase.getInstance(context).albumDao())
    }
}
