package com.florapin.app.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.FlowerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Alimente le [androidx.compose.foundation.pager.HorizontalPager] du détail
 * (TÂCHE 6.10) : la liste ordonnée des identifiants de fleurs à faire défiler.
 *
 * On réutilise la même source que la galerie ([FlowerRepository.flowers], déjà
 * triée « plus récentes d'abord » et purgée des suppressions), afin que le swipe
 * suive l'ordre par défaut de la galerie. Seuls les ids transitent : chaque page
 * observe ensuite sa propre fleur via [DetailViewModel], device-first.
 */
class DetailPagerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FlowerRepository.from(application)

    /** Identifiants locaux des fleurs, dans l'ordre d'affichage de la galerie. */
    val orderedIds: StateFlow<List<Long>> = repository.flowers
        .map { flowers -> flowers.map { it.id } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}
