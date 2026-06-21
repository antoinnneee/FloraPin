package com.florapin.app.map

import android.graphics.Color
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
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
    const val CLUSTER_COUNT = "flowers-cluster-count"
    const val UNCLUSTERED = "flowers-unclustered"

    /** Propriété de feature portant l'id de la fleur. */
    const val PROP_ID = "id"
}

/**
 * Ajoute à [style] une source GeoJSON clusterisée et les couches associées :
 * cercles de cluster (taille/couleur selon le nombre), compteur, et points
 * individuels. Idempotent : ne fait rien si la source existe déjà.
 */
fun Style.setupFlowerClustering() {
    if (getSource(MapLayers.SOURCE) != null) return

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

    // Cercles de cluster, dimensionnés/colorés par paliers de point_count.
    val pointCount = Expression.toNumber(Expression.get("point_count"))
    addLayer(
        CircleLayer(MapLayers.CLUSTERS, MapLayers.SOURCE).withProperties(
            circleColor(
                Expression.step(
                    pointCount,
                    Expression.color(Color.parseColor("#386A53")),
                    Expression.literal(10), Expression.color(Color.parseColor("#E0A458")),
                    Expression.literal(50), Expression.color(Color.parseColor("#C0392B")),
                ),
            ),
            circleRadius(
                Expression.step(
                    pointCount,
                    Expression.literal(18f),
                    Expression.literal(10), Expression.literal(24f),
                    Expression.literal(50), Expression.literal(30f),
                ),
            ),
            circleStrokeWidth(2f),
            circleStrokeColor(Color.WHITE),
        ).withFilter(Expression.has("point_count")),
    )

    // Nombre d'éléments au centre du cluster.
    addLayer(
        SymbolLayer(MapLayers.CLUSTER_COUNT, MapLayers.SOURCE).withProperties(
            textField(Expression.toString(Expression.get("point_count_abbreviated"))),
            textSize(12f),
            textColor(Color.WHITE),
            textIgnorePlacement(true),
            textAllowOverlap(true),
        ).withFilter(Expression.has("point_count")),
    )

    // Fleurs individuelles (hors cluster).
    addLayer(
        CircleLayer(MapLayers.UNCLUSTERED, MapLayers.SOURCE).withProperties(
            circleColor(Expression.color(Color.parseColor("#7D5260"))),
            circleRadius(8f),
            circleStrokeWidth(2f),
            circleStrokeColor(Color.WHITE),
        ).withFilter(Expression.not(Expression.has("point_count"))),
    )
}

/** Remplace les features de la source par les marqueurs fournis. */
fun Style.updateFlowerMarkers(markers: List<FlowerMarker>) {
    val source = getSourceAs<GeoJsonSource>(MapLayers.SOURCE) ?: return
    val features = markers.map { marker ->
        Feature.fromGeometry(
            Point.fromLngLat(marker.longitude, marker.latitude),
        ).apply { addNumberProperty(MapLayers.PROP_ID, marker.id) }
    }
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}
