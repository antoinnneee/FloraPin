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
 * géographiques (badges pays, Explorateur et outre-mer) via un résolveur
 * synthétique.
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
    fun france_explorateur_et_outremer_via_resolveur() {
        // Résolveur synthétique : deux régions (une métropole, une outre-mer).
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"countryCode":"FR","code":"11","nom":"Métro","outreMer":false},
               "geometry":{"type":"Polygon","coordinates":[
                 [[2,48],[3,48],[3,49],[2,49],[2,48]]
               ]}},
              {"type":"Feature","properties":{"countryCode":"FR","code":"04","nom":"Réunion","outreMer":true},
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

        // Deux régions françaises → palier France 2.
        assertTrue(badges.contains(BadgeCalculator.FRANCE to 2))
        // L'Explorateur global démarre dès la première région, mais pas encore à 5.
        assertTrue(badges.contains(BadgeCalculator.EXPLORATEUR to 1))
        assertFalse(badges.contains(BadgeCalculator.EXPLORATEUR to 5))
        // Badge outre-mer de la région visitée.
        assertTrue(badges.contains(BadgeCalculator.overseasBadgeId("04") to 1))
        // Pas de badge outre-mer pour la région métropolitaine.
        assertFalse(badges.contains(BadgeCalculator.overseasBadgeId("11") to 1))
    }

    @Test
    fun belgique_alimente_explorateur_sans_fausser_france() {
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"countryCode":"FR","code":"11","nom":"France","outreMer":false},
               "geometry":{"type":"Polygon","coordinates":[
                 [[2,48],[3,48],[3,49],[2,49],[2,48]]
               ]}},
              {"type":"Feature","properties":{"countryCode":"BE","code":"02000","nom":"Flandre","outreMer":false},
               "geometry":{"type":"Polygon","coordinates":[
                 [[4,50],[5,50],[5,51],[4,51],[4,50]]
               ]}},
              {"type":"Feature","properties":{"countryCode":"BE","code":"03000","nom":"Wallonie","outreMer":false},
               "geometry":{"type":"Polygon","coordinates":[
                 [[5,49],[6,49],[6,50],[5,50],[5,49]]
               ]}}
            ]}
        """.trimIndent()
        val calc = BadgeCalculator(
            RegionResolver.fromJson(json),
            zone = ZoneId.of("UTC"),
        )
        val geoTimes = listOf(
            FlowerGeoTime(48.5, 2.5, monthMillis(6)), // une seule région française
            FlowerGeoTime(50.5, 4.5, monthMillis(6)), // Flandre
            FlowerGeoTime(49.5, 5.5, monthMillis(6)), // Wallonie
        )

        val badges = ids(calc.compute(input(flowerCount = 3, geoTimes = geoTimes)))
        val progress = calc.progress(input(flowerCount = 3, geoTimes = geoTimes))

        assertTrue(badges.contains(BadgeCalculator.BELGIQUE to 1))
        assertTrue(badges.contains(BadgeCalculator.EXPLORATEUR to 1))
        assertFalse(badges.any { it.first == BadgeCalculator.FRANCE })
        assertEquals(1, progress.franceRegionCount)
        assertEquals(1, progress.belgiumVisited)
        assertEquals(3, progress.exploredRegionCount)
    }

    @Test
    fun suisse_alimente_explorateur_sans_fausser_france_ni_belgique() {
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"countryCode":"FR","code":"11","nom":"France","outreMer":false},
               "geometry":{"type":"Polygon","coordinates":[
                 [[2,48],[3,48],[3,49],[2,49],[2,48]]
               ]}},
              {"type":"Feature","properties":{"countryCode":"BE","code":"02000","nom":"Flandre","outreMer":false},
               "geometry":{"type":"Polygon","coordinates":[
                 [[4,50],[5,50],[5,51],[4,51],[4,50]]
               ]}},
              {"type":"Feature","properties":{"countryCode":"CH","code":"VD","nom":"Vaud","outreMer":false},
               "geometry":{"type":"Polygon","coordinates":[
                 [[6,46],[7,46],[7,47],[6,47],[6,46]]
               ]}},
              {"type":"Feature","properties":{"countryCode":"CH","code":"ZH","nom":"Zurich","outreMer":false},
               "geometry":{"type":"Polygon","coordinates":[
                 [[8,47],[9,47],[9,48],[8,48],[8,47]]
               ]}}
            ]}
        """.trimIndent()
        val calc = BadgeCalculator(
            RegionResolver.fromJson(json),
            zone = ZoneId.of("UTC"),
        )
        val geoTimes = listOf(
            FlowerGeoTime(48.5, 2.5, monthMillis(6)), // France
            FlowerGeoTime(50.5, 4.5, monthMillis(6)), // Belgique
            FlowerGeoTime(46.5, 6.5, monthMillis(6)), // Vaud
            FlowerGeoTime(47.5, 8.5, monthMillis(6)), // Zurich
            FlowerGeoTime(47.6, 8.6, monthMillis(6)), // Zurich, doublon régional
        )

        val badges = ids(calc.compute(input(flowerCount = 5, geoTimes = geoTimes)))
        val progress = calc.progress(input(flowerCount = 5, geoTimes = geoTimes))

        assertTrue(badges.contains(BadgeCalculator.SUISSE to 1))
        assertTrue(badges.contains(BadgeCalculator.BELGIQUE to 1))
        assertEquals(1, progress.franceRegionCount)
        assertEquals(1, progress.belgiumVisited)
        assertEquals(1, progress.switzerlandVisited)
        assertEquals(4, progress.exploredRegionCount)
    }

    @Test
    fun explorateur_compte_cinq_regions_de_plusieurs_pays() {
        val features = (0 until 5).joinToString(",") { index ->
            val country = when {
                index < 2 -> "FR"
                index < 4 -> "BE"
                else -> "CH"
            }
            val minLng = index * 2
            """
              {"type":"Feature","properties":{"countryCode":"$country","code":"R$index","nom":"R$index","outreMer":false},
               "geometry":{"type":"Polygon","coordinates":[
                 [[$minLng,0],[${minLng + 1},0],[${minLng + 1},1],[$minLng,1],[$minLng,0]]
               ]}}
            """.trimIndent()
        }
        val resolver = RegionResolver.fromJson(
            """{"type":"FeatureCollection","features":[$features]}""",
        )
        val calc = BadgeCalculator(resolver, zone = ZoneId.of("UTC"))
        val geoTimes = (0 until 5).map { index ->
            FlowerGeoTime(0.5, index * 2.0 + 0.5, monthMillis(6))
        }

        val badges = ids(calc.compute(input(flowerCount = 5, geoTimes = geoTimes)))

        assertTrue(badges.contains(BadgeCalculator.EXPLORATEUR to 1))
        assertTrue(badges.contains(BadgeCalculator.EXPLORATEUR to 5))
        assertFalse(badges.contains(BadgeCalculator.EXPLORATEUR to 10))
    }

    @Test
    fun nouveaux_pays_debloquent_leur_badge_et_alimentent_explorateur() {
        val countries = listOf("GB", "IE", "ES", "IT", "JP")
        val features = countries.mapIndexed { index, country ->
            val minLng = index * 2
            """
              {"type":"Feature","properties":{"countryCode":"$country","code":"R$index","nom":"R$index","outreMer":false},
               "geometry":{"type":"Polygon","coordinates":[
                 [[$minLng,0],[${minLng + 1},0],[${minLng + 1},1],[$minLng,1],[$minLng,0]]
               ]}}
            """.trimIndent()
        }.joinToString(",")
        val calc = BadgeCalculator(
            RegionResolver.fromJson("""{"type":"FeatureCollection","features":[$features]}"""),
            zone = ZoneId.of("UTC"),
        )
        val points = countries.indices.map { index ->
            FlowerGeoTime(0.5, index * 2.0 + 0.5, monthMillis(6))
        } + FlowerGeoTime(0.6, 8.6, monthMillis(6)) // doublon dans la région japonaise

        val badges = ids(calc.compute(input(flowerCount = points.size, geoTimes = points)))
        val progress = calc.progress(input(flowerCount = points.size, geoTimes = points))

        listOf(
            BadgeCalculator.ANGLETERRE,
            BadgeCalculator.IRLANDE,
            BadgeCalculator.ESPAGNE,
            BadgeCalculator.ITALIE,
            BadgeCalculator.JAPON,
        ).forEach { badgeId -> assertTrue(badges.contains(badgeId to 1)) }
        assertTrue(badges.contains(BadgeCalculator.EXPLORATEUR to 5))
        assertEquals(5, progress.exploredRegionCount)
        assertEquals(1, progress.englandVisited)
        assertEquals(1, progress.irelandVisited)
        assertEquals(1, progress.spainVisited)
        assertEquals(1, progress.italyVisited)
        assertEquals(1, progress.japanVisited)
    }

    @Test
    fun sans_resolveur_pas_de_badges_geographiques() {
        val geoTimes = listOf(FlowerGeoTime(48.5, 2.5, monthMillis(6)))
        val badges = plain.compute(input(flowerCount = 1, geoTimes = geoTimes))
        // Aucun badge géographique sans résolveur (dégradation device-first).
        assertFalse(badges.any { it.badgeId == BadgeCalculator.FRANCE })
        assertFalse(badges.any { it.badgeId == BadgeCalculator.EXPLORATEUR })
        assertFalse(badges.any { it.badgeId == BadgeCalculator.BELGIQUE })
        assertFalse(badges.any { it.badgeId == BadgeCalculator.SUISSE })
        assertFalse(badges.any { it.badgeId == BadgeCalculator.ANGLETERRE })
        assertFalse(badges.any { it.badgeId == BadgeCalculator.IRLANDE })
        assertFalse(badges.any { it.badgeId == BadgeCalculator.ESPAGNE })
        assertFalse(badges.any { it.badgeId == BadgeCalculator.ITALIE })
        assertFalse(badges.any { it.badgeId == BadgeCalculator.JAPON })
        assertFalse(badges.any { it.badgeId.startsWith(BadgeCalculator.OUTRE_MER_PREFIX) })
        // Mais les badges non géographiques restent calculés (grille, saison).
        assertTrue(badges.any { it.badgeId == BadgeCalculator.SAISON_ETE })
    }
}
