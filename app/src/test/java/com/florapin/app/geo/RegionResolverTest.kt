package com.florapin.app.geo

import com.florapin.app.location.GeoPoint
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du résolveur GPS → région (TÂCHE 5.2).
 *
 * On valide :
 *  - le chargement des 18 régions embarquées (`assets/regions-fr.geojson`) ;
 *  - la classification de villes connues (métropole + outre-mer) ;
 *  - le rejet des positions hors France (mer, étranger) ;
 *  - l'ordre `[longitude, latitude]`, la gestion des trous et des `MultiPolygon`
 *    via des géométries synthétiques contrôlées.
 */
class RegionResolverTest {

    /** Résolveur construit sur les vraies données embarquées. */
    private val resolver: RegionResolver by lazy {
        // cwd des tests Gradle = dossier du module `app/`.
        val asset = File("src/main/assets/${RegionResolver.ASSET_NAME}")
        assertTrue("Asset introuvable : ${asset.absolutePath}", asset.exists())
        asset.inputStream().use { RegionResolver.fromInputStream(it) }
    }

    @Test
    fun charge_les_18_regions() {
        assertEquals(18, resolver.regionCount)
    }

    @Test
    fun classe_les_villes_de_metropole() {
        // Points volontairement à l'intérieur des terres (une ville côtière peut
        // tomber hors du littoral simplifié).
        assertRegion("Île-de-France", 48.8566, 2.3522) // Paris
        assertRegion("Auvergne-Rhône-Alpes", 45.7640, 4.8357) // Lyon
        assertRegion("Provence-Alpes-Côte d'Azur", 43.9352, 6.0679) // Digne-les-Bains
        assertRegion("Occitanie", 43.6047, 1.4442) // Toulouse
        assertRegion("Nouvelle-Aquitaine", 45.1667, 0.7167) // Périgueux
        assertRegion("Grand Est", 48.5734, 7.7521) // Strasbourg
        assertRegion("Hauts-de-France", 50.6292, 3.0573) // Lille
        assertRegion("Bretagne", 48.1173, -1.6778) // Rennes
        assertRegion("Normandie", 49.1829, -0.3707) // Caen
        assertRegion("Pays de la Loire", 47.7500, -0.3300) // Sablé-sur-Sarthe (intérieur)
        assertRegion("Centre-Val de Loire", 47.9029, 1.9093) // Orléans
        assertRegion("Bourgogne-Franche-Comté", 47.3220, 5.0415) // Dijon
        assertRegion("Corse", 42.3061, 9.1490) // Corte (intérieur de l'île)
    }

    @Test
    fun classe_les_regions_d_outre_mer() {
        assertRegion("Guadeloupe", 16.2415, -61.5340, overseas = true)
        assertRegion("Martinique", 14.6036, -61.0678, overseas = true)
        assertRegion("Guyane", 4.9227, -52.3269, overseas = true)
        assertRegion("La Réunion", -20.8789, 55.4481, overseas = true)
        assertRegion("Mayotte", -12.7806, 45.2278, overseas = true)
    }

    @Test
    fun marque_outre_mer_correctement() {
        assertFalse(resolver.resolve(48.8566, 2.3522)!!.overseas) // Paris → métropole
        assertTrue(resolver.resolve(-20.8789, 55.4481)!!.overseas) // Réunion → outre-mer
    }

    @Test
    fun renvoie_null_hors_de_france() {
        assertNull(resolver.resolve(46.0, -5.0)) // Atlantique, à l'ouest de la Bretagne
        assertNull(resolver.resolve(51.5074, -0.1278)) // Londres
        assertNull(resolver.resolve(0.0, 0.0)) // golfe de Guinée
        assertNull(resolver.resolve(41.9028, 12.4964)) // Rome
    }

    @Test
    fun accepte_un_geopoint() {
        val paris = GeoPoint(latitude = 48.8566, longitude = 2.3522, accuracyMeters = 5f)
        assertEquals("Île-de-France", resolver.resolve(paris)?.name)
    }

    // --- Géométries synthétiques : on isole le ray-casting du jeu de données réel ---

    @Test
    fun respecte_l_ordre_longitude_latitude() {
        // Carré [lng 2..3] × [lat 48..49]. Le GeoJSON stocke [lng, lat].
        val r = RegionResolver.fromJson(square(code = "T1", nom = "Carré", overseas = false))
        // (lat=48.5, lng=2.5) est dedans.
        assertEquals("Carré", r.resolve(48.5, 2.5)?.name)
        // Inverser les axes (lat=2.5, lng=48.5) doit tomber dehors : preuve que
        // l'on n'a pas confondu latitude et longitude.
        assertNull(r.resolve(2.5, 48.5))
    }

    @Test
    fun exclut_les_points_dans_un_trou() {
        // Carré extérieur [0..10]² avec un trou central [4..6]².
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"code":"T2","nom":"Anneau","outreMer":false},
               "geometry":{"type":"Polygon","coordinates":[
                 [[0,0],[10,0],[10,10],[0,10],[0,0]],
                 [[4,4],[6,4],[6,6],[4,6],[4,4]]
               ]}}
            ]}
        """.trimIndent()
        val r = RegionResolver.fromJson(json)
        assertNotNull("Point dans l'anneau plein", r.resolve(1.0, 1.0)) // coin plein
        assertNull("Point dans le trou central", r.resolve(5.0, 5.0)) // trou
    }

    @Test
    fun gere_les_multipolygon() {
        // Deux carrés disjoints dans une même région.
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"code":"T3","nom":"Archipel","outreMer":true},
               "geometry":{"type":"MultiPolygon","coordinates":[
                 [[[0,0],[1,0],[1,1],[0,1],[0,0]]],
                 [[[5,5],[6,5],[6,6],[5,6],[5,5]]]
               ]}}
            ]}
        """.trimIndent()
        val r = RegionResolver.fromJson(json)
        assertEquals("Archipel", r.resolve(0.5, 0.5)?.name) // 1er polygone
        assertEquals("Archipel", r.resolve(5.5, 5.5)?.name) // 2e polygone
        assertTrue(r.resolve(0.5, 0.5)!!.overseas)
        assertNull(r.resolve(3.0, 3.0)) // entre les deux
    }

    private fun assertRegion(
        expected: String,
        latitude: Double,
        longitude: Double,
        overseas: Boolean? = null,
    ) {
        val region = resolver.resolve(latitude, longitude)
        assertNotNull("Aucune région pour ($latitude, $longitude)", region)
        assertEquals(expected, region!!.name)
        if (overseas != null) assertEquals(overseas, region.overseas)
    }

    /** Carré [lng 2..3] × [lat 48..49] au format GeoJSON `[lng, lat]`. */
    private fun square(code: String, nom: String, overseas: Boolean): String = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","properties":{"code":"$code","nom":"$nom","outreMer":$overseas},
           "geometry":{"type":"Polygon","coordinates":[
             [[2,48],[3,48],[3,49],[2,49],[2,48]]
           ]}}
        ]}
    """.trimIndent()
}
