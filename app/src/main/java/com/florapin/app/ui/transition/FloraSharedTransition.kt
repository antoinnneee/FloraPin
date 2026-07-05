package com.florapin.app.ui.transition

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Transitions partagées galerie ↔ détail (TÂCHE 6.17).
 *
 * L'API [SharedTransitionScope] est encore expérimentale dans le BOM Compose
 * 2024.12.01 : tout est isolé ici derrière un unique point d'entrée pour pouvoir
 * la désactiver d'un seul geste ([SHARED_TRANSITIONS_ENABLED]) sans toucher aux
 * écrans. Côté appelant, on ne manipule qu'un [FloraSharedScope] nullable et le
 * modificateur [sharedFlowerImage] ; quand la fonctionnalité est coupée (ou que
 * les portées ne sont pas disponibles), tout dégrade en no-op — les écrans
 * s'affichent normalement, sans animation.
 */

/**
 * Interrupteur global des transitions partagées. Passer à `false` neutralise
 * entièrement l'effet (le modificateur devient un no-op) : utile
 * si l'API expérimentale régresse dans une future montée de BOM.
 */
const val SHARED_TRANSITIONS_ENABLED = true

/**
 * Regroupe les deux portées nécessaires à un élément partagé : la portée de
 * transition (fournie par `SharedTransitionLayout`) et la portée de visibilité de
 * la destination de navigation courante (l'`AnimatedContentScope` du `composable`).
 *
 * Transmis en paramètre nullable aux écrans galerie/détail : `null` = pas de
 * transition (aperçu isolé, tests, fonctionnalité coupée).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
class FloraSharedScope(
    val transitionScope: SharedTransitionScope,
    val visibilityScope: AnimatedVisibilityScope,
)

/** Clé stable de l'image partagée d'une fleur (identique galerie et détail). */
private fun flowerImageKey(flowerId: Long): String = "flower-image-$flowerId"

/**
 * Marque une image de fleur comme élément partagé entre la galerie et le détail.
 * La clé dérive de l'id local : seule la fleur ouverte (même id des deux côtés)
 * s'anime, les autres vignettes restent inertes.
 *
 * No-op si [scope] est nul ou si [SHARED_TRANSITIONS_ENABLED] est faux, de sorte
 * que l'appelant applique toujours le modificateur sans condition.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedFlowerImage(scope: FloraSharedScope?, flowerId: Long): Modifier {
    if (!SHARED_TRANSITIONS_ENABLED || scope == null) return this
    return with(scope.transitionScope) {
        this@sharedFlowerImage.sharedElement(
            rememberSharedContentState(key = flowerImageKey(flowerId)),
            animatedVisibilityScope = scope.visibilityScope,
        )
    }
}
