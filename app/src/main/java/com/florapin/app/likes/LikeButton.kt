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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.florapin.app.network.dto.Reactions
import com.florapin.app.util.Haptics

/**
 * Bouton de réaction (NODE-140 / TÂCHE 3.5). Un tap sur l'emoji bascule la
 * réaction par défaut (❤️) ; un appui long ouvre le sélecteur des 7 réactions
 * enrichies (😍 🌸 🌹 🌼 🪻 🔍 👍). Le libellé affiche uniquement le total :
 * la réaction courante est déjà portée par l'emoji interactif, qu'il ne faut pas
 * dupliquer. Un tap sur le total ouvre la liste des likers via [onCountClick]
 * (sinon bascule aussi la réaction). Mise à jour optimiste gérée côté ViewModel.
 *
 * @param myReaction code de la réaction du spectateur, ou null s'il n'a pas réagi.
 * @param showCount affiche le libellé (aperçu des types + total). À false, seul
 *   l'emoji subsiste : le bouton se réduit à une pastille, posée sur la photo
 *   dans le flux. [onCountClick] est alors sans effet, faute de libellé à taper.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LikeButton(
    myReaction: String?,
    count: Int,
    onToggle: () -> Unit,
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCountClick: (() -> Unit)? = null,
    showCount: Boolean = true,
) {
    var showPicker by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    // Confirmations haptiques légères au like/réaction (QOL 6.15).
    val toggle = {
        Haptics.tap(haptic)
        onToggle()
    }
    val react = { code: String ->
        Haptics.tap(haptic)
        onReact(code)
    }
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
                        onClick = toggle,
                        onLongClick = { showPicker = true },
                    ),
                )
                ReactionPicker(
                    expanded = showPicker,
                    onDismiss = { showPicker = false },
                    onPick = { code ->
                        showPicker = false
                        react(code)
                    },
                )
            }
            if (showCount) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(onClick = onCountClick ?: toggle),
                )
            }
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
