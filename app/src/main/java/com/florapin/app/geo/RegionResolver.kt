package com.florapin.app.geo

import android.content.Context
import com.florapin.app.location.GeoPoint
import com.squareup.moshi.JsonReader
import okio.buffer
import okio.source
import java.io.InputStream

/**
 * Région administrative prise en charge par FloraPin.
 *
 * @param countryCode code ISO 3166-1 alpha-2 du pays.
 * @param code code stable de la subdivision dans sa source officielle.
 * @param name libellé officiel.
 * @param overseas `true` pour les cinq régions d'outre-mer (Guadeloupe,
 *   Martinique, Guyane, La Réunion, Mayotte) — utile aux badges dédiés.
 */
data class Region(
    val countryCode: String,
    val code: String,
    val name: String,
    val overseas: Boolean,
)

/**
 * Mappe une position GPS vers une subdivision prise en charge, **entièrement
 * hors-ligne** (TÂCHE 5.2, débloque les badges géographiques locaux de 5.3).
 *
 * Les polygones des 18 régions françaises sont embarqués dans
 * `assets/regions-fr.geojson` (source
 * [france-geojson](https://github.com/gregoiredavid/france-geojson), version
 * simplifiée pour la métropole + outre-mer simplifié par Douglas-Peucker).
 * Les trois Régions belges sont dans `assets/regions-be.geojson` (source
 * [AdminVector NGI-IGN](https://www.odwb.be/explore/dataset/regionsgeweste-belgium/),
 * millésime 2025, géométries déjà simplifiées par le diffuseur).
 * Les 26 cantons suisses sont dans `assets/regions-ch.geojson` (source officielle
 * [swissBOUNDARIES3D — swisstopo](https://opendata.swiss/fr/dataset/swissboundaries3d-kantonsgrenzen),
 * millésime 2026, WGS84), simplifiée topologiquement avec Mapshaper en conservant
 * 30 % des sommets.
 * Les autres couches proviennent des sources officielles décrites dans
 * `docs/COUNTRY_SUBDIVISIONS.md` : ONS 2024 pour les neuf régions anglaises
 * (simplification topologique à 25 %), Tailte Éireann 2015 pour les quatre
 * provinces irlandaises (reprojection EPSG:2157 → WGS84), IGN Espagne pour les
 * 19 communautés et villes autonomes (6 %), Istat 2026 pour les 20 régions
 * italiennes (UTM 32N → WGS84) et GSI 2015 pour les huit macro-régions japonaises
 * (regroupement de 2 914 polygones municipaux).
 *
 * Le test d'appartenance est un *ray-casting* local, précédé d'un rejet par boîte
 * englobante pour la performance.
 *
 * Points d'attention respectés :
 *  - l'ordre GeoJSON est **`[longitude, latitude]`** (jamais l'inverse) ;
 *  - `Polygon` **et** `MultiPolygon` sont gérés, y compris les anneaux
 *    intérieurs (trous) — un point dans un trou n'appartient pas à la région ;
 *  - une fleur **sans GPS** n'est pas de notre ressort : l'appelant ne doit pas
 *    invoquer [resolve] (pas de région « inconnue » qui fausserait les paliers).
 *
 * L'objet est immuable et sûr à partager entre threads une fois construit ;
 * réutiliser une instance unique (le parsing du GeoJSON n'est fait qu'une fois).
 */
class RegionResolver private constructor(
    private val regions: List<CompiledRegion>,
) {

    /** Nombre de subdivisions chargées (107 attendu) — pratique pour les tests/diagnostics. */
    val regionCount: Int get() = regions.size

    /**
     * Renvoie la région contenant [point], ou `null` si la position n'appartient
     * à aucune subdivision prise en charge (mer, autre pays…).
     */
    fun resolve(point: GeoPoint): Region? = resolve(point.latitude, point.longitude)

    /**
     * Renvoie la région contenant la position (`latitude`, `longitude`) en degrés
     * décimaux, ou `null` si aucune ne la contient.
     */
    fun resolve(latitude: Double, longitude: Double): Region? {
        for (region in regions) {
            if (!region.bbox.contains(longitude, latitude)) continue
            for (polygon in region.polygons) {
                if (polygon.contains(longitude, latitude)) return region.region
            }
        }
        return null
    }

    // --- Représentation compilée : anneaux en tableaux plats [lng0,lat0,lng1,lat1,…] ---

    private class CompiledRegion(
        val region: Region,
        val polygons: List<CompiledPolygon>,
        val bbox: BBox,
    )

    /** Un polygone = un anneau extérieur + d'éventuels anneaux intérieurs (trous). */
    private class CompiledPolygon(
        val exterior: DoubleArray,
        val holes: List<DoubleArray>,
        val bbox: BBox,
    ) {
        fun contains(lng: Double, lat: Double): Boolean {
            if (!bbox.contains(lng, lat)) return false
            if (!rayCast(lng, lat, exterior)) return false
            for (hole in holes) if (rayCast(lng, lat, hole)) return false
            return true
        }
    }

    private class BBox(
        val minLng: Double,
        val minLat: Double,
        val maxLng: Double,
        val maxLat: Double,
    ) {
        fun contains(lng: Double, lat: Double): Boolean =
            lng in minLng..maxLng && lat in minLat..maxLat
    }

    companion object {
        const val ASSET_NAME = "regions-fr.geojson"
        const val BELGIUM_ASSET_NAME = "regions-be.geojson"
        const val SWITZERLAND_ASSET_NAME = "regions-ch.geojson"
        const val ENGLAND_ASSET_NAME = "regions-gb.geojson"
        const val IRELAND_ASSET_NAME = "regions-ie.geojson"
        const val SPAIN_ASSET_NAME = "regions-es.geojson"
        const val ITALY_ASSET_NAME = "regions-it.geojson"
        const val JAPAN_ASSET_NAME = "regions-jp.geojson"
        val ASSET_NAMES = listOf(
            ASSET_NAME,
            BELGIUM_ASSET_NAME,
            SWITZERLAND_ASSET_NAME,
            ENGLAND_ASSET_NAME,
            IRELAND_ASSET_NAME,
            SPAIN_ASSET_NAME,
            ITALY_ASSET_NAME,
            JAPAN_ASSET_NAME,
        )

        const val FRANCE_COUNTRY_CODE = "FR"
        const val BELGIUM_COUNTRY_CODE = "BE"
        const val SWITZERLAND_COUNTRY_CODE = "CH"
        const val ENGLAND_COUNTRY_CODE = "GB"
        const val IRELAND_COUNTRY_CODE = "IE"
        const val SPAIN_COUNTRY_CODE = "ES"
        const val ITALY_COUNTRY_CODE = "IT"
        const val JAPAN_COUNTRY_CODE = "JP"

        /** Charge le résolveur depuis tous les assets géographiques de l'application. */
        fun fromAssets(
            context: Context,
            assetNames: List<String> = ASSET_NAMES,
        ): RegionResolver {
            val compiled = assetNames.flatMap { assetName ->
                context.assets.open(assetName).use { input ->
                    parse(JsonReader.of(input.source().buffer()))
                }
            }
            return RegionResolver(compiled)
        }

        /** Charge le résolveur depuis un flux GeoJSON (utile aux tests). */
        fun fromInputStream(input: InputStream): RegionResolver =
            RegionResolver(parse(JsonReader.of(input.source().buffer())))

        /** Fusionne plusieurs flux GeoJSON dans un même résolveur (utile aux tests). */
        fun fromInputStreams(vararg inputs: InputStream): RegionResolver =
            RegionResolver(inputs.flatMap { parse(JsonReader.of(it.source().buffer())) })

        /** Charge le résolveur depuis une chaîne GeoJSON (utile aux tests). */
        fun fromJson(json: String): RegionResolver =
            fromInputStream(json.byteInputStream())

        // --- Parsing GeoJSON en streaming (aucune structure intermédiaire boxée) ---

        private fun parse(reader: JsonReader): List<CompiledRegion> {
            val regions = mutableListOf<CompiledRegion>()
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.nextName() == "features") {
                    reader.beginArray()
                    while (reader.hasNext()) regions += parseFeature(reader)
                    reader.endArray()
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
            return regions
        }

        private fun parseFeature(reader: JsonReader): CompiledRegion {
            var code = ""
            var name = ""
            var overseas = false
            var countryCode = FRANCE_COUNTRY_CODE
            var polygons: List<CompiledPolygon> = emptyList()
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "properties" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "code" -> code = reader.nextString()
                                "nom" -> name = reader.nextString()
                                "outreMer" -> overseas = reader.nextBoolean()
                                "countryCode" -> countryCode = reader.nextString()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                    "geometry" -> polygons = parseGeometry(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            val bbox = unionBBox(polygons)
            return CompiledRegion(
                Region(countryCode, code, name, overseas),
                polygons,
                bbox,
            )
        }

        private fun parseGeometry(reader: JsonReader): List<CompiledPolygon> {
            var type = ""
            var polygons: List<CompiledPolygon> = emptyList()
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "type" -> type = reader.nextString()
                    // Le GeoJSON embarqué met toujours `type` avant `coordinates`.
                    "coordinates" -> polygons = when (type) {
                        "Polygon" -> listOf(parsePolygon(reader))
                        "MultiPolygon" -> parseMultiPolygon(reader)
                        else -> {
                            reader.skipValue()
                            emptyList()
                        }
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return polygons
        }

        private fun parseMultiPolygon(reader: JsonReader): List<CompiledPolygon> {
            val polys = mutableListOf<CompiledPolygon>()
            reader.beginArray()
            while (reader.hasNext()) polys += parsePolygon(reader)
            reader.endArray()
            return polys
        }

        private fun parsePolygon(reader: JsonReader): CompiledPolygon {
            reader.beginArray()
            val poly = parsePolygonRings(reader)
            reader.endArray()
            return poly
        }

        /** Lit les anneaux d'un polygone dont le tableau englobant est déjà ouvert. */
        private fun parsePolygonRings(reader: JsonReader): CompiledPolygon {
            val exterior = parseRing(reader)
            val holes = mutableListOf<DoubleArray>()
            while (reader.hasNext()) holes += parseRing(reader)
            return makePolygon(exterior, holes)
        }

        private fun parseRing(reader: JsonReader): DoubleArray {
            reader.beginArray()
            val ring = parseRingPoints(reader)
            reader.endArray()
            return ring
        }

        /** Lit les points d'un anneau dont le tableau englobant est déjà ouvert. */
        private fun parseRingPoints(reader: JsonReader): DoubleArray {
            val coords = ArrayList<Double>(64)
            while (reader.hasNext()) {
                reader.beginArray()
                val lng = reader.nextDouble()
                val lat = reader.nextDouble()
                // Certaines sources ajoutent une altitude : on l'ignore.
                while (reader.hasNext()) reader.skipValue()
                reader.endArray()
                coords.add(lng)
                coords.add(lat)
            }
            return coords.toDoubleArray()
        }

        private fun makePolygon(exterior: DoubleArray, holes: List<DoubleArray>): CompiledPolygon =
            CompiledPolygon(exterior, holes, ringBBox(exterior))

        private fun ringBBox(ring: DoubleArray): BBox {
            var minLng = Double.POSITIVE_INFINITY
            var minLat = Double.POSITIVE_INFINITY
            var maxLng = Double.NEGATIVE_INFINITY
            var maxLat = Double.NEGATIVE_INFINITY
            var i = 0
            while (i < ring.size) {
                val lng = ring[i]
                val lat = ring[i + 1]
                if (lng < minLng) minLng = lng
                if (lng > maxLng) maxLng = lng
                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat
                i += 2
            }
            return BBox(minLng, minLat, maxLng, maxLat)
        }

        private fun unionBBox(polygons: List<CompiledPolygon>): BBox {
            var minLng = Double.POSITIVE_INFINITY
            var minLat = Double.POSITIVE_INFINITY
            var maxLng = Double.NEGATIVE_INFINITY
            var maxLat = Double.NEGATIVE_INFINITY
            for (p in polygons) {
                if (p.bbox.minLng < minLng) minLng = p.bbox.minLng
                if (p.bbox.minLat < minLat) minLat = p.bbox.minLat
                if (p.bbox.maxLng > maxLng) maxLng = p.bbox.maxLng
                if (p.bbox.maxLat > maxLat) maxLat = p.bbox.maxLat
            }
            return BBox(minLng, minLat, maxLng, maxLat)
        }

        /**
         * Ray-casting (algorithme even-odd) sur un anneau plat
         * `[lng0,lat0,lng1,lat1,…]`. Renvoie `true` si (`lng`,`lat`) est à
         * l'intérieur de l'anneau.
         */
        private fun rayCast(lng: Double, lat: Double, ring: DoubleArray): Boolean {
            var inside = false
            val n = ring.size / 2
            var j = n - 1
            var i = 0
            while (i < n) {
                val xi = ring[i * 2]
                val yi = ring[i * 2 + 1]
                val xj = ring[j * 2]
                val yj = ring[j * 2 + 1]
                if ((yi > lat) != (yj > lat) &&
                    lng < (xj - xi) * (lat - yi) / (yj - yi) + xi
                ) {
                    inside = !inside
                }
                j = i
                i++
            }
            return inside
        }
    }
}
