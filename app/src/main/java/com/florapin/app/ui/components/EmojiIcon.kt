package com.florapin.app.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle

/**
 * Emoji tenant lieu de pictogramme (icône d'un IconButton, d'un FloatingActionButton
 * ou d'une action de barre). TalkBack lit les emojis littéralement (« maison »,
 * « visage souriant »…), ce qui est illisible pour une commande. On force donc un
 * [contentDescription] parlant et on masque le glyphe lui-même via
 * [clearAndSetSemantics] (TÂCHE 6.18, accessibilité).
 *
 * Pour un emoji purement décoratif accompagné d'un libellé texte voisin (ex. icône
 * de tête d'un champ de recherche), utiliser plutôt [DecorativeEmoji].
 */
@Composable
fun EmojiIcon(
    emoji: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text = emoji,
        style = style,
        modifier = modifier.clearAndSetSemantics { this.contentDescription = contentDescription },
    )
}

/**
 * Emoji purement décoratif : son sens est déjà porté par un texte voisin (libellé
 * d'onglet, placeholder de champ…). On le retire donc de l'arbre d'accessibilité
 * pour éviter une lecture parasite par TalkBack.
 */
@Composable
fun DecorativeEmoji(
    emoji: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text = emoji,
        style = style,
        modifier = modifier.clearAndSetSemantics {},
    )
}
