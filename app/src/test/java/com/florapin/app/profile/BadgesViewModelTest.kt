package com.florapin.app.profile

import com.florapin.app.badges.BadgeCalculator
import com.florapin.app.badges.BadgeCatalog
import com.florapin.app.network.dto.EntraideBadgeCountsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests des constructeurs purs de la grille Badges (TÂCHE 5.5) : mapping
 * progression/compteurs → états de cartes, comptage des étoiles, progression
 * « 34 / 50 » et dégradation grisée (géo sans résolveur, entraide hors-ligne).
 */
class BadgesViewModelTest {

    private fun progress(
        flowerCount: Int = 0,
        distinctSpeciesCount: Int = 0,
        seasonCount: Int = 0,
        franceRegionCount: Int = 0,
        belgiumVisited: Int = 0,
        switzerlandVisited: Int = 0,
        englandVisited: Int = 0,
        irelandVisited: Int = 0,
        spainVisited: Int = 0,
        italyVisited: Int = 0,
        japanVisited: Int = 0,
        exploredRegionCount: Int = 0,
        overseasCount: Int = 0,
        cellCount: Int = 0,
    ) = BadgeCalculator.Progress(
        flowerCount = flowerCount,
        distinctSpeciesCount = distinctSpeciesCount,
        seasonCount = seasonCount,
        franceRegionCount = franceRegionCount,
        belgiumVisited = belgiumVisited,
        switzerlandVisited = switzerlandVisited,
        englandVisited = englandVisited,
        irelandVisited = irelandVisited,
        spainVisited = spainVisited,
        italyVisited = italyVisited,
        japanVisited = japanVisited,
        exploredRegionCount = exploredRegionCount,
        overseasCount = overseasCount,
        cellCount = cellCount,
    )

    private fun collection(id: String, p: BadgeCalculator.Progress) =
        buildCollectionBadges(p).first { it.id == id }

    private fun country(id: String, p: BadgeCalculator.Progress) =
        buildCountryBadges(p).first { it.id == id }

    @Test
    fun herbier_compte_les_etoiles_et_affiche_la_progression() {
        val herbier = collection(BadgeCalculator.HERBIER, progress(flowerCount = 34))
        // Seuils 10/50/100/250 : 34 franchit le premier seulement.
        assertEquals(1, herbier.unlockedTiers)
        assertEquals(4, herbier.tiers.size)
        assertTrue(herbier.unlocked)
        assertFalse(herbier.maxed)
        // Numérateur brut + prochain seuil → « 34 / 50 ».
        assertEquals(34, herbier.currentValue)
        assertEquals(50, herbier.nextTier)
    }

    @Test
    fun famille_sans_palier_est_grisee_mais_disponible() {
        val herbier = collection(BadgeCalculator.HERBIER, progress(flowerCount = 3))
        assertFalse(herbier.unlocked)
        assertEquals(0, herbier.unlockedTiers)
        assertTrue(herbier.available)
    }

    @Test
    fun palier_max_atteint_est_complet() {
        val diversite = collection(BadgeCalculator.DIVERSITE, progress(distinctSpeciesCount = 60))
        assertTrue(diversite.maxed)
        assertEquals(diversite.tiers.size, diversite.unlockedTiers)
        assertEquals(null, diversite.nextTier)
    }

    @Test
    fun premiere_fleur_est_un_palier_unique() {
        val locked = collection(BadgeCalculator.PREMIERE_FLEUR, progress(flowerCount = 0))
        assertTrue(locked.singleTier)
        assertFalse(locked.unlocked)

        val unlocked = collection(BadgeCalculator.PREMIERE_FLEUR, progress(flowerCount = 1))
        assertTrue(unlocked.unlocked)
        assertTrue(unlocked.maxed)
    }

    @Test
    fun badges_geo_sont_grises_quand_le_resolveur_est_absent() {
        val p = progress(
            franceRegionCount = BadgeCalculator.UNAVAILABLE,
            belgiumVisited = BadgeCalculator.UNAVAILABLE,
            switzerlandVisited = BadgeCalculator.UNAVAILABLE,
            englandVisited = BadgeCalculator.UNAVAILABLE,
            irelandVisited = BadgeCalculator.UNAVAILABLE,
            spainVisited = BadgeCalculator.UNAVAILABLE,
            italyVisited = BadgeCalculator.UNAVAILABLE,
            japanVisited = BadgeCalculator.UNAVAILABLE,
            exploredRegionCount = BadgeCalculator.UNAVAILABLE,
            overseasCount = BadgeCalculator.UNAVAILABLE,
        )
        val france = country(BadgeCalculator.FRANCE, p)
        val belgique = country(BadgeCalculator.BELGIQUE, p)
        val suisse = country(BadgeCalculator.SUISSE, p)
        val angleterre = country(BadgeCalculator.ANGLETERRE, p)
        val irlande = country(BadgeCalculator.IRLANDE, p)
        val espagne = country(BadgeCalculator.ESPAGNE, p)
        val italie = country(BadgeCalculator.ITALIE, p)
        val japon = country(BadgeCalculator.JAPON, p)
        val explorateur = country(BadgeCalculator.EXPLORATEUR, p)
        val outremer = collection(BadgeCatalog.OUTRE_MER, p)
        assertFalse(france.available)
        assertFalse(explorateur.available)
        assertFalse(belgique.available)
        assertFalse(suisse.available)
        assertFalse(angleterre.available)
        assertFalse(irlande.available)
        assertFalse(espagne.available)
        assertFalse(italie.available)
        assertFalse(japon.available)
        assertFalse(outremer.available)
        // Indisponible → aucune étoile allumée, valeur ramenée à 0 (pas de -1 affiché).
        assertEquals(0, explorateur.unlockedTiers)
        assertEquals(0, explorateur.currentValue)
    }

    @Test
    fun belgique_est_un_badge_unique_debloque_apres_une_visite() {
        val locked = country(BadgeCalculator.BELGIQUE, progress(belgiumVisited = 0))
        val unlocked = country(BadgeCalculator.BELGIQUE, progress(belgiumVisited = 1))

        assertTrue(locked.singleTier)
        assertFalse(locked.unlocked)
        assertTrue(unlocked.unlocked)
        assertTrue(unlocked.maxed)
    }

    @Test
    fun suisse_est_un_badge_unique_debloque_apres_une_visite() {
        val locked = country(BadgeCalculator.SUISSE, progress(switzerlandVisited = 0))
        val unlocked = country(BadgeCalculator.SUISSE, progress(switzerlandVisited = 1))

        assertTrue(locked.singleTier)
        assertFalse(locked.unlocked)
        assertTrue(unlocked.unlocked)
        assertTrue(unlocked.maxed)
    }

    @Test
    fun nouveaux_pays_sont_des_badges_uniques() {
        val cases = listOf(
            BadgeCalculator.ANGLETERRE to progress(englandVisited = 1),
            BadgeCalculator.IRLANDE to progress(irelandVisited = 1),
            BadgeCalculator.ESPAGNE to progress(spainVisited = 1),
            BadgeCalculator.ITALIE to progress(italyVisited = 1),
            BadgeCalculator.JAPON to progress(japanVisited = 1),
        )
        cases.forEach { (id, p) ->
            val badge = country(id, p)
            assertTrue(badge.singleTier)
            assertTrue(badge.unlocked)
            assertTrue(badge.maxed)
        }
    }

    @Test
    fun pays_est_une_section_distincte_avec_france_et_explorateur_global() {
        val p = progress(
            franceRegionCount = 6,
            belgiumVisited = 1,
            switzerlandVisited = 1,
            exploredRegionCount = 9,
        )
        val collectionIds = buildCollectionBadges(p).map { it.id }
        val countries = buildCountryBadges(p)
        val countryIds = BadgeCatalog.COUNTRIES.map { it.id }

        assertFalse(collectionIds.any { it in countryIds })
        assertEquals(
            listOf(
                BadgeCalculator.EXPLORATEUR,
                BadgeCalculator.FRANCE,
                BadgeCalculator.BELGIQUE,
                BadgeCalculator.SUISSE,
                BadgeCalculator.ANGLETERRE,
                BadgeCalculator.IRLANDE,
                BadgeCalculator.ESPAGNE,
                BadgeCalculator.ITALIE,
                BadgeCalculator.JAPON,
            ),
            countries.map { it.id },
        )
        val explorateur = countries.first { it.id == BadgeCalculator.EXPLORATEUR }
        val france = countries.first { it.id == BadgeCalculator.FRANCE }
        assertEquals(listOf(1, 5, 10, 15, 20), explorateur.tiers)
        assertEquals(9, explorateur.currentValue)
        assertEquals("France", france.title)
        assertEquals(6, france.currentValue)
    }

    @Test
    fun palier_neuf_est_marque_comme_nouveau() {
        val badges = buildCollectionBadges(
            progress(flowerCount = 12),
            freshIds = setOf(BadgeCalculator.HERBIER),
        )
        val herbier = badges.first { it.id == BadgeCalculator.HERBIER }
        assertTrue(herbier.isNew)
        assertFalse(badges.first { it.id == BadgeCalculator.DIVERSITE }.isNew)
    }

    @Test
    fun entraide_mappe_les_compteurs_serveur() {
        val counts = EntraideBadgeCountsDto(friends = 4, proposalsAccepted = 7)
        val badges = buildEntraideBadges(counts)
        val amis = badges.first { it.id == BadgeCatalog.FRIENDS }
        // Amis 1/3/5/10 : 4 franchit 1 et 3.
        assertTrue(amis.available)
        assertEquals(4, amis.currentValue)
        assertEquals(2, amis.unlockedTiers)
        assertEquals(5, amis.nextTier)
    }

    @Test
    fun entraide_est_grisee_hors_ligne() {
        val badges = buildEntraideBadges(null)
        assertTrue(badges.isNotEmpty())
        assertTrue(badges.all { !it.available })
        assertTrue(badges.all { it.unlockedTiers == 0 })
    }
}
