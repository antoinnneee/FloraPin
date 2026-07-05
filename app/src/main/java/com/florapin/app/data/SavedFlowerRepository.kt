package com.florapin.app.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * Persistance de « Ma sélection » (TÂCHE 3.11) : fleurs d'amis enregistrées en
 * favori PRIVÉ et LOCAL. Aucune synchronisation serveur — chaque snapshot est
 * autonome (cf. [SavedFlowerEntity]) pour survivre au hors-ligne et à la
 * révocation d'un partage.
 */
class SavedFlowerRepository(private val dao: SavedFlowerDao) {

    /** Snapshots enregistrés, observés en continu (plus récents d'abord). */
    val saved: Flow<List<SavedFlowerEntity>> = dao.observeAll()

    /** Ids serveur enregistrés, pour marquer les cartes du feed. */
    val savedIds: Flow<List<String>> = dao.observeSavedIds()

    suspend fun getByServerId(serverId: String): SavedFlowerEntity? =
        dao.getByServerId(serverId)

    /** Enregistre (ou remplace) le snapshot d'une fleur d'ami. */
    suspend fun save(entity: SavedFlowerEntity) = dao.insert(entity)

    /**
     * Retire une fleur de la sélection et supprime sa miniature en cache (le
     * fichier local devient inutile).
     */
    suspend fun remove(serverId: String) {
        dao.getByServerId(serverId)?.let { existing ->
            if (existing.imagePath.isNotEmpty()) {
                runCatching { File(existing.imagePath).delete() }
            }
        }
        dao.deleteByServerId(serverId)
    }

    /** Purge toute la sélection et ses miniatures (suppression de compte — NODE-93). */
    suspend fun deleteAll() {
        dao.all().forEach { entity ->
            if (entity.imagePath.isNotEmpty()) {
                runCatching { File(entity.imagePath).delete() }
            }
        }
        dao.deleteAll()
    }

    companion object {
        /** Répertoire privé des miniatures enregistrées (séparé des captures). */
        fun imagesDir(context: Context): File =
            File(context.applicationContext.filesDir, "saved_flowers").apply { mkdirs() }

        fun from(context: Context): SavedFlowerRepository =
            SavedFlowerRepository(FloraDatabase.getInstance(context).savedFlowerDao())
    }
}
