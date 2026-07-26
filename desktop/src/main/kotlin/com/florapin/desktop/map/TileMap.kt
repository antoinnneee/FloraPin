package com.florapin.desktop.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.florapin.desktop.core.DesktopConfig
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/** Styles MapTiler proposés, alignés sur ceux de l'app Android. */
enum class DesktopMapStyle(val id: String, val label: String, val extension: String) {
    BRIGHT("bright-v2", "Clair", "png"),
    SATELLITE("satellite", "Satellite", "jpg"),
    HYBRID("hybrid", "Hybride", "jpg"),
    WINTER("winter-v2", "Hiver", "png"),
    ;

    companion object {
        fun fromId(id: String?): DesktopMapStyle = entries.find { it.id == id } ?: BRIGHT
    }
}

/** Une photo géolocalisée à placer sur la carte. */
data class MapMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    /** Miniature affichée dans la pastille, si elle est déjà en cache. */
    val thumbnailUrl: String?,
    /** Distingue visuellement mes photos de celles que l'on m'a partagées. */
    val mine: Boolean,
)

/** Caméra observable : conservée entre deux affichages de l'écran Carte. */
class MapCamera(latitude: Double = 46.6, longitude: Double = 2.4, zoom: Float = 5f) {
    var latitude by mutableStateOf(latitude)
    var longitude by mutableStateOf(longitude)
    var zoom by mutableStateOf(zoom)

    /** Vrai tant que l'utilisateur n'a pas déplacé la carte lui-même. */
    var autoFramed by mutableStateOf(false)

    fun moveTo(latitude: Double, longitude: Double, zoom: Float = this.zoom) {
        this.latitude = latitude.coerceIn(-MapMath.MAX_LATITUDE, MapMath.MAX_LATITUDE)
        this.longitude = longitude
        this.zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 18f
    }
}

/** Groupe de marqueurs trop proches à l'écran pour être distingués. */
private data class Cluster(
    val ids: List<String>,
    val center: Offset,
    val representative: MapMarker,
)

/**
 * Géométrie des pastilles, recalculée à chaque passe de dessin et relue par le
 * hit-testing au clic. Délibérément hors état Compose : y écrire pendant le
 * dessin invaliderait la composition à chaque image.
 */
private class ClusterHitBox {
    var value: List<Cluster> = emptyList()
}

/**
 * Carte à tuiles raster rendue directement en Compose.
 *
 * L'app Android s'appuie sur MapLibre, dont le SDK est spécifique à Android.
 * Plutôt que d'embarquer un moteur natif sur Windows, le compagnon dessine
 * lui-même les tuiles servies par MapTiler : c'est suffisant pour une carte de
 * consultation, cela évite toute dépendance native — donc tout risque à
 * l'installation — et cela laisse la main sur des interactions réellement
 * faites pour la souris (glisser pour déplacer, molette pour zoomer là où
 * pointe le curseur, clic droit contextuel).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TileMapView(
    markers: List<MapMarker>,
    style: DesktopMapStyle,
    camera: MapCamera,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
    onContextMenu: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val tiles = remember { TileCache() }
    val textMeasurer = rememberTextMeasurer()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // Recalculés à chaque rendu, relus par le hit-testing : la géométrie
    // affichée et celle testée au clic ne peuvent donc pas diverger.
    val clusters = remember { ClusterHitBox() }
    var lastClickAt by remember { mutableStateOf(0L) }
    var lastClickId by remember { mutableStateOf<String?>(null) }

    // Premier affichage : cadrer sur l'ensemble des photos plutôt que sur une
    // vue par défaut, qui obligerait à chercher ses propres souvenirs.
    if (!camera.autoFramed && markers.isNotEmpty() && canvasSize.width > 0) {
        camera.autoFramed = true
        val lats = markers.map { it.latitude }
        val lons = markers.map { it.longitude }
        camera.moveTo(
            latitude = (lats.min() + lats.max()) / 2,
            longitude = (lons.min() + lons.max()) / 2,
            zoom = if (markers.size == 1) {
                13f
            } else {
                MapMath.zoomToFit(
                    lats.min(), lats.max(), lons.min(), lons.max(),
                    canvasSize.width * 0.85, canvasSize.height * 0.85,
                ).toFloat()
            },
        )
    }

    Box(
        modifier
            // Un Canvas ne borne pas son dessin : les tuiles qui dépassent la
            // fenêtre déborderaient sur le rail de navigation voisin.
            .clipToBounds()
            .background(Color(0xFFE8EDE9))
            .onSizeChanged { canvasSize = it }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                val delta = change.scrollDelta.y
                if (delta == 0f) return@onPointerEvent
                // La molette zoome autour du curseur : le point survolé reste
                // sous le curseur, comme dans toutes les cartes du web.
                zoomAround(camera, change.position, canvasSize, -delta * ZOOM_PER_NOTCH)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitPointerEventScopeDown()
                    var dragging = false
                    var previous = down.first
                    val secondary = down.second
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            val delta = change.position - previous
                            if (!dragging && delta.getDistance() > DRAG_SLOP) dragging = true
                            if (dragging && !secondary) {
                                panBy(camera, delta, canvasSize)
                                change.consume()
                            }
                            previous = change.position
                        } else {
                            if (!dragging) {
                                val hit = clusters.value.firstOrNull {
                                    (it.center - change.position).getDistance() <= MARKER_RADIUS
                                }
                                if (secondary) {
                                    hit?.let { onContextMenu(it.representative.id) }
                                } else if (hit != null) {
                                    val now = System.currentTimeMillis()
                                    val isDouble = hit.representative.id == lastClickId &&
                                        now - lastClickAt < DOUBLE_CLICK_MS
                                    lastClickAt = now
                                    lastClickId = hit.representative.id
                                    when {
                                        hit.ids.size > 1 ->
                                            // Un amas se dénoue en zoomant dessus.
                                            zoomTo(camera, hit.center, canvasSize, 2f)
                                        isDouble -> onOpen(hit.representative.id)
                                        else -> onSelect(hit.representative.id)
                                    }
                                }
                            }
                            change.consume()
                            break
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (DesktopConfig.maptilerApiKey.isBlank()) return@Canvas
            val zoomInt = floor(camera.zoom).toInt().coerceIn(0, 18)
            val scale = 2.0.pow((camera.zoom - zoomInt).toDouble()).toFloat()
            val centerX = MapMath.lonToWorldX(camera.longitude, zoomInt)
            val centerY = MapMath.latToWorldY(camera.latitude, zoomInt)

            drawTiles(zoomInt, scale, centerX, centerY, style, tiles, scope)

            val computed = clusterMarkers(markers, zoomInt, scale, centerX, centerY, size.width, size.height)
            clusters.value = computed
            computed.forEach { cluster ->
                drawMarker(
                    cluster = cluster,
                    selected = cluster.ids.size == 1 && cluster.ids.first() == selectedId,
                    thumbnail = cluster.representative.thumbnailUrl
                        ?.let { tiles.image(it, scope) },
                    textMeasurer = textMeasurer,
                )
            }
        }
    }
}

/** Tuiles couvrant la fenêtre, dessinées du coin supérieur gauche. */
private fun DrawScope.drawTiles(
    zoomInt: Int,
    scale: Float,
    centerX: Double,
    centerY: Double,
    style: DesktopMapStyle,
    tiles: TileCache,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val tilePx = (TILE_SIZE * scale).toFloat()
    val tileCount = 1 shl zoomInt
    // Coordonnée monde du coin haut-gauche de la fenêtre.
    val originX = centerX - size.width / 2.0 / scale
    val originY = centerY - size.height / 2.0 / scale
    val firstTileX = floor(originX / TILE_SIZE).toInt()
    val firstTileY = floor(originY / TILE_SIZE).toInt()
    val columns = (size.width / tilePx).toInt() + 2
    val rows = (size.height / tilePx).toInt() + 2

    for (row in 0..rows) {
        for (column in 0..columns) {
            val tileX = firstTileX + column
            val tileY = firstTileY + row
            // La carte s'enroule horizontalement mais pas verticalement.
            if (tileY < 0 || tileY >= tileCount) continue
            val wrappedX = ((tileX % tileCount) + tileCount) % tileCount
            val left = ((tileX * TILE_SIZE - originX) * scale).toFloat()
            val top = ((tileY * TILE_SIZE - originY) * scale).toFloat()
            val bitmap = tiles.tile(zoomInt, wrappedX, tileY, style, scope)
            if (bitmap != null) {
                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    // +1 px : sans cela, les arrondis laissent des lignes
                    // claires entre les tuiles à certains niveaux de zoom.
                    dstSize = IntSize(tilePx.roundToInt() + 1, tilePx.roundToInt() + 1),
                )
            }
        }
    }
}

/**
 * Regroupe les marqueurs par cellule de grille écran : au-delà d'une certaine
 * densité, des pastilles superposées sont illisibles et impossibles à viser.
 */
private fun clusterMarkers(
    markers: List<MapMarker>,
    zoomInt: Int,
    scale: Float,
    centerX: Double,
    centerY: Double,
    width: Float,
    height: Float,
): List<Cluster> {
    val cell = CLUSTER_CELL
    val buckets = LinkedHashMap<Long, MutableList<Pair<MapMarker, Offset>>>()
    val visible = Rect(-cell, -cell, width + cell, height + cell)

    markers.forEach { marker ->
        val x = ((MapMath.lonToWorldX(marker.longitude, zoomInt) - centerX) * scale + width / 2)
        val y = ((MapMath.latToWorldY(marker.latitude, zoomInt) - centerY) * scale + height / 2)
        val point = Offset(x.toFloat(), y.toFloat())
        if (!visible.contains(point)) return@forEach
        val key = (floor(point.x / cell).toLong() shl 32) xor floor(point.y / cell).toLong()
        buckets.getOrPut(key) { mutableListOf() } += marker to point
    }

    return buckets.values.map { entries ->
        val center = Offset(
            entries.map { it.second.x }.average().toFloat(),
            entries.map { it.second.y }.average().toFloat(),
        )
        Cluster(
            ids = entries.map { it.first.id },
            center = center,
            // Représentant : une photo à moi de préférence, pour que l'aperçu
            // corresponde à ce que l'utilisateur cherche le plus souvent.
            representative = entries.firstOrNull { it.first.mine }?.first ?: entries.first().first,
        )
    }
}

private fun DrawScope.drawMarker(
    cluster: Cluster,
    selected: Boolean,
    thumbnail: ImageBitmap?,
    textMeasurer: TextMeasurer,
) {
    val accent = if (cluster.representative.mine) MARKER_MINE else MARKER_SHARED
    val radius = if (selected) MARKER_RADIUS * 1.15f else MARKER_RADIUS

    drawCircle(Color(0x33000000), radius + 3f, cluster.center)
    if (thumbnail != null) {
        val diameter = (radius - 3f) * 2
        val path = Path().apply {
            addOval(
                Rect(
                    cluster.center.x - radius + 3f,
                    cluster.center.y - radius + 3f,
                    cluster.center.x + radius - 3f,
                    cluster.center.y + radius - 3f,
                ),
            )
        }
        clipPath(path) {
            // Recadrage centré « au plus court » : la vignette remplit le
            // disque sans être déformée.
            val side = minOf(thumbnail.width, thumbnail.height)
            drawImage(
                image = thumbnail,
                srcOffset = IntOffset(
                    (thumbnail.width - side) / 2,
                    (thumbnail.height - side) / 2,
                ),
                srcSize = IntSize(side, side),
                dstOffset = IntOffset(
                    (cluster.center.x - radius + 3f).roundToInt(),
                    (cluster.center.y - radius + 3f).roundToInt(),
                ),
                dstSize = IntSize(diameter.roundToInt(), diameter.roundToInt()),
            )
        }
    } else {
        drawCircle(accent.copy(alpha = 0.85f), radius - 3f, cluster.center)
    }

    drawCircle(
        color = if (selected) Color.White else accent,
        radius = radius,
        center = cluster.center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (selected) 4f else 3f),
    )

    if (cluster.ids.size > 1) {
        val label = if (cluster.ids.size > 99) "99+" else cluster.ids.size.toString()
        val badgeCenter = Offset(cluster.center.x + radius - 4f, cluster.center.y - radius + 4f)
        drawCircle(accent, 11f, badgeCenter)
        val measured = textMeasurer.measure(
            label,
            TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold),
        )
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                badgeCenter.x - measured.size.width / 2f,
                badgeCenter.y - measured.size.height / 2f,
            ),
        )
    }
}

// ── Manipulation de la caméra ─────────────────────────────────────────────

private fun panBy(camera: MapCamera, delta: Offset, size: IntSize) {
    if (size.width == 0) return
    val zoomInt = floor(camera.zoom).toInt().coerceIn(0, 18)
    val scale = 2.0.pow((camera.zoom - zoomInt).toDouble())
    val x = MapMath.lonToWorldX(camera.longitude, zoomInt) - delta.x / scale
    val y = MapMath.latToWorldY(camera.latitude, zoomInt) - delta.y / scale
    camera.moveTo(
        latitude = MapMath.worldYToLat(y, zoomInt),
        longitude = MapMath.worldXToLon(x, zoomInt),
    )
}

/** Zoome de [step] niveaux en gardant fixe le point sous le curseur. */
private fun zoomAround(camera: MapCamera, pointer: Offset, size: IntSize, step: Float) {
    if (size.width == 0) return
    val target = (camera.zoom + step).coerceIn(MapCamera.MIN_ZOOM, MapCamera.MAX_ZOOM)
    if (target == camera.zoom) return

    val zoomInt = floor(camera.zoom).toInt().coerceIn(0, 18)
    val scale = 2.0.pow((camera.zoom - zoomInt).toDouble())
    // Coordonnées géographiques du point survolé, avant zoom.
    val worldX = MapMath.lonToWorldX(camera.longitude, zoomInt) +
        (pointer.x - size.width / 2.0) / scale
    val worldY = MapMath.latToWorldY(camera.latitude, zoomInt) +
        (pointer.y - size.height / 2.0) / scale
    val anchorLon = MapMath.worldXToLon(worldX, zoomInt)
    val anchorLat = MapMath.worldYToLat(worldY, zoomInt)

    // Après zoom, on replace le centre pour que ce point retombe sous le curseur.
    val newZoomInt = floor(target).toInt().coerceIn(0, 18)
    val newScale = 2.0.pow((target - newZoomInt).toDouble())
    val newCenterX = MapMath.lonToWorldX(anchorLon, newZoomInt) -
        (pointer.x - size.width / 2.0) / newScale
    val newCenterY = MapMath.latToWorldY(anchorLat, newZoomInt) -
        (pointer.y - size.height / 2.0) / newScale
    camera.moveTo(
        latitude = MapMath.worldYToLat(newCenterY, newZoomInt),
        longitude = MapMath.worldXToLon(newCenterX, newZoomInt),
        zoom = target,
    )
}

private fun zoomTo(camera: MapCamera, point: Offset, size: IntSize, step: Float) =
    zoomAround(camera, point, size, step)

/**
 * Premier appui d'un geste : renvoie la position initiale et si le bouton
 * secondaire est enfoncé (menu contextuel).
 */
@OptIn(ExperimentalComposeUiApi::class)
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope
.awaitPointerEventScopeDown(): Pair<Offset, Boolean> {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull()
        if (change != null && change.pressed) {
            return change.position to event.buttons.isSecondaryPressed
        }
    }
}

private const val DRAG_SLOP = 4f
private const val MARKER_RADIUS = 22f
private const val CLUSTER_CELL = 54f
private const val ZOOM_PER_NOTCH = 0.6f
private const val DOUBLE_CLICK_MS = 400L
private val MARKER_MINE = Color(0xFF386A53)
private val MARKER_SHARED = Color(0xFF7D5260)
