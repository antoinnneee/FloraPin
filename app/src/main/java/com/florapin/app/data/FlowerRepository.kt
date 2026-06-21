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
        ),
    )

    suspend fun updateNotes(flower: FlowerEntity, notes: String) =
        dao.update(flower.copy(notes = notes))

    suspend fun delete(flower: FlowerEntity) = dao.delete(flower)

    companion object {
        /** Construit un repository câblé sur la base singleton. */
        fun from(context: Context): FlowerRepository =
            FlowerRepository(FloraDatabase.getInstance(context).flowerDao())
    }
}
