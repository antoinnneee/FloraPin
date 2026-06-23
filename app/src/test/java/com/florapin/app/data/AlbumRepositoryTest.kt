package com.florapin.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** AlbumDao en mémoire pour tester la logique de AlbumRepository (JVM pur). */
private class MemAlbumDao : AlbumDao {
    val albums = linkedMapOf<Long, AlbumEntity>()
    val refs = mutableSetOf<Pair<Long, Long>>()
    private var seq = 0L

    override fun observeAll(): Flow<List<AlbumEntity>> = flowOf(albums.values.toList())
    override suspend fun getById(id: Long) = albums[id]
    override fun observeById(id: Long): Flow<AlbumEntity?> = flowOf(albums[id])
    override fun observeFlowersInAlbum(albumId: Long): Flow<List<FlowerEntity>> =
        flowOf(emptyList())
    override suspend fun findByServerId(serverId: String) =
        albums.values.find { it.serverId == serverId }
    override suspend fun allActive() = albums.values.filter { it.deletedAt == null }
    override suspend fun insert(album: AlbumEntity): Long {
        val id = ++seq; albums[id] = album.copy(id = id); return id
    }
    override suspend fun update(album: AlbumEntity) { albums[album.id] = album }
    override suspend fun deleteById(id: Long) { albums.remove(id) }
    override suspend fun deleteAllAlbums() { albums.clear() }
    override suspend fun pendingSync() =
        albums.values.filter { it.syncState != SyncState.SYNCED.name }
    override suspend fun markSynced(id: Long, serverId: String, updatedAt: Long) {
        albums[id]?.let {
            albums[id] = it.copy(serverId = serverId, syncState = SyncState.SYNCED.name)
        }
    }
    override suspend fun addCrossRef(ref: FlowerAlbumCrossRef) { refs.add(ref.albumId to ref.flowerId) }
    override suspend fun clearMembers(albumId: Long) { refs.removeAll { it.first == albumId } }
    override suspend fun removeMember(albumId: Long, flowerId: Long) { refs.remove(albumId to flowerId) }
    override suspend fun memberFlowerIds(albumId: Long) =
        refs.filter { it.first == albumId }.map { it.second }
    override suspend fun memberFlowerServerIds(albumId: Long) = emptyList<String>()
    override suspend fun deleteAllCrossRefs() { refs.clear() }
}

class AlbumRepositoryTest {

    private fun repo(dao: MemAlbumDao) = AlbumRepository(dao) { 7L }

    @Test
    fun create_marksPending() = runBlocking {
        val dao = MemAlbumDao()
        val id = repo(dao).create("Printemps")
        val album = dao.getById(id)!!
        assertEquals("Printemps", album.name)
        assertEquals(SyncState.PENDING.name, album.syncState)
        assertEquals(7L, album.createdAt)
    }

    @Test
    fun delete_unsynced_removesImmediately() = runBlocking {
        val dao = MemAlbumDao()
        val r = repo(dao)
        val id = r.create("Tmp")
        r.delete(dao.getById(id)!!)
        assertNull(dao.getById(id))
    }

    @Test
    fun delete_synced_softDeletes() = runBlocking {
        val dao = MemAlbumDao()
        val r = repo(dao)
        val id = r.create("Synced")
        r.markSynced(id, "srv-1")
        r.delete(dao.getById(id)!!)
        val album = dao.getById(id)!!
        assertNotNull(album.deletedAt)
        assertEquals(SyncState.PENDING.name, album.syncState)
    }

    @Test
    fun addFlower_setsMembership_andTouchesAlbum() = runBlocking {
        val dao = MemAlbumDao()
        val r = repo(dao)
        val id = r.create("Roses")
        r.markSynced(id, "srv-2")

        r.addFlower(id, flowerLocalId = 42L)

        assertEquals(listOf(42L), dao.memberFlowerIds(id))
        // L'ajout repasse l'album en attente de sync.
        assertEquals(SyncState.PENDING.name, dao.getById(id)!!.syncState)
    }

    @Test
    fun removeFlower_clearsMembership() = runBlocking {
        val dao = MemAlbumDao()
        val r = repo(dao)
        val id = r.create("Roses")
        r.addFlower(id, 42L)
        r.removeFlower(id, 42L)
        assertTrue(dao.memberFlowerIds(id).isEmpty())
    }
}
