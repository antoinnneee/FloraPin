package com.florapin.app.data

import com.florapin.app.badges.BadgeCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** BadgeDao en mémoire (JVM pur). */
private class MemBadgeDao : BadgeDao {
    private val rows = MutableStateFlow<List<BadgeEntity>>(emptyList())

    override fun observeAll(): Flow<List<BadgeEntity>> = rows
    override suspend fun all(): List<BadgeEntity> = rows.value
    override suspend fun unseen(): List<BadgeEntity> = rows.value.filter { !it.seen }
    override suspend fun count(): Int = rows.value.size

    override suspend fun upsertAll(badges: List<BadgeEntity>) {
        val keys = badges.map { it.badgeId to it.tier }.toSet()
        rows.value = rows.value.filterNot { (it.badgeId to it.tier) in keys } + badges
    }

    override suspend fun markAllSeen() {
        rows.value = rows.value.map { it.copy(seen = true) }
    }

    override suspend fun markSeen(badgeId: String, tier: Int) {
        rows.value = rows.value.map {
            if (it.badgeId == badgeId && it.tier == tier) it.copy(seen = true) else it
        }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}

/**
 * Base neutre de [FlowerDao] pour les tests : toutes les méthodes non pertinentes
 * lèvent une erreur. On délègue à cette base et on n'override que les agrégats.
 */
private class NoopFlowerDao : FlowerDao {
    override fun observeAll(): Flow<List<FlowerEntity>> = emptyFlow()
    override suspend fun getById(id: Long): FlowerEntity? = null
    override fun observeBySpecies(speciesId: String?, scientificName: String?):
        Flow<List<FlowerEntity>> = emptyFlow()
    override suspend fun findByServerId(serverId: String): FlowerEntity? = null
    override suspend fun allActive(): List<FlowerEntity> = emptyList()
    override suspend fun findLocalTwin(createdAt: Long): FlowerEntity? = null
    override fun observeById(id: Long): Flow<FlowerEntity?> = emptyFlow()
    override suspend fun insert(flower: FlowerEntity): Long = 0
    override suspend fun update(flower: FlowerEntity) = Unit
    override suspend fun delete(flower: FlowerEntity) = Unit
    override suspend fun deleteAll() = Unit
    override suspend fun pendingSync(): List<FlowerEntity> = emptyList()
    override suspend fun markSynced(
        id: Long,
        serverId: String,
        updatedAt: Long,
        expectedUpdatedAt: Long,
    ) = Unit
    override suspend fun markFailed(id: Long) = Unit
    override suspend fun pendingImageUploads(): List<FlowerEntity> = emptyList()
    override suspend fun setImagePendingUpload(id: Long, pending: Boolean) = Unit
    override suspend fun setImagePath(id: Long, path: String) = Unit
    override suspend fun softDeleteByServerId(serverId: String, deletedAt: Long) = Unit
    override suspend fun countActive(): Int = 0
    override suspend fun countDistinctSpecies(): Int = 0
    override suspend fun geoTimes(): List<FlowerGeoTime> = emptyList()
}

/** FlowerDao minimal : seuls les agrégats de badges sont implémentés. */
private class MemFlowerDaoForBadges(
    var flowerCount: Int = 0,
    var distinctSpecies: Int = 0,
    var geo: List<FlowerGeoTime> = emptyList(),
) : FlowerDao by NoopFlowerDao() {
    override suspend fun countActive(): Int = flowerCount
    override suspend fun countDistinctSpecies(): Int = distinctSpecies
    override suspend fun geoTimes(): List<FlowerGeoTime> = geo
}

/** Drapeau de baseline en mémoire. */
private class MemBaseline(private var done: Boolean = false) :
    BadgeRepository.BaselineFlag {
    override fun isDone(): Boolean = done
    override fun markDone() {
        done = true
    }
    override fun reset() {
        done = false
    }
}

class BadgeRepositoryTest {

    @Test
    fun premiere_execution_sur_base_existante_marque_tout_vu() = runBlocking {
        val badgeDao = MemBadgeDao()
        val repo = BadgeRepository(
            badgeDao = badgeDao,
            flowerDao = MemFlowerDaoForBadges(flowerCount = 60, distinctSpecies = 12),
            calculator = BadgeCalculator(regionResolver = null),
            baseline = MemBaseline(done = false),
        )

        val nouveaux = repo.recompute(now = 1_000L)

        // Baseline : aucune célébration renvoyée…
        assertTrue(nouveaux.isEmpty())
        // …et tous les paliers acquis sont marqués « vus ».
        val all = badgeDao.all()
        assertTrue(all.isNotEmpty())
        assertTrue(all.all { it.seen })
        // Herbier 50 et Diversité 10 font partie du lot (60 fleurs, 12 espèces).
        assertTrue(all.any { it.badgeId == BadgeCalculator.HERBIER && it.tier == 50 })
        assertTrue(all.any { it.badgeId == BadgeCalculator.DIVERSITE && it.tier == 10 })
    }

    @Test
    fun deblocage_apres_baseline_est_a_celebrer() = runBlocking {
        val badgeDao = MemBadgeDao()
        val flowerDao = MemFlowerDaoForBadges(flowerCount = 9, distinctSpecies = 0)
        val repo = BadgeRepository(
            badgeDao = badgeDao,
            flowerDao = flowerDao,
            calculator = BadgeCalculator(regionResolver = null),
            baseline = MemBaseline(done = false),
        )

        // Baseline avec 9 fleurs : « Première fleur » acquise et marquée vue.
        assertTrue(repo.recompute(now = 1L).isEmpty())
        assertTrue(badgeDao.all().all { it.seen })

        // On franchit le seuil Herbier 10 : nouveau palier à célébrer (seen=false).
        flowerDao.flowerCount = 10
        val nouveaux = repo.recompute(now = 2L)

        assertEquals(1, nouveaux.size)
        assertEquals(BadgeCalculator.HERBIER, nouveaux.first().badgeId)
        assertEquals(10, nouveaux.first().tier)
        assertFalse(nouveaux.first().seen)
    }

    @Test
    fun nouvel_utilisateur_celebre_sa_premiere_fleur() = runBlocking {
        val badgeDao = MemBadgeDao()
        val flowerDao = MemFlowerDaoForBadges(flowerCount = 0)
        val baseline = MemBaseline(done = false)
        val repo = BadgeRepository(
            badgeDao = badgeDao,
            flowerDao = flowerDao,
            calculator = BadgeCalculator(regionResolver = null),
            baseline = baseline,
        )

        // Baseline établie alors que l'utilisateur n'a aucune fleur : rien à voir,
        // mais le drapeau est posé.
        assertTrue(repo.recompute(now = 1L).isEmpty())
        assertTrue(baseline.isDone())
        assertTrue(badgeDao.all().isEmpty())

        // Sa toute première fleur doit être célébrée (pas noyée dans la baseline).
        flowerDao.flowerCount = 1
        val nouveaux = repo.recompute(now = 2L)
        assertEquals(1, nouveaux.size)
        assertEquals(BadgeCalculator.PREMIERE_FLEUR, nouveaux.first().badgeId)
        assertFalse(nouveaux.first().seen)
    }

    @Test
    fun recompute_est_idempotent() = runBlocking {
        val badgeDao = MemBadgeDao()
        val flowerDao = MemFlowerDaoForBadges(flowerCount = 12)
        val repo = BadgeRepository(
            badgeDao = badgeDao,
            flowerDao = flowerDao,
            calculator = BadgeCalculator(regionResolver = null),
            baseline = MemBaseline(done = true), // hors baseline
        )

        val premier = repo.recompute(now = 1L)
        val taille = badgeDao.all().size
        // Un second passage sans changement ne débloque rien de neuf.
        val second = repo.recompute(now = 2L)
        assertTrue(second.isEmpty())
        assertEquals(taille, badgeDao.all().size)
        assertTrue(premier.isNotEmpty())
    }
}
