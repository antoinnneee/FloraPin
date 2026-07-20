package com.florapin.app.map

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.florapin.app.R
import org.maplibre.android.maps.Style
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Identifiants de la source et des couches de la carte des fleurs. */
object MapLayers {
    const val SOURCE = "flowers"
    const val CALLOUT_SOURCE = "flower-callouts"
    const val CALLOUT_LINE_SOURCE = "flower-callout-lines"
    const val CLUSTERS = "flowers-clusters"
    const val UNCLUSTERED = "flowers-unclustered"
    const val CALLOUT_LINES = "flower-callout-lines"
    const val CALLOUTS = "flower-callouts"

    /** Propriété de feature portant l'id de la fleur. */
    const val PROP_ID = "id"

    /** Id interne de tout marqueur, y compris une fleur d'ami non navigable. */
    const val PROP_MARKER_ID = "markerId"

    /** Propriété de feature portant le nom de l'image emoji à afficher. */
    const val PROP_EMOJI = "emoji"

    /** Propriété de feature portant l'URL de la photo d'une fleur d'ami. */
    const val PROP_PHOTO = "photo"

    /** Nom de la bulle photo enregistrée dans le style. */
    const val PROP_PHOTO_ICON = "photoIcon"
}

/** Badges de cluster, par palier croissant de nombre de fleurs. */
private const val CLUSTER_ICON_SMALL = "cluster-small"
private const val CLUSTER_ICON_MEDIUM = "cluster-medium"
private const val CLUSTER_ICON_LARGE = "cluster-large"

/** Côté du badge de cluster, en pixels, à `iconSize` 1. */
private const val CLUSTER_ICON_PX = 192

/** Seuils de nombre de fleurs séparant les trois badges. */
private const val CLUSTER_MEDIUM_FROM = 10
private const val CLUSTER_LARGE_FROM = 50

/** Échelle des emojis d'espèce (les fleurs isolées non encore illustrées). */
const val EMOJI_ICON_SCALE = 0.6f

/** Taille stable des appels photo : la proximité ne les rend plus minuscules. */
private const val PHOTO_CALLOUT_ICON_SCALE = 1f

/** Positions géographiques actuellement affichées, conservées entre deux layouts. */
class CalloutMotionState internal constructor() {
    internal var displayedPositions: Map<Long, LatLng> = emptyMap()
}

/**
 * Ajoute à [style] une source GeoJSON clusterisée et les couches associées :
 * cercles de cluster (taille/couleur selon le nombre), compteur, et points
 * individuels. Idempotent : ne fait rien si la source existe déjà.
 */
fun Style.setupFlowerClustering(context: Context) {
    if (getSource(MapLayers.SOURCE) != null) return

    // Pré-enregistre une image par emoji de fleur ; les features individuelles
    // référencent leur image par nom (cf. PROP_EMOJI).
    FlowerEmoji.all.forEach { emoji ->
        addImage(emoji, emojiToBitmap(emoji))
    }

    addImage(CLUSTER_ICON_SMALL, clusterBadge(context, R.drawable.map_cluster_small))
    addImage(CLUSTER_ICON_MEDIUM, clusterBadge(context, R.drawable.map_cluster_medium))
    addImage(CLUSTER_ICON_LARGE, clusterBadge(context, R.drawable.map_cluster_large))

    addSource(
        GeoJsonSource(
            MapLayers.SOURCE,
            FeatureCollection.fromFeatures(emptyList()),
            GeoJsonOptions()
                .withCluster(true)
                .withClusterMaxZoom(14)
                .withClusterRadius(50),
        ),
    )
    addSource(
        GeoJsonSource(
            MapLayers.CALLOUT_SOURCE,
            FeatureCollection.fromFeatures(emptyList()),
        ),
    )
    addSource(
        GeoJsonSource(
            MapLayers.CALLOUT_LINE_SOURCE,
            FeatureCollection.fromFeatures(emptyList()),
        ),
    )

    // Badge floral au lieu d'un simple cercle : sa couleur et sa taille croissent
    // par paliers de point_count, et le nombre s'inscrit dans son cœur plein.
    val pointCount = Expression.toNumber(Expression.get("point_count"))
    addLayer(
        SymbolLayer(MapLayers.CLUSTERS, MapLayers.SOURCE).withProperties(
            iconImage(
                Expression.step(
                    pointCount,
                    Expression.literal(CLUSTER_ICON_SMALL),
                    Expression.stop(CLUSTER_MEDIUM_FROM, CLUSTER_ICON_MEDIUM),
                    Expression.stop(CLUSTER_LARGE_FROM, CLUSTER_ICON_LARGE),
                ),
            ),
            iconSize(
                Expression.step(
                    pointCount,
                    Expression.literal(0.6f),
                    Expression.stop(CLUSTER_MEDIUM_FROM, 0.75f),
                    Expression.stop(CLUSTER_LARGE_FROM, 0.9f),
                ),
            ),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
            textField(Expression.toString(Expression.get("point_count_abbreviated"))),
            textSize(
                Expression.step(
                    pointCount,
                    Expression.literal(11f),
                    Expression.stop(CLUSTER_MEDIUM_FROM, 13f),
                    Expression.stop(CLUSTER_LARGE_FROM, 15f),
                ),
            ),
            textColor(Color.WHITE),
            textIgnorePlacement(true),
            textAllowOverlap(true),
        ).withFilter(Expression.has("point_count")),
    )

    // Les lignes sont sous les emojis et les photos. Leur géométrie est recalculée
    // après chaque mouvement de caméra par le solveur de répulsion.
    addLayer(
        LineLayer(MapLayers.CALLOUT_LINES, MapLayers.CALLOUT_LINE_SOURCE)
            .withProperties(
                lineColor(Color.rgb(48, 93, 70)),
                lineWidth(2.5f),
                lineDasharray(arrayOf(1f, 2.2f)),
            )
            .apply { minZoom = PHOTO_ICON_MIN_ZOOM },
    )

    // L'emoji reste toujours exactement sur la coordonnée GPS.
    addLayer(
        SymbolLayer(MapLayers.UNCLUSTERED, MapLayers.SOURCE).withProperties(
            iconImage(Expression.get(MapLayers.PROP_EMOJI)),
            iconSize(EMOJI_ICON_SCALE),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
        ).withFilter(Expression.not(Expression.has("point_count"))),
    )

    // Les photos vivent dans une source distincte : leur point géographique est
    // dérivé d'une position écran, donc elles peuvent se repousser librement.
    addLayer(
        SymbolLayer(MapLayers.CALLOUTS, MapLayers.CALLOUT_SOURCE)
            .withProperties(
                iconImage(Expression.get(MapLayers.PROP_PHOTO_ICON)),
                iconSize(PHOTO_CALLOUT_ICON_SCALE),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
            )
            .apply { minZoom = PHOTO_ICON_MIN_ZOOM },
    )
}

/** Charge un badge de cluster à la taille attendue par le style. */
private fun clusterBadge(context: Context, resId: Int) =
    ContextCompat.getDrawable(context, resId)!!
        .toBitmap(CLUSTER_ICON_PX, CLUSTER_ICON_PX)
        .withScreenDensity(context)

/**
 * Remplace les features de la source par les marqueurs fournis.
 *
 * @param photoIconIds ids des marqueurs dont la pastille photo est déjà
 *   enregistrée dans le style. Nommer une image absente ferait disparaître le
 *   marqueur : on ne pose la propriété que pour celles-là.
 */
fun Style.updateFlowerMarkers(markers: List<FlowerMarker>) {
    val source = getSourceAs<GeoJsonSource>(MapLayers.SOURCE) ?: return
    val features = markers.map { marker ->
        Feature.fromGeometry(
            Point.fromLngLat(marker.longitude, marker.latitude),
        ).apply {
            addNumberProperty(MapLayers.PROP_MARKER_ID, marker.id)
            // Seules mes fleurs portent un id : un tap ouvre leur détail local.
            // Une fleur d'ami n'a pas de détail local : elle porte l'URL de sa
            // photo, qu'un tap affiche en plein écran.
            if (marker.navigable) {
                addNumberProperty(MapLayers.PROP_ID, marker.id)
            } else {
                marker.photoUrl?.let { addStringProperty(MapLayers.PROP_PHOTO, it) }
            }
            addStringProperty(MapLayers.PROP_EMOJI, marker.emoji)
        }
    }
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}

/** Recalcule les bulles visibles et leurs lignes après un changement de caméra. */
fun Style.updateFlowerCallouts(
    map: MapLibreMap,
    markers: List<FlowerMarker>,
    photoIconIds: Set<Long>,
    viewportWidth: Float,
    viewportHeight: Float,
    relaxationSteps: Int = CALLOUT_RELAXATION_STEPS,
    motionState: CalloutMotionState? = null,
    interpolation: Float = 1f,
) {
    val calloutSource = getSourceAs<GeoJsonSource>(MapLayers.CALLOUT_SOURCE) ?: return
    val lineSource = getSourceAs<GeoJsonSource>(MapLayers.CALLOUT_LINE_SOURCE) ?: return
    if (map.cameraPosition.zoom < PHOTO_ICON_MIN_ZOOM) {
        calloutSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        lineSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        motionState?.displayedPositions = emptyMap()
        return
    }

    val bounds = runCatching { map.projection.visibleRegion.latLngBounds }.getOrNull()
    val visibleMarkers = markers.filter { marker ->
        marker.id in photoIconIds &&
            (bounds == null || bounds.contains(org.maplibre.android.geometry.LatLng(marker.latitude, marker.longitude)))
    }
    val anchors = visibleMarkers.map { marker ->
        val point = map.projection.toScreenLocation(
            org.maplibre.android.geometry.LatLng(marker.latitude, marker.longitude),
        )
        CalloutAnchor(marker.id, ScreenPoint(point.x, point.y))
    }
    val anchorsById = anchors.associateBy(CalloutAnchor::id)
    val targets = repelCallouts(
        anchors = anchors,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        relaxationSteps = relaxationSteps,
    )
    val placements = targets.mapValues { (id, target) ->
        val previousGeo = motionState?.displayedPositions?.get(id)
        if (previousGeo == null) {
            target
        } else {
            val previous = map.projection.toScreenLocation(previousGeo)
            interpolateScreenPoint(
                from = ScreenPoint(previous.x, previous.y),
                to = target,
                fraction = interpolation,
            )
        }
    }

    val displayedGeoPositions = placements.mapValues { (_, point) ->
        map.projection.fromScreenLocation(PointF(point.x, point.y))
    }
    motionState?.displayedPositions = displayedGeoPositions

    val callouts = visibleMarkers.mapNotNull { marker ->
        val bubblePosition = displayedGeoPositions[marker.id] ?: return@mapNotNull null
        Feature.fromGeometry(Point.fromLngLat(bubblePosition.longitude, bubblePosition.latitude)).apply {
            addMarkerTargetProperties(marker)
            addStringProperty(
                MapLayers.PROP_PHOTO_ICON,
                photoIconId(marker.id, friend = !marker.navigable),
            )
        }
    }
    val lines = visibleMarkers.mapNotNull { marker ->
        val screenPoint = placements[marker.id] ?: return@mapNotNull null
        val anchor = anchorsById[marker.id]?.point ?: return@mapNotNull null
        val obstacles = anchors.asSequence()
            .filter { it.id != marker.id }
            .map { it.point }
            .toList()
        val curvedPath = harmoniousCalloutPath(anchor, screenPoint, obstacles)
        Feature.fromGeometry(
            LineString.fromLngLats(
                curvedPath.map { point ->
                    val position = map.projection.fromScreenLocation(PointF(point.x, point.y))
                    Point.fromLngLat(position.longitude, position.latitude)
                },
            ),
        )
    }
    calloutSource.setGeoJson(FeatureCollection.fromFeatures(callouts))
    lineSource.setGeoJson(FeatureCollection.fromFeatures(lines))
}

private fun Feature.addMarkerTargetProperties(marker: FlowerMarker) {
    addNumberProperty(MapLayers.PROP_MARKER_ID, marker.id)
    if (marker.navigable) {
        addNumberProperty(MapLayers.PROP_ID, marker.id)
    } else {
        marker.photoUrl?.let { addStringProperty(MapLayers.PROP_PHOTO, it) }
    }
}
