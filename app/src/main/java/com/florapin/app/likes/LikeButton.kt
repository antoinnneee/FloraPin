package com.florapin.app.likes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.florapin.app.network.dto.Reactions

/**
 * Bouton de réaction (NODE-140 / TÂCHE 3.5). Un tap sur l'emoji bascule la
 * réaction par défaut (❤️) ; un appui long ouvre le sélecteur des 7 réactions
 * enrichies (😍 🌸 🌹 🌼 🪻 🔍 👍). Le libellé résume les types présents
 * ([reactionCounts]) suivis du total ; un tap dessus ouvre la liste des likers
 * via [onCountClick] (sinon bascule aussi la réaction). Mise à jour optimiste
 * gérée côté ViewModel.
 *
 * @param myReaction code de la réaction du spectateur, ou null s'il n'a pas réagi.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LikeButton(
    myReaction: String?,
    count: Int,
    onToggle: () -> Unit,
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier,
    reactionCounts: Map<String, Int> = emptyMap(),
    onCountClick: (() -> Unit)? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    // Emojis des types présents, du plus fréquent au moins fréquent (max 3), en
    // aperçu à côté du total : rendu visible des « compteurs par type ».
    val summary = reactionCounts.entries
        .sortedByDescending { it.value }
        .take(3)
        .joinToString(" ") { Reactions.emoji(it.key) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Box {
                Text(
                    text = if (myReaction != null) Reactions.emoji(myReaction) else "🤍",
                    modifier = Modifier.combinedClickable(
                        onClick = onToggle,
                        onLongClick = { showPicker = true },
                    ),
                )
                ReactionPicker(
                    expanded = showPicker,
                    onDismiss = { showPicker = false },
                    onPick = { code ->
                        showPicker = false
                        onReact(code)
                    },
                )
            }
            Text(
                text = if (summary.isBlank()) count.toString() else "$summary $count",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(onClick = onCountClick ?: onToggle),
            )
        }
    }
}

/** Sélecteur horizontal des 7 réactions enrichies (ouvert à l'appui long). */
@Composable
private fun ReactionPicker(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Reactions.PICKER.forEach { (code, emoji) ->
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .clickable { onPick(code) }
                        .padding(6.dp),
                )
            }
        }
    }
}
