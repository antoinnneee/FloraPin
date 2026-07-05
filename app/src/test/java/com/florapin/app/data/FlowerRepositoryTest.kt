package com.florapin.app.data

import com.florapin.app.location.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Vérifie le mapping capture → entité dans [FlowerRepository], sans Room réel
 * (DAO factice qui mémorise la dernière insertion).
 */
class FlowerRepositoryTest {

    private class FakeFlowerDao : FlowerDao {
        var lastInserted: FlowerEntity? = null
        var lastUpdated: FlowerEntity? = null
        var lastDeleted: FlowerEntity? = null

        override fun observeAll(): Flow<List<FlowerEntity>> = flowOf(emptyList())
        override suspend fun getById(id: Long): FlowerEntity? = null
        override fun observeById(id: Long): Flow<FlowerEntity?> = flowOf(null)
        override fun observeBySpecies(
            speciesId: String?,
            scientificName: String?,
        ): Flow<List<FlowerEntity>> = flowOf(emptyList())
        override suspend fun insert(flower: FlowerEntity): Long {
            lastInserted = flower
            return 42L
        }

        override suspend fun update(flower: FlowerEntity) {
            lastUpdated = flower
        }
        override suspend fun delete(flower: FlowerEntity) {
            lastDeleted = flower
        }
        override suspend fun deleteAll() = Unit
        override suspend fun pendingSync(): List<FlowerEntity> = emptyList()
        override suspend fun markSynced(
            id: Long,
            serverId: String,
            updatedAt: Long,
            expectedUpdatedAt: Long,
        ) = Unit
        override suspend fun markFailed(id: Long) = Unit
        override suspend fun setImagePath(id: Long, path: String) = Unit
        override suspend fun findByServerId(serverId: String): FlowerEntity? = null
        override suspend fun allActive(): List<FlowerEntity> = emptyList()
        override suspend fun findLocalTwin(createdAt: Long): FlowerEntity? = null
        override suspend fun softDeleteByServerId(serverId: String, deletedAt: Long) =
            Unit
        override suspend fun pendingImageUploads(): List<FlowerEntity> = emptyList()
        override suspend fun setImagePendingUpload(id: Long, pending: Boolean) = Unit
        override suspend fun countActive(): Int = 0
        override suspend fun countDistinctSpecies(): Int = 0
        override suspend fun geoTimes(): List<FlowerGeoTime> = emptyList()
    }

    @Test
    fun saveCapture_marksPending() = runBlocking {
        val dao = FakeFlowerDao()
        val repository = FlowerRepository(dao)
        repository.saveCapture(imagePath = "/p.jpg", location = null, createdAt = 7L)
        assertEquals(SyncState.PENDING.name, dao.lastInserted!!.syncState)
        assertEquals(7L, dao.lastInserted!!.updatedAt)
    }

    @Test
    fun saveCapture_mapsLocationFields() = runBlocking {
        val dao = FakeFlowerDao()
        val repository = FlowerRepository(dao)

        val id = repository.saveCapture(
            imagePath = "/data/photos/x.jpg",
            location = GeoPoint(48.8584, 2.2945, 5f),
            createdAt = 1_000L,
        )

        assertEquals(42L, id)
        val saved = dao.lastInserted!!
        assertEquals("/data/photos/x.jpg", saved.imagePath)
        assertEquals(48.8584, saved.latitude!!, 1e-9)
        assertEquals(2.2945, saved.longitude!!, 1e-9)
        assertEquals(5f, saved.accuracyMeters!!, 0f)
        assertEquals(1_000L, saved.createdAt)
    }

    @Test
    fun saveCapture_withoutLocation_storesNulls() = runBlocking {
        val dao = FakeFlowerDao()
        val repository = FlowerRepository(dao)

        repository.saveCapture(imagePath = "/p.jpg", location = null, createdAt = 7L)

        val saved = dao.lastInserted!!
        assertNull(saved.latitude)
        assertNull(saved.longitude)
        assertNull(saved.accuracyMeters)
        assertEquals("/p.jpg", saved.imagePath)
    }

    @Test
    fun delete_neverSyncedFlower_hardDeletes() = runBlocking {
        val dao = FakeFlowerDao()
        val repository = FlowerRepository(dao)
        val flower = FlowerEntity(
            id = 1L,
            imagePath = "", // pas de fichier à supprimer dans ce test
            createdAt = 1_000L,
            serverId = null,
        )

        repository.delete(flower)

        // Jamais synchronisée : suppression physique immédiate.
        assertEquals(flower, dao.lastDeleted)
        assertNull(dao.lastUpdated)
    }

    @Test
    fun delete_syncedFlower_softDeletesAndMarksPending() = runBlocking {
        val dao = FakeFlowerDao()
        val repository = FlowerRepository(dao)
        val flower = FlowerEntity(
            id = 1L,
            imagePath = "/p.jpg",
            createdAt = 1_000L,
            serverId = "srv-1",
            syncState = SyncState.SYNCED.name,
            updatedAt = 1_000L,
        )

        repository.delete(flower)

        // Connue du serveur : soft-delete à propager au prochain push (C3).
        assertNull(dao.lastDeleted)
        val updated = dao.lastUpdated!!
        assertEquals(SyncState.PENDING.name, updated.syncState)
        assertNotNull(updated.deletedAt)
        assertEquals(updated.deletedAt, updated.updatedAt)
    }
}
