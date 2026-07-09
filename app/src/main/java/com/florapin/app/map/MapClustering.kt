package com.florapin.app.map

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.florapin.app.R
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Identifiants de la source et des couches de la carte des fleurs. */
object MapLayers {
    const val SOURCE = "flowers"
    const val CLUSTERS = "flowers-clusters"
    const val UNCLUSTERED = "flowers-unclustered"

    /** Propriété de feature portant l'id de la fleur. */
    const val PROP_ID = "id"

    /** Propriété de feature portant le nom de l'image emoji à afficher. */
    const val PROP_EMOJI = "emoji"

    /** Propriété de feature portant l'URL de la photo d'une fleur d'ami. */
    const val PROP_PHOTO = "photo"

    /**
     * Propriété de feature portant le nom de la pastille photo enregistrée dans
     * le style. Absente tant que la vignette n'a pas été chargée : l'expression
     * de la couche retombe alors sur [PROP_EMOJI].
     */
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

    // Fleurs individuelles (hors cluster) : emoji d'espèce rendu en bitmap, puis
    // pastille photo une fois zoomé de près (PHOTO_ICON_MIN_ZOOM). Le `coalesce`
    // retient l'emoji pour les fleurs dont la vignette n'a pas (encore) été
    // chargée. MapLibre n'accepte le zoom dans une propriété liée aux données
    // qu'au niveau le plus externe d'un `step` — d'où cette forme.
    addLayer(
        SymbolLayer(MapLayers.UNCLUSTERED, MapLayers.SOURCE).withProperties(
            iconImage(
                Expression.step(
                    Expression.zoom(),
                    Expression.get(MapLayers.PROP_EMOJI),
                    Expression.stop(
                        PHOTO_ICON_MIN_ZOOM,
                        Expression.coalesce(
                            Expression.get(MapLayers.PROP_PHOTO_ICON),
                            Expression.get(MapLayers.PROP_EMOJI),
                        ),
                    ),
                ),
            ),
            iconSize(photoIconSizeExpression(PHOTO_ICON_SCALE_INITIAL)),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
        ).withFilter(Expression.not(Expression.has("point_count"))),
    )
}

/** Charge un badge de cluster à la taille attendue par le style. */
private fun clusterBadge(context: Context, resId: Int) =
    ContextCompat.getDrawable(context, resId)!!
        .toBitmap(CLUSTER_ICON_PX, CLUSTER_ICON_PX)
        .withScreenDensity(context)

/**
 * Taille des icônes de fleurs isolées : les emojis gardent [EMOJI_ICON_SCALE],
 * les pastilles photo prennent [scale], qui suit le zoom.
 */
private fun photoIconSizeExpression(scale: Float): Expression = Expression.step(
    Expression.zoom(),
    Expression.literal(EMOJI_ICON_SCALE),
    Expression.stop(
        PHOTO_ICON_MIN_ZOOM,
        Expression.switchCase(
            Expression.has(MapLayers.PROP_PHOTO_ICON),
            Expression.literal(scale),
            Expression.literal(EMOJI_ICON_SCALE),
        ),
    ),
)

/**
 * Redimensionne les pastilles photo. Appelé à chaque mouvement de caméra : le
 * zoom les grossit, le voisinage les bride (cf. [photoIconScale]).
 */
fun Style.setPhotoIconScale(scale: Float) {
    val layer = getLayerAs<SymbolLayer>(MapLayers.UNCLUSTERED) ?: return
    layer.setProperties(iconSize(photoIconSizeExpression(scale)))
}

/**
 * Remplace les features de la source par les marqueurs fournis.
 *
 * @param photoIconIds ids des marqueurs dont la pastille photo est déjà
 *   enregistrée dans le style. Nommer une image absente ferait disparaître le
 *   marqueur : on ne pose la propriété que pour celles-là.
 */
fun Style.updateFlowerMarkers(
    markers: List<FlowerMarker>,
    photoIconIds: Set<Long> = emptySet(),
) {
    val source = getSourceAs<GeoJsonSource>(MapLayers.SOURCE) ?: return
    val features = markers.map { marker ->
        Feature.fromGeometry(
            Point.fromLngLat(marker.longitude, marker.latitude),
        ).apply {
            // Seules mes fleurs portent un id : un tap ouvre leur détail local.
            // Une fleur d'ami n'a pas de détail local : elle porte l'URL de sa
            // photo, qu'un tap affiche en plein écran.
            if (marker.navigable) {
                addNumberProperty(MapLayers.PROP_ID, marker.id)
            } else {
                marker.photoUrl?.let { addStringProperty(MapLayers.PROP_PHOTO, it) }
            }
            addStringProperty(MapLayers.PROP_EMOJI, marker.emoji)
            if (marker.id in photoIconIds) {
                addStringProperty(MapLayers.PROP_PHOTO_ICON, photoIconId(marker.id))
            }
        }
    }
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}
