package com.florapin.app.map

import android.app.Application
import android.location.Geocoder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.florapin.app.ui.components.BloomDownloadIndicator
import com.florapin.app.ui.components.rememberSingleLineKeyboardActions
import com.florapin.app.ui.components.singleLineKeyboardOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

data class OfflineMapSelection(
    val bounds: LatLngBounds,
    val currentZoom: Double,
)

enum class OfflineMapDetail(val label: String, val maximumZoom: Double) {
    STANDARD("Standard", 16.0),
    DETAILED("Détaillée", 18.0),
}

data class OfflineMapRegionUi(
    val id: Long,
    val name: String,
    val styleId: String,
    val progress: Float,
    val completedBytes: Long,
    val isComplete: Boolean,
    val isActive: Boolean,
    val createdAt: Long,
    val bounds: LatLngBounds,
    val minimumZoom: Double,
    val maximumZoom: Double,
    val pixelRatio: Float,
)

/** Gère les régions persistantes du cache hors ligne MapLibre. */
class OfflineMapViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = run {
        MapLibre.getInstance(application)
        OfflineManager.getInstance(application)
    }
    private val nativeRegions = mutableMapOf<Long, OfflineRegion>()
    private val regionMetadata = mutableMapOf<Long, RegionMetadata>()
    private val regionItems = mutableMapOf<Long, OfflineMapRegionUi>()

    private val _regions = MutableStateFlow<List<OfflineMapRegionUi>>(emptyList())
    val regions: StateFlow<List<OfflineMapRegionUi>> = _regions.asStateFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _suggestedName = MutableStateFlow<String?>(null)
    val suggestedName: StateFlow<String?> = _suggestedName.asStateFlow()
    private val cleanedExpansionIds = mutableSetOf<Long>()

    init {
        manager.setOfflineMapboxTileCountLimit(OFFLINE_TOTAL_TILE_LIMIT)
        refresh()
    }

    fun refresh() {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                nativeRegions.values.forEach { it.setObserver(null) }
                nativeRegions.clear()
                regionMetadata.clear()
                regionItems.clear()
                cleanedExpansionIds.clear()
                publish()
                offlineRegions.orEmpty().forEach(::observe)
            }

            override fun onError(error: String) {
                _error.value = error
            }
        })
    }

    fun download(
        name: String,
        style: MapStyle,
        styleUrl: String,
        selection: OfflineMapSelection,
        detail: OfflineMapDetail,
        pixelRatio: Float,
    ) {
        if (_isCreating.value) return
        val requestedMinimumZoom = floor(selection.currentZoom)
        val requestedMaximumZoom = max(detail.maximumZoom, ceil(selection.currentZoom))
        val overlapping = findOverlappingRegionCluster(
            selection = selection.bounds,
            styleId = style.id,
            regions = regionItems.values,
        )
        val coveringRegion = overlapping.firstOrNull {
            it.covers(
                bounds = selection.bounds,
                minimumZoom = requestedMinimumZoom,
                maximumZoom = requestedMaximumZoom,
            )
        }

        if (coveringRegion != null) {
            _error.value = null
            val native = nativeRegions[coveringRegion.id]
            if (!coveringRegion.isComplete) {
                native?.setDownloadState(OfflineRegion.STATE_ACTIVE)
                _notice.value = if (coveringRegion.isActive) {
                    "Cette zone est déjà en cours de téléchargement."
                } else {
                    "Le téléchargement de cette zone a repris."
                }
            } else {
                _notice.value = "Cette zone est déjà disponible hors ligne."
            }
            return
        }

        val mergedBounds = overlapping.fold(selection.bounds) { bounds, region ->
            bounds.union(region.bounds)
        }
        val minimumZoom = min(
            requestedMinimumZoom,
            overlapping.minOfOrNull { it.minimumZoom } ?: requestedMinimumZoom,
        )
        val maximumZoom = max(
            requestedMaximumZoom,
            overlapping.maxOfOrNull { it.maximumZoom } ?: requestedMaximumZoom,
        )
        val mergedPixelRatio = max(
            pixelRatio.coerceIn(1f, 2f),
            overlapping.maxOfOrNull { it.pixelRatio } ?: 1f,
        )
        val tileCount = estimateTileCount(mergedBounds, minimumZoom, maximumZoom)
        if (selection.currentZoom < OFFLINE_MINIMUM_SELECTION_ZOOM ||
            tileCount > OFFLINE_REGION_TILE_LIMIT
        ) {
            _error.value = if (overlapping.isEmpty()) {
                "La zone est trop large. Zoomez davantage sur la carte."
            } else {
                "L’agrandissement serait trop large. Zoomez davantage sur la carte."
            }
            _notice.value = null
            return
        }

        _error.value = null
        _notice.value = null
        _isCreating.value = true
        val existingName = overlapping.minByOrNull { it.createdAt }?.name
        val regionName = existingName ?: name.trim().ifBlank { "Zone hors ligne" }
        val metadata = RegionMetadata(
            name = regionName,
            styleId = style.id,
            createdAt = System.currentTimeMillis(),
            replacesIds = overlapping.map { it.id },
        )
        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl,
            mergedBounds,
            minimumZoom,
            maximumZoom,
            mergedPixelRatio,
            false,
        )
        manager.createOfflineRegion(
            definition,
            metadata.toBytes(),
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    _isCreating.value = false
                    observe(offlineRegion, metadata)
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    if (metadata.replacesIds.isNotEmpty()) {
                        _notice.value =
                            "Agrandissement de « ${metadata.name} » : seules les nouvelles " +
                            "tuiles sont téléchargées."
                    }
                }

                override fun onError(error: String) {
                    _isCreating.value = false
                    _error.value = error
                    _notice.value = null
                }
            },
        )
    }

    fun toggle(regionId: Long) {
        val native = nativeRegions[regionId] ?: return
        val item = regionItems[regionId] ?: return
        native.setDownloadState(
            if (item.isActive) OfflineRegion.STATE_INACTIVE else OfflineRegion.STATE_ACTIVE,
        )
    }

    fun delete(regionId: Long) {
        val native = nativeRegions[regionId] ?: return
        native.setDownloadState(OfflineRegion.STATE_INACTIVE)
        native.setObserver(null)
        native.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                nativeRegions.remove(regionId)
                regionMetadata.remove(regionId)
                regionItems.remove(regionId)
                publish()
            }

            override fun onError(error: String) {
                _error.value = error
                observe(native)
            }
        })
    }

    fun clearError() {
        _error.value = null
        _notice.value = null
    }

    fun suggestName(selection: OfflineMapSelection) {
        _suggestedName.value = null
        viewModelScope.launch {
            val center = selection.bounds.center
            _suggestedName.value = withContext(Dispatchers.IO) {
                resolvePlaceName(center.latitude, center.longitude)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolvePlaceName(latitude: Double, longitude: Double): String? = runCatching {
        val address = Geocoder(getApplication(), Locale.getDefault())
            .getFromLocation(latitude, longitude, 1)
            ?.firstOrNull()
            ?: return@runCatching null
        address.locality
            ?.takeIf { it.isNotBlank() }
            ?: address.subAdminArea?.takeIf { it.isNotBlank() }
            ?: address.adminArea?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun observe(
        region: OfflineRegion,
        knownMetadata: RegionMetadata? = null,
    ) {
        val metadata = knownMetadata ?: RegionMetadata.fromBytes(region.metadata)
        nativeRegions[region.id] = region
        regionMetadata[region.id] = metadata
        // Rend la définition disponible immédiatement pour la déduplication,
        // sans attendre le callback asynchrone de statut. Un tap rapide après
        // l'ouverture du dialogue ne peut ainsi pas recréer la même zone.
        region.definition.bounds?.let { bounds ->
            val definition = region.definition
            val previous = regionItems[region.id]
            regionItems[region.id] = OfflineMapRegionUi(
                id = region.id,
                name = metadata.name,
                styleId = metadata.styleId,
                progress = previous?.progress ?: 0f,
                completedBytes = previous?.completedBytes ?: 0L,
                isComplete = previous?.isComplete ?: false,
                isActive = previous?.isActive ?: false,
                createdAt = metadata.createdAt,
                bounds = bounds,
                minimumZoom = definition.minZoom,
                maximumZoom = definition.maxZoom,
                pixelRatio = definition.pixelRatio,
            )
            publish()
        }
        region.setDeliverInactiveMessages(true)
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                update(region, status)
                if (status.isComplete && status.downloadState == OfflineRegion.STATE_ACTIVE) {
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                }
            }

            override fun onError(error: OfflineRegionError) {
                _error.value = error.toString()
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                _error.value = "Limite de stockage hors ligne atteinte ($limit tuiles)."
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            }
        })
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                status?.let { update(region, it) }
            }

            override fun onError(error: String?) {
                _error.value = error ?: "Impossible de lire la zone hors ligne."
            }
        })
    }

    private fun update(region: OfflineRegion, status: OfflineRegionStatus) {
        val metadata = regionMetadata[region.id] ?: RegionMetadata.DEFAULT
        val definition = region.definition
        val bounds = definition.bounds ?: return
        val required = status.requiredResourceCount
        val progress = when {
            status.isComplete -> 1f
            required > 0L -> (status.completedResourceCount.toFloat() / required).coerceIn(0f, 1f)
            else -> 0f
        }
        regionItems[region.id] = OfflineMapRegionUi(
            id = region.id,
            name = metadata.name,
            styleId = metadata.styleId,
            progress = progress,
            completedBytes = status.completedResourceSize,
            isComplete = status.isComplete,
            isActive = status.downloadState == OfflineRegion.STATE_ACTIVE,
            createdAt = metadata.createdAt,
            bounds = bounds,
            minimumZoom = definition.minZoom,
            maximumZoom = definition.maxZoom,
            pixelRatio = definition.pixelRatio,
        )
        publish()
        if (status.isComplete &&
            metadata.replacesIds.isNotEmpty() &&
            cleanedExpansionIds.add(region.id)
        ) {
            deleteReplacedRegions(region.id, metadata)
        }
    }

    private fun publish() {
        val supersededIds = regionItems.keys
            .flatMapTo(mutableSetOf()) { regionMetadata[it]?.replacesIds.orEmpty() }
        _regions.value = regionItems.values
            .filterNot { it.id in supersededIds }
            .sortedByDescending { it.createdAt }
    }

    /**
     * Supprime les anciennes définitions seulement après la fin de la région
     * agrandie. Les ressources encore référencées par celle-ci restent dans la
     * base MapLibre : aucune tuile commune n'est évincée.
     */
    private fun deleteReplacedRegions(
        replacementId: Long,
        replacementMetadata: RegionMetadata,
    ) {
        replacementMetadata.replacesIds
            .filter { it != replacementId }
            .forEach { replacedId ->
                val replaced = nativeRegions[replacedId] ?: return@forEach
                replaced.setDownloadState(OfflineRegion.STATE_INACTIVE)
                replaced.setObserver(null)
                replaced.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() {
                        nativeRegions.remove(replacedId)
                        regionMetadata.remove(replacedId)
                        regionItems.remove(replacedId)
                        publish()
                        _notice.value = "La zone « ${replacementMetadata.name} » a été agrandie."
                    }

                    override fun onError(error: String) {
                        _error.value = error
                        observe(replaced)
                    }
                })
            }
    }

    override fun onCleared() {
        nativeRegions.values.forEach { it.setObserver(null) }
        super.onCleared()
    }

    private data class RegionMetadata(
        val name: String,
        val styleId: String,
        val createdAt: Long,
        val replacesIds: List<Long> = emptyList(),
    ) {
        fun toBytes(): ByteArray = JSONObject()
            .put("owner", "florapin")
            .put("name", name)
            .put("style", styleId)
            .put("createdAt", createdAt)
            .put("replaces", JSONArray(replacesIds))
            .toString()
            .toByteArray(Charsets.UTF_8)

        companion object {
            val DEFAULT = RegionMetadata("Zone hors ligne", MapStyle.DEFAULT.id, 0L)

            fun fromBytes(bytes: ByteArray): RegionMetadata = runCatching {
                val json = JSONObject(bytes.toString(Charsets.UTF_8))
                RegionMetadata(
                    name = json.optString("name").ifBlank { DEFAULT.name },
                    styleId = json.optString("style").ifBlank { DEFAULT.styleId },
                    createdAt = json.optLong("createdAt", 0L),
                    replacesIds = json.optJSONArray("replaces")?.let { ids ->
                        List(ids.length()) { index -> ids.optLong(index) }
                    }.orEmpty(),
                )
            }.getOrDefault(DEFAULT)
        }
    }
}

@Composable
fun OfflineMapDialog(
    selection: OfflineMapSelection?,
    suggestedName: String?,
    regions: List<OfflineMapRegionUi>,
    isCreating: Boolean,
    error: String?,
    notice: String?,
    onDownload: (String, OfflineMapDetail) -> Unit,
    onToggle: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onShow: (OfflineMapRegionUi) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(selection) { mutableStateOf(defaultRegionName()) }
    var detail by remember { mutableStateOf(OfflineMapDetail.STANDARD) }
    LaunchedEffect(selection, suggestedName) {
        if (!suggestedName.isNullOrBlank()) name = suggestedName
    }
    val maximumZoom = selection?.let { max(detail.maximumZoom, ceil(it.currentZoom)) }
    val tileCount = if (selection != null && maximumZoom != null) {
        estimateTileCount(selection.bounds, selection.currentZoom, maximumZoom)
    } else {
        0L
    }
    val canDownload = selection != null &&
        selection.currentZoom >= OFFLINE_MINIMUM_SELECTION_ZOOM &&
        tileCount <= OFFLINE_REGION_TILE_LIMIT &&
        !isCreating

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cartes hors ligne") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Téléchargez la zone actuellement visible. Le fond de carte " +
                        "restera disponible sans connexion.",
                )
                if (selection == null) {
                    Text("La carte est encore en cours de chargement.")
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nom de la zone") },
                        singleLine = true,
                        keyboardOptions = singleLineKeyboardOptions(),
                        keyboardActions = rememberSingleLineKeyboardActions(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OfflineMapDetail.entries.forEach { option ->
                            FilterChip(
                                selected = detail == option,
                                onClick = { detail = option },
                                label = { Text(option.label) },
                            )
                        }
                    }
                    if (selection.currentZoom < OFFLINE_MINIMUM_SELECTION_ZOOM ||
                        tileCount > OFFLINE_REGION_TILE_LIMIT
                    ) {
                        Text(
                            "Zone trop large : fermez cette fenêtre et zoomez davantage.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = { onDownload(name, detail) },
                        enabled = canDownload,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isCreating) {
                            BloomDownloadIndicator(
                                modifier = Modifier.size(32.dp),
                                contentDescription = "Préparation du téléchargement",
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("Préparation…")
                        } else {
                            Text("Télécharger la zone visible")
                        }
                    }
                }

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                notice?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                HorizontalDivider()
                Text("Zones enregistrées", style = MaterialTheme.typography.titleMedium)
                if (regions.isEmpty()) {
                    Text("Aucune zone téléchargée.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    regions.forEach { region ->
                        OfflineRegionRow(region, onToggle, onDelete, onShow)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}

@Composable
private fun OfflineRegionRow(
    region: OfflineMapRegionUi,
    onToggle: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onShow: (OfflineMapRegionUi) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BloomDownloadIndicator(
                    progress = region.progress,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(region.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        formatBytes(region.completedBytes),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    if (region.isComplete) "Disponible" else "${(region.progress * 100).toInt()} %",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (region.isComplete) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            LinearProgressIndicator(
                progress = { region.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (!region.isComplete) {
                    TextButton(
                        onClick = { onToggle(region.id) },
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text(if (region.isActive) "Pause" else "Reprendre")
                    }
                }
                TextButton(
                    onClick = { onShow(region) },
                    modifier = Modifier.height(32.dp),
                ) {
                    Text("Voir")
                }
                TextButton(
                    onClick = { onDelete(region.id) },
                    modifier = Modifier.height(32.dp),
                ) {
                    Text("Supprimer")
                }
            }
        }
    }
}

/** Régions de même style reliées à la sélection, y compris par recouvrement en chaîne. */
internal fun findOverlappingRegionCluster(
    selection: LatLngBounds,
    styleId: String,
    regions: Collection<OfflineMapRegionUi>,
): List<OfflineMapRegionUi> {
    val remaining = regions.filter { it.styleId == styleId }.toMutableList()
    val overlapping = mutableListOf<OfflineMapRegionUi>()
    var mergedBounds = selection
    while (true) {
        val next = remaining.filter { mergedBounds.intersect(it.bounds) != null }
        if (next.isEmpty()) break
        overlapping += next
        remaining.removeAll(next.toSet())
        next.forEach { mergedBounds = mergedBounds.union(it.bounds) }
    }
    return overlapping
}

/** Vrai si une région contient déjà toute la demande, niveaux de zoom compris. */
internal fun OfflineMapRegionUi.covers(
    bounds: LatLngBounds,
    minimumZoom: Double,
    maximumZoom: Double,
): Boolean = this.bounds.contains(bounds) &&
    this.minimumZoom <= minimumZoom &&
    this.maximumZoom >= maximumZoom

private fun estimateTileCount(
    bounds: LatLngBounds,
    minimumZoom: Double,
    maximumZoom: Double,
): Long {
    var total = 0L
    for (zoom in floor(minimumZoom).toInt()..ceil(maximumZoom).toInt()) {
        val tiles = 2.0.pow(zoom)
        val west = tileX(bounds.longitudeWest, tiles)
        val east = tileX(bounds.longitudeEast, tiles)
        val north = tileY(bounds.latitudeNorth, tiles)
        val south = tileY(bounds.latitudeSouth, tiles)
        total += (east - west + 1).coerceAtLeast(1L) *
            (south - north + 1).coerceAtLeast(1L)
        if (total > OFFLINE_REGION_TILE_LIMIT) return total
    }
    return total
}

private fun tileX(longitude: Double, tiles: Double): Long =
    floor(((longitude + 180.0) / 360.0) * tiles)
        .toLong()
        .coerceIn(0L, tiles.toLong() - 1L)

private fun tileY(latitude: Double, tiles: Double): Long {
    val safeLatitude = latitude.coerceIn(-85.05112878, 85.05112878)
    val radians = Math.toRadians(safeLatitude)
    return floor((1.0 - ln(tan(radians) + 1.0 / cos(radians)) / PI) / 2.0 * tiles)
        .toLong()
        .coerceIn(0L, tiles.toLong() - 1L)
}

private fun defaultRegionName(): String =
    "Zone ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date())}"

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format("%.1f Go", bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> String.format("%.1f Mo", bytes / 1_000_000.0)
    bytes >= 1_000L -> String.format("%.1f Ko", bytes / 1_000.0)
    else -> "$bytes o"
}

private const val OFFLINE_MINIMUM_SELECTION_ZOOM = 11.0
private const val OFFLINE_REGION_TILE_LIMIT = 40_000L
private const val OFFLINE_TOTAL_TILE_LIMIT = 250_000L
