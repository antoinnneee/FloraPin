package com.florapin.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SavedFlowerDao en mémoire (JVM pur), adossé à un flux mutable pour l'observation. */
class MemSavedFlowerDao : SavedFlowerDao {
    private val rows = MutableStateFlow<List<SavedFlowerEntity>>(emptyList())
    private var seq = 0L

    override fun observeAll(): Flow<List<SavedFlowerEntity>> =
        rows.map { list -> list.sortedByDescending { it.savedAt } }

    override fun observeSavedIds(): Flow<List<String>> =
        rows.map { list -> list.map { it.serverId } }

    override suspend fun getByServerId(serverId: String): SavedFlowerEntity? =
        rows.value.find { it.serverId == serverId }

    override suspend fun all(): List<SavedFlowerEntity> = rows.value

    override suspend fun insert(saved: SavedFlowerEntity): Long {
        // REPLACE : un même serverId écrase l'existant (idempotent).
        val id = ++seq
        rows.value = rows.value.filterNot { it.serverId == saved.serverId } + saved.copy(id = id)
        return id
    }

    override suspend fun deleteByServerId(serverId: String) {
        rows.value = rows.value.filterNot { it.serverId == serverId }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}

class SavedFlowerRepositoryTest {

    private fun entity(serverId: String, savedAt: Long = 1L) = SavedFlowerEntity(
        serverId = serverId,
        ownerName = "Alice",
        species = "Rose",
        savedAt = savedAt,
    )

    @Test
    fun save_thenRemove_roundTrips() = runBlocking {
        val dao = MemSavedFlowerDao()
        val repo = SavedFlowerRepository(dao)

        repo.save(entity("fl1"))
        assertEquals("fl1", repo.getByServerId("fl1")?.serverId)

        repo.remove("fl1")
        assertNull(repo.getByServerId("fl1"))
    }

    @Test
    fun save_sameServerId_replacesSnapshot() = runBlocking {
        val dao = MemSavedFlowerDao()
        val repo = SavedFlowerRepository(dao)

        repo.save(entity("fl1").copy(species = "Ancien"))
        repo.save(entity("fl1").copy(species = "Nouveau"))

        // Ré-enregistrer ne crée pas de doublon : un seul snapshot, à jour.
        assertEquals(1, dao.all().size)
        assertEquals("Nouveau", repo.getByServerId("fl1")?.species)
    }

    @Test
    fun deleteAll_clearsSelection() = runBlocking {
        val dao = MemSavedFlowerDao()
        val repo = SavedFlowerRepository(dao)
        repo.save(entity("fl1"))
        repo.save(entity("fl2"))

        repo.deleteAll()
        assertTrue(dao.all().isEmpty())
    }
}
