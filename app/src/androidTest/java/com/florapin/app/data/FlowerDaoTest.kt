package com.florapin.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** CRUD réel + requêtes de sync sur une base Room en mémoire (NODE-60). */
@RunWith(AndroidJUnit4::class)
class FlowerDaoTest {

    private lateinit var db: FloraDatabase
    private lateinit var dao: FlowerDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FloraDatabase::class.java,
        ).build()
        dao = db.flowerDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insert_thenObserveAll_returnsRow() = runBlocking {
        val id = dao.insert(
            FlowerEntity(
                imagePath = "/p.jpg",
                createdAt = 1_000L,
                species = "Rosa",
                tags = listOf("rouge", "jardin"),
            ),
        )

        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(id, all.first().id)
        // Le TypeConverter restitue bien la liste d'étiquettes.
        assertEquals(listOf("rouge", "jardin"), all.first().tags)
    }

    @Test
    fun softDeleteByServerId_hidesFromObserveAll() = runBlocking {
        dao.insert(
            FlowerEntity(
                imagePath = "/p.jpg",
                createdAt = 1_000L,
                serverId = "srv-1",
                syncState = SyncState.SYNCED.name,
            ),
        )

        dao.softDeleteByServerId("srv-1", 5_000L)

        assertEquals(0, dao.observeAll().first().size)
        assertNotNull(dao.findByServerId("srv-1"))
    }

    @Test
    fun pendingSync_thenMarkSynced_movesOutOfQueue() = runBlocking {
        val id = dao.insert(
            FlowerEntity(
                imagePath = "/p.jpg",
                createdAt = 1_000L,
                syncState = SyncState.PENDING.name,
            ),
        )
        assertEquals(1, dao.pendingSync().size)

        dao.markSynced(id, "srv-9", 6_000L)

        assertEquals(0, dao.pendingSync().size)
        val saved = dao.getById(id)
        assertEquals("srv-9", saved?.serverId)
        assertEquals(SyncState.SYNCED.name, saved?.syncState)
    }

    @Test
    fun observeBySpecies_matchesBySpeciesIdOrScientificName() = runBlocking {
        // Rattachée par species_id.
        dao.insert(
            FlowerEntity(
                imagePath = "/a.jpg",
                createdAt = 1_000L,
                speciesId = "sp-1",
            ),
        )
        // Rattachée par texte libre = nom scientifique.
        dao.insert(
            FlowerEntity(
                imagePath = "/b.jpg",
                createdAt = 2_000L,
                species = "Rosa canina",
            ),
        )
        // Une autre espèce : exclue.
        dao.insert(
            FlowerEntity(
                imagePath = "/c.jpg",
                createdAt = 3_000L,
                species = "Bellis perennis",
            ),
        )

        val mine = dao.observeBySpecies("sp-1", "Rosa canina").first()
        assertEquals(2, mine.size)
        // Triées par date décroissante : la fleur texte (2_000) avant l'id (1_000).
        assertEquals(listOf("/b.jpg", "/a.jpg"), mine.map { it.imagePath })
    }

    @Test
    fun observeById_emitsNullWhenDeleted() = runBlocking {
        val id = dao.insert(FlowerEntity(imagePath = "/p.jpg", createdAt = 1_000L))
        assertNotNull(dao.observeById(id).first())

        dao.delete(dao.getById(id)!!)
        assertNull(dao.observeById(id).first())
    }
}
