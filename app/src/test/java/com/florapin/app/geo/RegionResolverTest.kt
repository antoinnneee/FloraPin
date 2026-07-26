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
 *  - le chargement des 107 subdivisions embarquées dans huit territoires ;
 *  - la classification de villes connues dans chaque subdivision ;
 *  - le rejet des positions hors des territoires pris en charge ;
 *  - l'ordre `[longitude, latitude]`, la gestion des trous et des `MultiPolygon`
 *    via des géométries synthétiques contrôlées.
 */
class RegionResolverTest {

    /** Résolveur construit sur tous les vrais jeux de données embarqués. */
    private val resolver: RegionResolver by lazy {
        // cwd des tests Gradle = dossier du module `app/`.
        val assets = RegionResolver.ASSET_NAMES.map { File("src/main/assets/$it") }
        assets.forEach { asset ->
            assertTrue("Asset introuvable : ${asset.absolutePath}", asset.exists())
        }
        val streams = assets.map { it.inputStream() }
        try {
            RegionResolver.fromInputStreams(*streams.toTypedArray())
        } finally {
            streams.forEach { it.close() }
        }
    }

    @Test
    fun charge_les_107_subdivisions_des_huit_territoires() {
        assertEquals(107, resolver.regionCount)
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
    fun classe_les_trois_regions_belges() {
        assertRegion(
            "Région flamande",
            51.2194,
            4.4025,
            countryCode = RegionResolver.BELGIUM_COUNTRY_CODE,
        ) // Anvers
        assertRegion(
            "Région wallonne",
            50.4674,
            4.8718,
            countryCode = RegionResolver.BELGIUM_COUNTRY_CODE,
        ) // Namur
        assertRegion(
            "Région de Bruxelles-Capitale",
            50.8503,
            4.3517,
            countryCode = RegionResolver.BELGIUM_COUNTRY_CODE,
        ) // Bruxelles
    }

    @Test
    fun classe_les_26_cantons_suisses() {
        val ch = RegionResolver.SWITZERLAND_COUNTRY_CODE
        assertRegion("Argovie", 47.3925, 8.0442, countryCode = ch) // Aarau
        assertRegion("Appenzell Rhodes-Intérieures", 47.3310, 9.4096, countryCode = ch)
        assertRegion("Appenzell Rhodes-Extérieures", 47.3860, 9.2790, countryCode = ch) // Herisau
        assertRegion("Berne", 46.9480, 7.4474, countryCode = ch)
        assertRegion("Bâle-Campagne", 47.4845, 7.7345, countryCode = ch) // Liestal
        assertRegion("Bâle-Ville", 47.5596, 7.5886, countryCode = ch)
        assertRegion("Fribourg", 46.8065, 7.1620, countryCode = ch)
        assertRegion("Genève", 46.2044, 6.1432, countryCode = ch)
        assertRegion("Glaris", 47.0406, 9.0680, countryCode = ch)
        assertRegion("Grisons", 46.8508, 9.5320, countryCode = ch) // Coire
        assertRegion("Jura", 47.3649, 7.3445, countryCode = ch) // Delémont
        assertRegion("Lucerne", 47.0502, 8.3093, countryCode = ch)
        assertRegion("Neuchâtel", 46.9900, 6.9293, countryCode = ch)
        assertRegion("Nidwald", 46.9580, 8.3661, countryCode = ch) // Stans
        assertRegion("Obwald", 46.8961, 8.2467, countryCode = ch) // Sarnen
        assertRegion("Saint-Gall", 47.4245, 9.3767, countryCode = ch)
        assertRegion("Schaffhouse", 47.6960, 8.6342, countryCode = ch)
        assertRegion("Soleure", 47.2088, 7.5323, countryCode = ch)
        assertRegion("Schwytz", 47.0207, 8.6520, countryCode = ch)
        assertRegion("Thurgovie", 47.5584, 8.8985, countryCode = ch) // Frauenfeld
        assertRegion("Tessin", 46.1950, 9.0220, countryCode = ch) // Bellinzone
        assertRegion("Uri", 46.8804, 8.6444, countryCode = ch) // Altdorf
        assertRegion("Vaud", 46.5197, 6.6323, countryCode = ch) // Lausanne
        assertRegion("Valais", 46.2333, 7.3600, countryCode = ch) // Sion
        assertRegion("Zoug", 47.1662, 8.5155, countryCode = ch)
        assertRegion("Zurich", 47.3769, 8.5417, countryCode = ch)
    }

    @Test
    fun classe_les_neuf_regions_anglaises() {
        val gb = RegionResolver.ENGLAND_COUNTRY_CODE
        assertRegion("Nord-Est", 54.9783, -1.6178, countryCode = gb)
        assertRegion("Nord-Ouest", 53.4808, -2.2426, countryCode = gb)
        assertRegion("Yorkshire-et-Humber", 53.8008, -1.5491, countryCode = gb)
        assertRegion("Midlands de l'Est", 52.9548, -1.1581, countryCode = gb)
        assertRegion("Midlands de l'Ouest", 52.4862, -1.8904, countryCode = gb)
        assertRegion("Est de l'Angleterre", 52.2053, 0.1218, countryCode = gb)
        assertRegion("Londres", 51.5074, -0.1278, countryCode = gb)
        assertRegion("Sud-Est", 51.7520, -1.2577, countryCode = gb)
        assertRegion("Sud-Ouest", 51.4545, -2.5879, countryCode = gb)
    }

    @Test
    fun classe_les_quatre_provinces_irlandaises() {
        val ie = RegionResolver.IRELAND_COUNTRY_CODE
        assertRegion("Connacht", 53.2707, -9.0568, countryCode = ie)
        assertRegion("Leinster", 53.3498, -6.2603, countryCode = ie)
        assertRegion("Munster", 51.8985, -8.4756, countryCode = ie)
        assertRegion("Ulster", 54.9566, -7.7344, countryCode = ie)
    }

    @Test
    fun classe_les_19_communautes_et_villes_autonomes_espagnoles() {
        val es = RegionResolver.SPAIN_COUNTRY_CODE
        val cases = listOf(
            Triple("Andalousie", 37.3891, -5.9845),
            Triple("Aragon", 41.6488, -0.8891),
            Triple("Asturies", 43.3614, -5.8494),
            Triple("Îles Baléares", 39.5696, 2.6502),
            Triple("Îles Canaries", 28.1235, -15.4363),
            Triple("Cantabrie", 43.4623, -3.8100),
            Triple("Castille-et-León", 41.6523, -4.7245),
            Triple("Castille-La Manche", 39.8628, -4.0273),
            Triple("Catalogne", 41.3874, 2.1686),
            Triple("Communauté valencienne", 39.4699, -0.3763),
            Triple("Estrémadure", 38.9170, -6.3430),
            Triple("Galice", 42.8782, -8.5448),
            Triple("Communauté de Madrid", 40.4168, -3.7038),
            Triple("Région de Murcie", 37.9922, -1.1307),
            Triple("Communauté forale de Navarre", 42.8125, -1.6458),
            Triple("Pays basque", 42.8467, -2.6727),
            Triple("La Rioja", 42.4627, -2.4450),
            Triple("Ceuta", 35.8894, -5.3213),
            Triple("Melilla", 35.2923, -2.9381),
        )
        cases.forEach { (name, lat, lng) -> assertRegion(name, lat, lng, countryCode = es) }
    }

    @Test
    fun classe_les_20_regions_italiennes() {
        val it = RegionResolver.ITALY_COUNTRY_CODE
        val cases = listOf(
            Triple("Piémont", 45.0703, 7.6869),
            Triple("Vallée d'Aoste", 45.7375, 7.3201),
            Triple("Lombardie", 45.4642, 9.1900),
            Triple("Trentin-Haut-Adige", 46.0748, 11.1217),
            Triple("Vénétie", 45.4408, 12.3155),
            Triple("Frioul-Vénétie Julienne", 45.6495, 13.7768),
            Triple("Ligurie", 44.4056, 8.9463),
            Triple("Émilie-Romagne", 44.4949, 11.3426),
            Triple("Toscane", 43.7696, 11.2558),
            Triple("Ombrie", 43.1107, 12.3908),
            Triple("Marches", 43.6158, 13.5189),
            Triple("Latium", 41.9028, 12.4964),
            Triple("Abruzzes", 42.3498, 13.3995),
            Triple("Molise", 41.5603, 14.6627),
            Triple("Campanie", 40.8518, 14.2681),
            Triple("Pouilles", 41.1171, 16.8719),
            Triple("Basilicate", 40.6404, 15.8056),
            Triple("Calabre", 38.9098, 16.5877),
            Triple("Sicile", 38.1157, 13.3615),
            Triple("Sardaigne", 39.2238, 9.1217),
        )
        cases.forEach { (name, lat, lng) -> assertRegion(name, lat, lng, countryCode = it) }
    }

    @Test
    fun classe_les_huit_macro_regions_japonaises() {
        val jp = RegionResolver.JAPAN_COUNTRY_CODE
        assertRegion("Hokkaidō", 43.0618, 141.3545, countryCode = jp)
        assertRegion("Tōhoku", 38.2682, 140.8694, countryCode = jp)
        assertRegion("Kantō", 35.6762, 139.6503, countryCode = jp)
        assertRegion("Chūbu", 35.1815, 136.9066, countryCode = jp)
        assertRegion("Kansai", 34.6937, 135.5023, countryCode = jp)
        assertRegion("Chūgoku", 34.3853, 132.4553, countryCode = jp)
        assertRegion("Shikoku", 33.8392, 132.7657, countryCode = jp)
        assertRegion("Kyūshū–Okinawa", 33.5902, 130.4017, countryCode = jp)
        assertRegion("Kyūshū–Okinawa", 26.2124, 127.6809, countryCode = jp)
    }

    @Test
    fun marque_outre_mer_correctement() {
        assertFalse(resolver.resolve(48.8566, 2.3522)!!.overseas) // Paris → métropole
        assertTrue(resolver.resolve(-20.8789, 55.4481)!!.overseas) // Réunion → outre-mer
    }

    @Test
    fun renvoie_null_hors_des_territoires_pris_en_charge() {
        assertNull(resolver.resolve(46.0, -5.0)) // Atlantique, à l'ouest de la Bretagne
        assertNull(resolver.resolve(0.0, 0.0)) // golfe de Guinée
        assertNull(resolver.resolve(52.3676, 4.9041)) // Amsterdam
        assertNull(resolver.resolve(52.5200, 13.4050)) // Berlin
        assertNull(resolver.resolve(40.7128, -74.0060)) // New York
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
        countryCode: String? = null,
    ) {
        val region = resolver.resolve(latitude, longitude)
        assertNotNull("Aucune région pour ($latitude, $longitude)", region)
        assertEquals(expected, region!!.name)
        if (overseas != null) assertEquals(overseas, region.overseas)
        if (countryCode != null) assertEquals(countryCode, region.countryCode)
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
