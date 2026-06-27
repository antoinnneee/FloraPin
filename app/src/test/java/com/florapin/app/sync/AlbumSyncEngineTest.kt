package com.florapin.app.sync

import com.florapin.app.data.AlbumDao
import com.florapin.app.data.AlbumEntity
import com.florapin.app.data.AlbumRepository
import com.florapin.app.data.FlowerAlbumCrossRef
import com.florapin.app.data.FlowerDao
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.FlowerRepository
import com.florapin.app.data.SyncState
import com.florapin.app.network.api.AlbumsApi
import com.florapin.app.network.dto.AddFlowerToAlbumRequest
import com.florapin.app.network.dto.AlbumDto
import com.florapin.app.network.dto.CreateAlbumRequest
import com.florapin.app.network.dto.UpdateAlbumRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

/** Fleurs en mémoire : seul ce dont AlbumSyncEngine a besoin est implémenté. */
private class MemFlowerDao : FlowerDao {
    val store = linkedMapOf<Long, FlowerEntity>()
    private var seq = 0L
    fun seed(serverId: String?): FlowerEntity {
        val id = ++seq
        val f = FlowerEntity(id = id, imagePath = "", createdAt = 0, updatedAt = 0,
            serverId = serverId)
        store[id] = f
        return f
    }
    override fun observeAll(): Flow<List<FlowerEntity>> = flowOf(store.values.toList())
    override suspend fun getById(id: Long) = store[id]
    override suspend fun findByServerId(serverId: String) =
        store.values.find { it.serverId == serverId }
    override suspend fun findLocalTwin(createdAt: Long) =
        store.values.find {
            it.createdAt == createdAt && it.imagePath.isNotEmpty() && it.deletedAt == null
        }
    override fun observeById(id: Long): Flow<FlowerEntity?> = flowOf(store[id])
    override fun observeBySpecies(
        speciesId: String?,
        scientificName: String?,
    ): Flow<List<FlowerEntity>> = flowOf(emptyList())
    override suspend fun insert(flower: FlowerEntity): Long { store[flower.id] = flower; return flower.id }
    override suspend fun update(flower: FlowerEntity) { store[flower.id] = flower }
    override suspend fun delete(flower: FlowerEntity) { store.remove(flower.id) }
    override suspend fun deleteAll() { store.clear() }
    override suspend fun pendingSync() = emptyList<FlowerEntity>()
    override suspend fun markSynced(id: Long, serverId: String, updatedAt: Long) {}
    override suspend fun markFailed(id: Long) {}
    override suspend fun softDeleteByServerId(serverId: String, deletedAt: Long) {}
}

private class MemAlbumDao(private val flowers: MemFlowerDao) : AlbumDao {
    val albums = linkedMapOf<Long, AlbumEntity>()
    val refs = mutableSetOf<Pair<Long, Long>>()
    private var seq = 0L

    override fun observeAll(): Flow<List<AlbumEntity>> = flowOf(albums.values.toList())
    override suspend fun getById(id: Long) = albums[id]
    override fun observeById(id: Long): Flow<AlbumEntity?> = flowOf(albums[id])
    override fun observeFlowersInAlbum(albumId: Long): Flow<List<FlowerEntity>> =
        flowOf(
            refs.filter { it.first == albumId }
                .mapNotNull { flowers.store[it.second] },
        )
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
            albums[id] = it.copy(serverId = serverId, syncState = SyncState.SYNCED.name,
                updatedAt = updatedAt)
        }
    }
    override suspend fun addCrossRef(ref: FlowerAlbumCrossRef) {
        refs.add(ref.albumId to ref.flowerId)
    }
    override suspend fun clearMembers(albumId: Long) { refs.removeAll { it.first == albumId } }
    override suspend fun removeMember(albumId: Long, flowerId: Long) {
        refs.remove(albumId to flowerId)
    }
    override suspend fun memberFlowerIds(albumId: Long) =
        refs.filter { it.first == albumId }.map { it.second }
    override suspend fun memberFlowerServerIds(albumId: Long) =
        refs.filter { it.first == albumId }
            .mapNotNull { flowers.store[it.second]?.serverId }
    override suspend fun deleteAllCrossRefs() { refs.clear() }
}

/** Serveur albums en mémoire. */
private class FakeAlbumsApi : AlbumsApi {
    val server = linkedMapOf<String, AlbumDto>()
    private var seq = 0

    override suspend fun list() = server.values.toList()
    override suspend fun get(id: String) = server.getValue(id)
    override suspend fun create(body: CreateAlbumRequest): AlbumDto {
        val id = "srv-album-${++seq}"
        val dto = AlbumDto(id, "owner", body.name, emptyList(), "2026-06-21T09:00:00Z")
        server[id] = dto
        return dto
    }
    override suspend fun rename(id: String, body: UpdateAlbumRequest): AlbumDto {
        val dto = server.getValue(id).copy(name = body.name)
        server[id] = dto
        return dto
    }
    override suspend fun delete(id: String): Response<Unit> {
        server.remove(id); return Response.success(null)
    }
    override suspend fun addFlower(id: String, body: AddFlowerToAlbumRequest): AlbumDto {
        val dto = server.getValue(id)
        val updated = dto.copy(flowerIds = (dto.flowerIds + body.flowerId).distinct())
        server[id] = updated
        return updated
    }
    override suspend fun removeFlower(id: String, flowerId: String): AlbumDto {
        val dto = server.getValue(id)
        val updated = dto.copy(flowerIds = dto.flowerIds - flowerId)
        server[id] = updated
        return updated
    }
}

class AlbumSyncEngineTest {

    @Test
    fun push_createsLocalAlbumOnServerWithMembers() = runBlocking {
        val flowerDao = MemFlowerDao()
        val albumDao = MemAlbumDao(flowerDao)
        val api = FakeAlbumsApi()
        val albumRepo = AlbumRepository(albumDao) { 100L }
        val engine = AlbumSyncEngine(albumRepo, FlowerRepository(flowerDao), api) { 100L }

        val flower = flowerDao.seed(serverId = "srv-flower-1")
        val albumId = albumRepo.create("Printemps")
        albumRepo.addFlower(albumId, flower.id)

        engine.push()

        val local = albumDao.getById(albumId)!!
        assertEquals(SyncState.SYNCED.name, local.syncState)
        assertNotNull(local.serverId)
        // L'appartenance a été propagée au serveur.
        assertEquals(listOf("srv-flower-1"), api.server.getValue(local.serverId!!).flowerIds)
    }

    @Test
    fun pull_insertsRemoteAlbumWithMappedMembers() = runBlocking {
        val flowerDao = MemFlowerDao()
        val albumDao = MemAlbumDao(flowerDao)
        val api = FakeAlbumsApi()
        val engine = AlbumSyncEngine(
            AlbumRepository(albumDao) { 100L },
            FlowerRepository(flowerDao),
            api,
        ) { 100L }

        val flower = flowerDao.seed(serverId = "srv-flower-9")
        api.server["srv-a"] = AlbumDto("srv-a", "owner", "Été", listOf("srv-flower-9"),
            "2026-06-21T09:00:00Z")

        engine.pull()

        val local = albumDao.findByServerId("srv-a")
        assertNotNull(local)
        assertEquals("Été", local!!.name)
        assertEquals(SyncState.SYNCED.name, local.syncState)
        assertEquals(listOf(flower.id), albumDao.memberFlowerIds(local.id))
    }

    @Test
    fun pull_purgesSyncedAlbumAbsentFromServer() = runBlocking {
        val flowerDao = MemFlowerDao()
        val albumDao = MemAlbumDao(flowerDao)
        val api = FakeAlbumsApi()
        val engine = AlbumSyncEngine(
            AlbumRepository(albumDao) { 100L },
            FlowerRepository(flowerDao),
            api,
        ) { 100L }

        // Album déjà synchronisé localement mais absent du serveur.
        albumDao.insert(
            AlbumEntity(serverId = "gone", name = "Vieux", createdAt = 0, updatedAt = 0,
                syncState = SyncState.SYNCED.name),
        )

        engine.pull()

        assertNull(albumDao.findByServerId("gone"))
    }
}
