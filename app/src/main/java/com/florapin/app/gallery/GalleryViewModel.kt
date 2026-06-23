package com.florapin.app.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.FlowerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Ordre de tri de la galerie (NODE-120). */
enum class GallerySort(val label: String) {
    DATE_DESC("Plus récentes"),
    DATE_ASC("Plus anciennes"),
    SPECIES("Espèce"),
}

/**
 * Expose la liste des fleurs persistées pour la galerie, filtrée par la
 * recherche courante et triée selon [sort] (NODE-120). Le filtrage/tri est fait
 * en mémoire — suffisant tant que le volume reste modeste (pagination : NODE-121).
 *
 * [AndroidViewModel] pour obtenir le contexte applicatif (la factory par défaut
 * suffit, pas besoin d'injection).
 */
class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FlowerRepository.from(application)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(GallerySort.DATE_DESC)
    val sort: StateFlow<GallerySort> = _sort.asStateFlow()

    val flowers: StateFlow<List<FlowerEntity>> =
        combine(repository.flowers, _query, _sort) { flowers, query, sort ->
            flowers.filterByQuery(query).sortedByOrder(sort)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setSort(sort: GallerySort) {
        _sort.value = sort
    }
}

/**
 * Filtre les fleurs dont l'espèce, les notes ou une étiquette contiennent
 * [query] (insensible à la casse). Requête vide ⇒ liste inchangée.
 */
internal fun List<FlowerEntity>.filterByQuery(query: String): List<FlowerEntity> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return this
    return filter { flower ->
        flower.species?.lowercase()?.contains(needle) == true ||
            flower.notes.lowercase().contains(needle) ||
            flower.tags.any { it.lowercase().contains(needle) }
    }
}

/** Trie les fleurs selon [sort] ; les espèces vides passent en dernier. */
internal fun List<FlowerEntity>.sortedByOrder(sort: GallerySort): List<FlowerEntity> =
    when (sort) {
        GallerySort.DATE_DESC -> sortedByDescending { it.createdAt }
        GallerySort.DATE_ASC -> sortedBy { it.createdAt }
        GallerySort.SPECIES -> sortedWith(
            compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { flower ->
                flower.species?.takeIf { it.isNotBlank() }
            },
        )
    }
