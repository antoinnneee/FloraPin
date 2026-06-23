package com.florapin.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private class FakePhotoDao : PhotoDao {
    val store = linkedMapOf<Long, PhotoEntity>()
    private var seq = 0L
    override fun observeForFlower(flowerLocalId: Long): Flow<List<PhotoEntity>> =
        flowOf(store.values.filter { it.flowerLocalId == flowerLocalId })
    override suspend fun getById(id: Long) = store[id]
    override suspend fun findByServerId(serverId: String) =
        store.values.find { it.serverId == serverId }
    override suspend fun forFlower(flowerLocalId: Long) =
        store.values.filter { it.flowerLocalId == flowerLocalId && it.deletedAt == null }
    override suspend fun insert(photo: PhotoEntity): Long {
        val id = ++seq; store[id] = photo.copy(id = id); return id
    }
    override suspend fun update(photo: PhotoEntity) { store[photo.id] = photo }
    override suspend fun deleteById(id: Long) { store.remove(id) }
    override suspend fun deleteAll() { store.clear() }
    override suspend fun pendingSync() =
        store.values.filter { it.syncState != SyncState.SYNCED.name }
    override suspend fun markSynced(id: Long, serverId: String) {
        store[id]?.let { store[id] = it.copy(serverId = serverId, syncState = SyncState.SYNCED.name) }
    }
}

class PhotoRepositoryTest {

    @Test
    fun addLocalPhoto_isPending_withIncrementingPosition() = runBlocking {
        val dao = FakePhotoDao()
        val repo = PhotoRepository(dao) { 7L }

        val id1 = repo.addLocalPhoto(1L, "/a.jpg")
        repo.addLocalPhoto(1L, "/b.jpg")

        val first = dao.getById(id1)!!
        assertEquals(SyncState.PENDING.name, first.syncState)
        assertEquals(0, first.position)
        assertEquals(listOf(0, 1), dao.forFlower(1L).map { it.position })
    }

    @Test
    fun delete_unsynced_removesImmediately() = runBlocking {
        val dao = FakePhotoDao()
        val repo = PhotoRepository(dao) { 7L }
        val id = repo.addLocalPhoto(1L, "/a.jpg")

        repo.delete(dao.getById(id)!!)

        assertNull(dao.getById(id))
    }

    @Test
    fun delete_synced_softDeletes() = runBlocking {
        val dao = FakePhotoDao()
        val repo = PhotoRepository(dao) { 7L }
        val id = repo.addLocalPhoto(1L, "/a.jpg")
        repo.markSynced(id, "srv-1")

        repo.delete(dao.getById(id)!!)

        val photo = dao.getById(id)!!
        assertNotNull(photo.deletedAt)
        assertEquals(SyncState.PENDING.name, photo.syncState)
    }
}
