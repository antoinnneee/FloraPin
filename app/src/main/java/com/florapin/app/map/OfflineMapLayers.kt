package com.florapin.app.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.fillOutlineColor
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/** Affiche les emprises conservées dans la base hors ligne MapLibre. */
fun Style.updateOfflineRegionLayers(regions: List<OfflineMapRegionUi>) {
    val features = regions.map { region ->
        val bounds = region.bounds
        Feature.fromGeometry(
            Polygon.fromLngLats(
                listOf(
                    listOf(
                        Point.fromLngLat(bounds.longitudeWest, bounds.latitudeSouth),
                        Point.fromLngLat(bounds.longitudeEast, bounds.latitudeSouth),
                        Point.fromLngLat(bounds.longitudeEast, bounds.latitudeNorth),
                        Point.fromLngLat(bounds.longitudeWest, bounds.latitudeNorth),
                        Point.fromLngLat(bounds.longitudeWest, bounds.latitudeSouth),
                    ),
                ),
            ),
        )
    }
    val collection = FeatureCollection.fromFeatures(features)
    val existingSource = getSourceAs<GeoJsonSource>(OFFLINE_REGION_SOURCE)
    if (existingSource != null) {
        existingSource.setGeoJson(collection)
        return
    }

    addSource(GeoJsonSource(OFFLINE_REGION_SOURCE, collection))
    addLayerBelow(
        FillLayer(OFFLINE_REGION_FILL, OFFLINE_REGION_SOURCE).withProperties(
            fillColor("#2F8A63"),
            fillOpacity(0.16f),
            fillOutlineColor("#1F6E4D"),
        ),
        MapLayers.UNCLUSTERED,
    )
}

private const val OFFLINE_REGION_SOURCE = "offline-region-source"
private const val OFFLINE_REGION_FILL = "offline-region-fill"
