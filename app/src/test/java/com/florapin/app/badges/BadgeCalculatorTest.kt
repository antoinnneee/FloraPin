package com.florapin.app.badges

import com.florapin.app.data.FlowerGeoTime
import com.florapin.app.geo.RegionResolver
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du calcul local des badges « collection » (TÂCHE 5.3).
 *
 * On isole chaque famille de badge et on vérifie les paliers cumulatifs, les
 * saisons (hémisphère nord, fuseau contrôlé), la grille ~5 km et les badges
 * géographiques (régions/outre-mer) via un résolveur synthétique.
 */
class BadgeCalculatorTest {

    /** Calculateur sans résolveur (badges géographiques omis), fuseau UTC déterministe. */
    private val plain = BadgeCalculator(regionResolver = null, zone = ZoneId.of("UTC"))

    private fun input(
        flowerCount: Int = 0,
        distinctSpecies: Int = 0,
        geoTimes: List<FlowerGeoTime> = emptyList(),
    ) = BadgeCalculator.Input(flowerCount, distinctSpecies, geoTimes)

    /** Epoch millis du 15 du mois donné (2024), à minuit UTC. */
    private fun monthMillis(month: Int): Long =
        LocalDate.of(2024, month, 15).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun ids(badges: List<UnlockedBadge>) = badges.map { it.badgeId to it.tier }.toSet()

    @Test
    fun premiere_fleur_des_la_premiere_capture() {
        assertTrue(plain.compute(input(flowerCount = 0)).isEmpty())
        assertTrue(
            ids(plain.compute(input(flowerCount = 1)))
                .contains(BadgeCalculator.PREMIERE_FLEUR to 1),
        )
    }

    @Test
    fun herbier_debloque_les_paliers_cumulatifs() {
        val badges = ids(plain.compute(input(flowerCount = 120)))
        assertTrue(badges.contains(BadgeCalculator.HERBIER to 10))
        assertTrue(badges.contains(BadgeCalculator.HERBIER to 50))
        assertTrue(badges.contains(BadgeCalculator.HERBIER to 100))
        // 120 < 250 : palier max non atteint.
        assertFalse(badges.contains(BadgeCalculator.HERBIER to 250))
    }

    @Test
    fun diversite_compte_les_paliers_especes() {
        val badges = ids(plain.compute(input(distinctSpecies = 26)))
        assertTrue(badges.contains(BadgeCalculator.DIVERSITE to 10))
        assertTrue(badges.contains(BadgeCalculator.DIVERSITE to 25))
        assertFalse(badges.contains(BadgeCalculator.DIVERSITE to 50))
    }

    @Test
    fun saisons_et_quatre_saisons() {
        // Trois saisons seulement (printemps, été, automne) → pas de « quatre saisons ».
        val trois = ids(
            plain.compute(
                input(
                    flowerCount = 3,
                    geoTimes = listOf(
                        FlowerGeoTime(null, null, monthMillis(4)), // printemps
                        FlowerGeoTime(null, null, monthMillis(7)), // été
                        FlowerGeoTime(null, null, monthMillis(10)), // automne
                    ),
                ),
            ),
        )
        assertTrue(trois.contains(BadgeCalculator.SAISON_PRINTEMPS to 1))
        assertTrue(trois.contains(BadgeCalculator.SAISON_ETE to 1))
        assertTrue(trois.contains(BadgeCalculator.SAISON_AUTOMNE to 1))
        assertFalse(trois.contains(BadgeCalculator.SAISON_HIVER to 1))
        assertFalse(trois.contains(BadgeCalculator.QUATRE_SAISONS to 1))

        // Ajout de l'hiver (janvier) → « quatre saisons » débloqué.
        val quatre = ids(
            plain.compute(
                input(
                    flowerCount = 4,
                    geoTimes = listOf(
                        FlowerGeoTime(null, null, monthMillis(4)),
                        FlowerGeoTime(null, null, monthMillis(7)),
                        FlowerGeoTime(null, null, monthMillis(10)),
                        FlowerGeoTime(null, null, monthMillis(1)), // hiver
                    ),
                ),
            ),
        )
        assertTrue(quatre.contains(BadgeCalculator.SAISON_HIVER to 1))
        assertTrue(quatre.contains(BadgeCalculator.QUATRE_SAISONS to 1))
    }

    @Test
    fun lieux_distincts_grille_5km() {
        // Cinq points espacés de ~0,1° de latitude (~11 km) → 5 cellules distinctes.
        val geoTimes = (0 until 5).map { i ->
            FlowerGeoTime(latitude = 48.0 + i * 0.1, longitude = 2.0, createdAt = monthMillis(6))
        }
        val badges = ids(plain.compute(input(flowerCount = 5, geoTimes = geoTimes)))
        assertTrue(badges.contains(BadgeCalculator.LIEUX_DISTINCTS to 5))

        // Deux captures très proches (~10 m) ne comptent que pour une cellule.
        val proches = listOf(
            FlowerGeoTime(48.8566, 2.3522, monthMillis(6)),
            FlowerGeoTime(48.8567, 2.3523, monthMillis(6)),
        )
        val unLieu = ids(plain.compute(input(flowerCount = 2, geoTimes = proches)))
        assertFalse(unLieu.contains(BadgeCalculator.LIEUX_DISTINCTS to 5))
    }

    @Test
    fun explorateur_et_outremer_via_resolveur() {
        // Résolveur synthétique : deux régions (une métropole, une outre-mer).
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"code":"11","nom":"Métro","outreMer":false},
               "geometry":{"type":"Polygon","coordinates":[
                 [[2,48],[3,48],[3,49],[2,49],[2,48]]
               ]}},
              {"type":"Feature","properties":{"code":"04","nom":"Réunion","outreMer":true},
               "geometry":{"type":"Polygon","coordinates":[
                 [[55,-21],[56,-21],[56,-20],[55,-20],[55,-21]]
               ]}}
            ]}
        """.trimIndent()
        val resolver = RegionResolver.fromJson(json)
        val calc = BadgeCalculator(resolver, zone = ZoneId.of("UTC"))

        val geoTimes = listOf(
            FlowerGeoTime(48.5, 2.5, monthMillis(6)), // métropole
            FlowerGeoTime(-20.5, 55.5, monthMillis(6)), // outre-mer
        )
        val badges = ids(calc.compute(input(flowerCount = 2, geoTimes = geoTimes)))

        // Deux régions distinctes → palier Explorateur 2.
        assertTrue(badges.contains(BadgeCalculator.EXPLORATEUR to 2))
        // Badge outre-mer de la région visitée.
        assertTrue(badges.contains(BadgeCalculator.overseasBadgeId("04") to 1))
        // Pas de badge outre-mer pour la région métropolitaine.
        assertFalse(badges.contains(BadgeCalculator.overseasBadgeId("11") to 1))
    }

    @Test
    fun sans_resolveur_pas_de_badges_geographiques() {
        val geoTimes = listOf(FlowerGeoTime(48.5, 2.5, monthMillis(6)))
        val badges = plain.compute(input(flowerCount = 1, geoTimes = geoTimes))
        // Aucun badge explorateur/outre-mer sans résolveur (dégradation device-first).
        assertFalse(badges.any { it.badgeId == BadgeCalculator.EXPLORATEUR })
        assertFalse(badges.any { it.badgeId.startsWith(BadgeCalculator.OUTRE_MER_PREFIX) })
        // Mais les badges non géographiques restent calculés (grille, saison).
        assertTrue(badges.any { it.badgeId == BadgeCalculator.SAISON_ETE })
    }
}
