package com.florapin.app.badges

import com.florapin.app.data.FlowerGeoTime
import com.florapin.app.geo.RegionResolver
import java.time.Instant
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.floor

/**
 * Un palier de badge débloqué, tel que produit par le calcul local (TÂCHE 5.3).
 *
 * @param badgeId identifiant stable du badge (voir [BadgeCalculator] pour le
 *   catalogue des constantes et le format des ids paramétrés — saisons, régions).
 * @param tier valeur du palier atteint. Pour les badges à seuils, c'est le seuil
 *   franchi (ex. `50` pour « Herbier 50 ») ; les badges à palier unique valent `1`.
 */
data class UnlockedBadge(val badgeId: String, val tier: Int)

/**
 * Calcul **100 % local** des badges « collection » à partir des fleurs de
 * l'appareil (TÂCHE 5.3). Classe pure et sans dépendance Android : la couche de
 * persistance (déblocage/célébration) vit dans
 * [com.florapin.app.data.BadgeRepository].
 *
 * Catalogue :
 *  - 🌸 **Première fleur** — dès la première capture.
 *  - 📚 **Herbier** — nombre de fleurs : 10 / 50 / 100 / 250.
 *  - 🌿 **Diversité** — espèces distinctes : 10 / 25 / 50.
 *  - 🌷☀️🍁❄️ **Saisons** — une capture au printemps / été / automne / hiver ;
 *    🍂 **Quatre saisons** quand les quatre sont réunies.
 *  - 🧭 **Explorateur** — régions françaises distinctes : 2 / 5 / 10 / 15 / 18.
 *  - 🏝️ **Outre-mer** — un badge par région d'outre-mer visitée (5 au total).
 *  - 📍 **Lieux distincts** — cellules d'une grille ~5 km : 5 / 15 / 30 / 50 / 100.
 *
 * Choix documentés :
 *  - **Saisons codées pour l'hémisphère nord** (France d'abord) : mois 3–5 =
 *    printemps, 6–8 = été, 9–11 = automne, 12/1/2 = hiver. Le mois est déterminé
 *    dans le fuseau [zone] (par défaut Europe/Paris) à partir de la date de
 *    capture.
 *  - **Grille 5 km** : la largeur d'un degré de longitude variant avec la
 *    latitude, la cellule vaut `floor(lat/Δ)` × `floor(lng / (Δ/cos(lat)))` avec
 *    `Δ = 5 km / 111,32 km` (degrés de latitude pour 5 km). C'est une
 *    approximation locale (on prend le `cos` de la latitude du point), largement
 *    suffisante pour distinguer des lieux d'observation.
 *  - Les fleurs **sans GPS** ne comptent ni pour la grille ni pour les régions,
 *    mais comptent pour les saisons (date de capture toujours présente).
 *  - Si [regionResolver] est `null` (assets indisponibles), les badges
 *    Explorateur / Outre-mer sont simplement omis (dégradation device-first).
 *
 * @param regionResolver résolveur GPS → région (TÂCHE 5.2), ou `null` pour
 *   ignorer les badges géographiques par région.
 * @param zone fuseau utilisé pour dater les saisons (défaut : Europe/Paris).
 */
class BadgeCalculator(
    private val regionResolver: RegionResolver?,
    private val zone: ZoneId = DEFAULT_ZONE,
) {

    /** Entrées agrégées du calcul (fournies par les DAO). */
    data class Input(
        /** Nombre de fleurs actives (Herbier). */
        val flowerCount: Int,
        /** Nombre d'espèces distinctes (Diversité). */
        val distinctSpeciesCount: Int,
        /** Coordonnées + dates de capture des fleurs actives. */
        val geoTimes: List<FlowerGeoTime>,
    )

    /**
     * Valeurs brutes courantes par famille (numérateurs de progression, TÂCHE 5.5).
     * Les compteurs géographiques valent [UNAVAILABLE] quand le résolveur est absent.
     */
    data class Progress(
        /** Nombre de fleurs actives (📚 Herbier). */
        val flowerCount: Int,
        /** Nombre d'espèces distinctes (🌿 Diversité). */
        val distinctSpeciesCount: Int,
        /** Nombre de saisons distinctes couvertes (🍂 Saisons, 0..4). */
        val seasonCount: Int,
        /** Nombre de régions françaises distinctes (🧭 Explorateur), ou [UNAVAILABLE]. */
        val regionCount: Int,
        /** Nombre de régions d'outre-mer distinctes (🏝️ Outre-mer), ou [UNAVAILABLE]. */
        val overseasCount: Int,
        /** Nombre de lieux distincts (📍 grille ~5 km). */
        val cellCount: Int,
    )

    /**
     * Renvoie tous les paliers débloqués (badge + tier). Tous les paliers atteints
     * sont émis, pas seulement le plus haut : « Herbier 50 » implique aussi
     * « Herbier 10 » (deux lignes), ce qui permet à l'UI de compter les étoiles.
     */
    fun compute(input: Input): List<UnlockedBadge> {
        val result = mutableListOf<UnlockedBadge>()

        // 🌸 Première fleur.
        if (input.flowerCount >= 1) result += UnlockedBadge(PREMIERE_FLEUR, 1)

        // 📚 Herbier · 🌿 Diversité (badges à seuils cumulatifs).
        result += tiered(HERBIER, input.flowerCount, HERBIER_TIERS)
        result += tiered(DIVERSITE, input.distinctSpeciesCount, DIVERSITE_TIERS)

        // 🌷☀️🍁❄️ Saisons + 🍂 Quatre saisons.
        val seasons = seasonsOf(input.geoTimes)
        for (season in seasons) result += UnlockedBadge(seasonBadgeId(season), 1)
        if (seasons.size == Season.values().size) result += UnlockedBadge(QUATRE_SAISONS, 1)

        // 🧭 Explorateur · 🏝️ Outre-mer (nécessitent le résolveur de régions).
        val regions = regionsOf(input.geoTimes)
        if (regions != null) {
            result += tiered(EXPLORATEUR, regions.all.size, EXPLORATEUR_TIERS)
            for (code in regions.overseas) result += UnlockedBadge(overseasBadgeId(code), 1)
        }

        // 📍 Lieux distincts (grille ~5 km).
        result += tiered(LIEUX_DISTINCTS, cellsOf(input.geoTimes).size, LIEUX_TIERS)

        return result
    }

    /**
     * Valeurs **brutes** courantes par famille (TÂCHE 5.5). Là où [compute] ne
     * renvoie que les seuils franchis, ce calcul donne le numérateur exact de la
     * progression affichée par l'UI (« 34 / 50 ») : nombre de fleurs, d'espèces,
     * de saisons, de régions, de régions d'outre-mer et de lieux distincts.
     *
     * Les compteurs géographiques valent [UNAVAILABLE] (`-1`) quand le résolveur de
     * régions est absent (assets indisponibles) : l'UI grise alors les badges
     * Explorateur / Outre-mer plutôt que d'afficher un faux « 0 » (device-first).
     */
    fun progress(input: Input): Progress {
        val regions = regionsOf(input.geoTimes)
        return Progress(
            flowerCount = input.flowerCount,
            distinctSpeciesCount = input.distinctSpeciesCount,
            seasonCount = seasonsOf(input.geoTimes).size,
            regionCount = regions?.all?.size ?: UNAVAILABLE,
            overseasCount = regions?.overseas?.size ?: UNAVAILABLE,
            cellCount = cellsOf(input.geoTimes).size,
        )
    }

    /** Ensemble des saisons couvertes par les captures. */
    private fun seasonsOf(geoTimes: List<FlowerGeoTime>): Set<Season> =
        geoTimes.mapTo(mutableSetOf()) { seasonOf(it.createdAt) }

    /** Régions (toutes / outre-mer) couvertes, ou `null` si le résolveur est absent. */
    private fun regionsOf(geoTimes: List<FlowerGeoTime>): Regions? {
        val resolver = regionResolver ?: return null
        val all = mutableSetOf<String>()
        val overseas = mutableSetOf<String>()
        for (gt in geoTimes) {
            val lat = gt.latitude ?: continue
            val lng = gt.longitude ?: continue
            val region = resolver.resolve(lat, lng) ?: continue
            all += region.code
            if (region.overseas) overseas += region.code
        }
        return Regions(all, overseas)
    }

    /** Cellules distinctes de la grille ~5 km couvertes par les captures géolocalisées. */
    private fun cellsOf(geoTimes: List<FlowerGeoTime>): Set<Long> {
        val cells = mutableSetOf<Long>()
        for (gt in geoTimes) {
            val lat = gt.latitude ?: continue
            val lng = gt.longitude ?: continue
            cells += gridCellKey(lat, lng)
        }
        return cells
    }

    private data class Regions(val all: Set<String>, val overseas: Set<String>)

    /** Paliers d'un badge à seuils : un [UnlockedBadge] par seuil atteint. */
    private fun tiered(badgeId: String, value: Int, thresholds: IntArray): List<UnlockedBadge> =
        thresholds.filter { value >= it }.map { UnlockedBadge(badgeId, it) }

    /**
     * Clé unique de la cellule de grille ~5 km contenant (`lat`, `lng`). On combine
     * les deux index de cellule en un `Long` (index de latitude dans les bits
     * hauts) : ils tiennent tous deux largement dans 32 bits pour des coordonnées
     * terrestres.
     */
    private fun gridCellKey(lat: Double, lng: Double): Long {
        val latCell = floor(lat / GRID_DELTA_LAT).toLong()
        // Largeur d'une cellule en longitude à cette latitude (bornée pour éviter
        // une division par ~0 aux pôles ; sans effet aux latitudes françaises).
        val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(MIN_COS_LAT)
        val deltaLng = GRID_DELTA_LAT / cosLat
        val lngCell = floor(lng / deltaLng).toLong()
        return (latCell shl 32) xor (lngCell and 0xFFFFFFFFL)
    }

    private fun seasonOf(epochMillis: Long): Season {
        val month = Instant.ofEpochMilli(epochMillis).atZone(zone).monthValue
        return when (month) {
            3, 4, 5 -> Season.PRINTEMPS
            6, 7, 8 -> Season.ETE
            9, 10, 11 -> Season.AUTOMNE
            else -> Season.HIVER // 12, 1, 2
        }
    }

    /** Saisons de l'hémisphère nord. */
    private enum class Season { PRINTEMPS, ETE, AUTOMNE, HIVER }

    private fun seasonBadgeId(season: Season): String = when (season) {
        Season.PRINTEMPS -> SAISON_PRINTEMPS
        Season.ETE -> SAISON_ETE
        Season.AUTOMNE -> SAISON_AUTOMNE
        Season.HIVER -> SAISON_HIVER
    }

    companion object {
        /** Fuseau par défaut pour dater les saisons (France d'abord). */
        val DEFAULT_ZONE: ZoneId = ZoneId.of("Europe/Paris")

        /** Compteur indisponible (résolveur de régions absent — badges géo grisés). */
        const val UNAVAILABLE = -1

        // --- Identifiants de badges (stables : ne pas renommer sans migration UI) ---
        const val PREMIERE_FLEUR = "premiere_fleur"
        const val HERBIER = "herbier"
        const val DIVERSITE = "diversite"
        const val SAISON_PRINTEMPS = "saison_printemps"
        const val SAISON_ETE = "saison_ete"
        const val SAISON_AUTOMNE = "saison_automne"
        const val SAISON_HIVER = "saison_hiver"
        const val QUATRE_SAISONS = "quatre_saisons"
        const val EXPLORATEUR = "explorateur"
        const val LIEUX_DISTINCTS = "lieux_distincts"

        /** Préfixe des badges outre-mer paramétrés par code région (ex. `outremer:01`). */
        const val OUTRE_MER_PREFIX = "outremer:"

        /** Id du badge outre-mer d'une région donnée (code INSEE de région). */
        fun overseasBadgeId(regionCode: String): String = OUTRE_MER_PREFIX + regionCode

        // --- Paliers (seuils cumulatifs) ---
        val HERBIER_TIERS = intArrayOf(10, 50, 100, 250)
        val DIVERSITE_TIERS = intArrayOf(10, 25, 50)
        val EXPLORATEUR_TIERS = intArrayOf(2, 5, 10, 15, 18)
        val LIEUX_TIERS = intArrayOf(5, 15, 30, 50, 100)

        // --- Grille ~5 km ---
        /** Degrés de latitude pour 5 km (1° ≈ 111,32 km). */
        const val GRID_DELTA_LAT = 5.0 / 111.32

        /** Borne basse du cosinus de latitude (anti division par ~0 aux pôles). */
        private const val MIN_COS_LAT = 1e-6
    }
}
