package com.florapin.app.data.backup

import com.florapin.app.data.AlbumDao
import com.florapin.app.data.AlbumEntity
import com.florapin.app.data.AlbumRepository
import com.florapin.app.data.FlowerAlbumCrossRef
import com.florapin.app.data.FlowerDao
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.FlowerGeoTime
import com.florapin.app.data.FlowerRepository
import com.florapin.app.data.PhotoDao
import com.florapin.app.data.PhotoEntity
import com.florapin.app.data.PhotoRepository
import com.florapin.app.data.SyncState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// --- Fakes DAO en mémoire (suffisants pour l'export/import) ---

private class FakeFlowerDao : FlowerDao {
    val store = linkedMapOf<Long, FlowerEntity>()
    private var seq = 0L
    override fun observeAll(): Flow<List<FlowerEntity>> = flowOf(store.values.toList())
    override suspend fun getById(id: Long) = store[id]
    override fun observeBySpecies(speciesId: String?, scientificName: String?) =
        flowOf(emptyList<FlowerEntity>())
    override fun observeById(id: Long): Flow<FlowerEntity?> = flowOf(store[id])
    override suspend fun findByServerId(serverId: String) =
        store.values.find { it.serverId == serverId }
    override suspend fun allActive() =
        store.values.filter { it.deletedAt == null }
    override suspend fun findLocalTwin(createdAt: Long) =
        store.values.find { it.createdAt == createdAt && it.imagePath.isNotEmpty() }
    override suspend fun insert(flower: FlowerEntity): Long {
        val id = ++seq; store[id] = flower.copy(id = id); return id
    }
    override suspend fun update(flower: FlowerEntity) { store[flower.id] = flower }
    override suspend fun delete(flower: FlowerEntity) { store.remove(flower.id) }
    override suspend fun deleteAll() { store.clear() }
    override suspend fun pendingSync() = store.values.filter { it.syncState != "SYNCED" }
    override suspend fun markSynced(id: Long, serverId: String, updatedAt: Long, expectedUpdatedAt: Long) = Unit
    override suspend fun markFailed(id: Long) = Unit
    override suspend fun pendingImageUploads() = emptyList<FlowerEntity>()
    override suspend fun setImagePendingUpload(id: Long, pending: Boolean) = Unit
    override suspend fun setImagePath(id: Long, path: String) = Unit
    override suspend fun softDeleteByServerId(serverId: String, deletedAt: Long) = Unit
    override suspend fun countActive(): Int = 0
    override suspend fun countDistinctSpecies(): Int = 0
    override suspend fun geoTimes(): List<FlowerGeoTime> = emptyList()
}

private class FakeAlbumDao : AlbumDao {
    val albums = linkedMapOf<Long, AlbumEntity>()
    val refs = mutableListOf<FlowerAlbumCrossRef>()
    private var seq = 0L
    override fun observeAll() = flowOf(albums.values.toList())
    override suspend fun getById(id: Long) = albums[id]
    override fun observeById(id: Long) = flowOf(albums[id])
    override fun observeFlowersInAlbum(albumId: Long) = flowOf(emptyList<FlowerEntity>())
    override suspend fun findByServerId(serverId: String) =
        albums.values.find { it.serverId == serverId }
    override suspend fun findByClientId(clientId: String) =
        albums.values.find { it.clientId == clientId }
    override suspend fun allActive() = albums.values.filter { it.deletedAt == null }
    override suspend fun insert(album: AlbumEntity): Long {
        val id = ++seq; albums[id] = album.copy(id = id); return id
    }
    override suspend fun update(album: AlbumEntity) { albums[album.id] = album }
    override suspend fun deleteById(id: Long) { albums.remove(id) }
    override suspend fun deleteAllAlbums() { albums.clear() }
    override suspend fun pendingSync() = albums.values.filter { it.syncState != "SYNCED" }
    override suspend fun markSynced(id: Long, serverId: String, updatedAt: Long) = Unit
    override suspend fun addCrossRef(ref: FlowerAlbumCrossRef) {
        if (refs.none { it.albumId == ref.albumId && it.flowerId == ref.flowerId }) refs.add(ref)
    }
    override suspend fun clearMembers(albumId: Long) { refs.removeAll { it.albumId == albumId } }
    override suspend fun removeMember(albumId: Long, flowerId: Long) {
        refs.removeAll { it.albumId == albumId && it.flowerId == flowerId }
    }
    override suspend fun memberFlowerIds(albumId: Long) =
        refs.filter { it.albumId == albumId }.map { it.flowerId }
    override suspend fun allCrossRefs() = refs.toList()
    override suspend fun memberFlowerServerIds(albumId: Long) = emptyList<String>()
    override suspend fun deleteAllCrossRefs() { refs.clear() }
}

private class FakePhotoDao : PhotoDao {
    val store = linkedMapOf<Long, PhotoEntity>()
    private var seq = 0L
    override fun observeForFlower(flowerLocalId: Long) =
        flowOf(store.values.filter { it.flowerLocalId == flowerLocalId })
    override suspend fun getById(id: Long) = store[id]
    override suspend fun findByServerId(serverId: String) =
        store.values.find { it.serverId == serverId }
    override suspend fun forFlower(flowerLocalId: Long) =
        store.values.filter { it.flowerLocalId == flowerLocalId && it.deletedAt == null }
    override suspend fun allForFlower(flowerLocalId: Long) =
        store.values.filter { it.flowerLocalId == flowerLocalId }
    override suspend fun allActive() = store.values.filter { it.deletedAt == null }
    override suspend fun insert(photo: PhotoEntity): Long {
        val id = ++seq; store[id] = photo.copy(id = id); return id
    }
    override suspend fun update(photo: PhotoEntity) { store[photo.id] = photo }
    override suspend fun deleteById(id: Long) { store.remove(id) }
    override suspend fun deleteAll() { store.clear() }
    override suspend fun pendingSync() = store.values.filter { it.syncState != "SYNCED" }
    override suspend fun markSynced(id: Long, serverId: String) = Unit
    override suspend fun setImagePath(id: Long, path: String) = Unit
    override suspend fun pendingImageUploads() = emptyList<PhotoEntity>()
    override suspend fun setImagePendingUpload(id: Long, pending: Boolean) = Unit
}

class BackupRoundTripTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun seedSource(imageDir: File): Triple<FakeFlowerDao, FakeAlbumDao, FakePhotoDao> {
        val flowerDao = FakeFlowerDao()
        val albumDao = FakeAlbumDao()
        val photoDao = FakePhotoDao()

        val coverFile = File(imageDir, "FLORA_cover.jpg").apply { writeText("cover-bytes") }
        val extraFile = File(imageDir, "FLORA_extra.jpg").apply { writeText("extra-bytes") }

        runBlocking {
            val fId = flowerDao.insert(
                FlowerEntity(
                    imagePath = coverFile.absolutePath,
                    latitude = 48.85,
                    longitude = 2.29,
                    createdAt = 1_000L,
                    notes = "jolie",
                    tags = listOf("jardin", "rose"),
                    serverId = "srv-flower-1",
                    ownerId = "owner-1",
                    syncState = SyncState.SYNCED.name,
                    updatedAt = 1_000L,
                ),
            )
            val aId = albumDao.insert(
                AlbumEntity(
                    clientId = "client-album-1",
                    name = "Été",
                    createdAt = 500L,
                    updatedAt = 500L,
                    serverId = "srv-album-1",
                    syncState = SyncState.SYNCED.name,
                ),
            )
            albumDao.addCrossRef(FlowerAlbumCrossRef(albumId = aId, flowerId = fId))
            photoDao.insert(
                PhotoEntity(
                    flowerLocalId = fId,
                    imagePath = extraFile.absolutePath,
                    serverId = "srv-photo-1",
                    position = 1,
                    syncState = SyncState.SYNCED.name,
                ),
            )
        }
        return Triple(flowerDao, albumDao, photoDao)
    }

    @Test
    fun exportThenImport_restoresData_preservesSyncFields_andRemapsRelations() = runBlocking {
        val srcImages = tmp.newFolder("src")
        val (sf, sa, sp) = seedSource(srcImages)
        val exporter = BackupExporter(
            FlowerRepository(sf), AlbumRepository(sa), PhotoRepository(sp),
            now = { 42L },
        )

        val bytes = ByteArrayOutputStream()
        val exportResult = exporter.export(bytes)
        assertEquals(1, exportResult.flowers)
        assertEquals(1, exportResult.albums)
        assertEquals(1, exportResult.photos)
        assertEquals(2, exportResult.imageFiles)

        // Base fraîche (autre appareil / réinstallation).
        val df = FakeFlowerDao()
        val da = FakeAlbumDao()
        val dp = FakePhotoDao()
        val photosDir = tmp.newFolder("photos")
        val importer = BackupImporter(
            FlowerRepository(df), AlbumRepository(da), PhotoRepository(dp),
            photosDir = photosDir, tempDir = tmp.newFolder("tmp"),
        )

        val r1 = importer.import(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(1, r1.flowersAdded)
        assertEquals(1, r1.albumsAdded)
        assertEquals(1, r1.photosAdded)

        // Fleur restaurée avec ses champs de sync préservés.
        val flower = df.store.values.single()
        assertEquals("srv-flower-1", flower.serverId)
        assertEquals(SyncState.SYNCED.name, flower.syncState)
        assertEquals(listOf("jardin", "rose"), flower.tags)
        assertTrue("image copiée", File(flower.imagePath).exists())
        assertEquals("cover-bytes", File(flower.imagePath).readText())

        // Photo rattachée à la NOUVELLE id locale de la fleur.
        val photo = dp.store.values.single()
        assertEquals(flower.id, photo.flowerLocalId)
        assertEquals("extra-bytes", File(photo.imagePath).readText())

        // Appartenance remappée sur les nouvelles ids locales.
        val album = da.albums.values.single()
        assertEquals(1, da.refs.size)
        assertEquals(album.id, da.refs.single().albumId)
        assertEquals(flower.id, da.refs.single().flowerId)

        // Ré-import idempotent : aucun doublon.
        val r2 = importer.import(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(0, r2.flowersAdded)
        assertEquals(1, r2.flowersSkipped)
        assertEquals(0, r2.albumsAdded)
        assertEquals(1, r2.albumsSkipped)
        assertEquals(0, r2.photosAdded)
        assertEquals(1, r2.photosSkipped)
        assertEquals(1, df.store.size)
        assertEquals(1, da.albums.size)
        assertEquals(1, dp.store.size)
        assertEquals(1, da.refs.size)
        assertNotNull(flower.serverId)
    }
}
