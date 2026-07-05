package com.florapin.app.data

import android.content.Context
import com.florapin.app.badges.BadgeCalculator
import com.florapin.app.geo.RegionResolver
import kotlinx.coroutines.flow.Flow

/**
 * Persistance des badges « collection » (TÂCHE 5.3) : orchestre le calcul local
 * ([BadgeCalculator]) et le stockage Room ([BadgeDao]), y compris la gestion des
 * célébrations (`seen`).
 *
 * **Initialisation « en masse » du `seen`.** À la toute première exécution du
 * calcul (base potentiellement déjà remplie lors de l'introduction de la
 * fonctionnalité), tous les paliers déjà acquis sont marqués `seen = true` pour
 * éviter une pluie de célébrations rétroactives — même principe que l'onboarding
 * 1.4. Un drapeau persistant (`baseline_done`) matérialise ce premier passage :
 * les paliers débloqués **après** sont, eux, `seen = false` (à célébrer). Ce
 * drapeau distingue proprement « utilisateur existant qui installe la
 * fonctionnalité » (tout vu) de « nouvel utilisateur qui débloque sa première
 * fleur » (célébré).
 *
 * Device-first : le résolveur de régions est chargé au mieux ; s'il échoue
 * (assets absents), les badges géographiques sont simplement omis.
 */
class BadgeRepository(
    private val badgeDao: BadgeDao,
    private val flowerDao: FlowerDao,
    private val calculator: BadgeCalculator,
    private val baseline: BaselineFlag,
) {

    /** Tous les paliers débloqués, observés en continu (grille Badges). */
    val badges: Flow<List<BadgeEntity>> = badgeDao.observeAll()

    /**
     * Recalcule les badges à partir des fleurs actives et persiste les paliers
     * nouvellement débloqués. Renvoie ces nouveaux paliers (vide si rien de neuf) ;
     * lors du premier passage (baseline), le retour est vide car tout est marqué
     * « vu » d'office.
     *
     * @param now horodatage de déblocage (injectable pour les tests).
     */
    suspend fun recompute(now: Long = System.currentTimeMillis()): List<BadgeEntity> {
        val input = BadgeCalculator.Input(
            flowerCount = flowerDao.countActive(),
            distinctSpeciesCount = flowerDao.countDistinctSpecies(),
            geoTimes = flowerDao.geoTimes(),
        )
        val unlocked = calculator.compute(input)

        val existing = badgeDao.all().mapTo(mutableSetOf()) { it.badgeId to it.tier }
        val isBaseline = !baseline.isDone()

        val fresh = unlocked
            .filter { (it.badgeId to it.tier) !in existing }
            .map {
                BadgeEntity(
                    badgeId = it.badgeId,
                    tier = it.tier,
                    unlockedAt = now,
                    // Premier passage : « vu » d'office (pas de célébration rétro).
                    seen = isBaseline,
                )
            }

        if (fresh.isNotEmpty()) badgeDao.upsertAll(fresh)
        // Toujours poser le drapeau au premier passage, même sans badge, pour que
        // le tout premier déblocage ultérieur soit bien célébré.
        if (isBaseline) baseline.markDone()

        // Les nouveaux paliers à célébrer sont ceux insérés hors baseline.
        return if (isBaseline) emptyList() else fresh
    }

    /** Marque tous les paliers comme vus (célébrations consommées à l'affichage). */
    suspend fun markAllSeen() = badgeDao.markAllSeen()

    /** Purge tous les badges et réarme la baseline (changement de compte — NODE-93). */
    suspend fun deleteAll() {
        badgeDao.deleteAll()
        baseline.reset()
    }

    /**
     * Drapeau « la baseline (premier calcul) a eu lieu ». Abstrait pour rendre la
     * réconciliation testable sans Android ; l'implémentation de prod est
     * [BaselinePrefs].
     */
    interface BaselineFlag {
        fun isDone(): Boolean
        fun markDone()
        fun reset()
    }

    /**
     * [BaselineFlag] persistant dans un fichier de prefs dédié — sans lien avec
     * `florapin_sync` (jamais purgé/`clear()`), conformément aux règles projet.
     */
    class BaselinePrefs(context: Context) : BaselineFlag {
        private val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        override fun isDone(): Boolean = prefs.getBoolean(KEY_BASELINE, false)

        override fun markDone() {
            prefs.edit().putBoolean(KEY_BASELINE, true).apply()
        }

        override fun reset() {
            prefs.edit().putBoolean(KEY_BASELINE, false).apply()
        }

        private companion object {
            const val PREFS = "florapin_badges"
            const val KEY_BASELINE = "baseline_done"
        }
    }

    companion object {
        /**
         * Construit le dépôt en chargeant au mieux le résolveur de régions
         * embarqué. En cas d'échec (assets indisponibles), les badges
         * géographiques sont omis (dégradation device-first).
         */
        fun from(context: Context): BadgeRepository {
            val db = FloraDatabase.getInstance(context)
            val resolver = runCatching { RegionResolver.fromAssets(context) }.getOrNull()
            return BadgeRepository(
                badgeDao = db.badgeDao(),
                flowerDao = db.flowerDao(),
                calculator = BadgeCalculator(resolver),
                baseline = BaselinePrefs(context),
            )
        }
    }
}
