package com.florapin.app.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florapin.app.network.dto.NotificationDto
import com.florapin.app.push.NotificationTarget
import com.florapin.app.ui.components.EmojiIcon
import com.florapin.app.ui.components.EmptyState
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Centre de notifications in-app (TÂCHE 2.7) : liste chronologique des
 * notifications reçues, avec point « non lu ». Un tap marque la notification lue
 * puis route vers le contenu concerné, en réutilisant le routage des push
 * (TÂCHE 2.2) via [onOpen] (résolution serverId → fleur locale côté NavHost).
 *
 * Device-first : hors-ligne / non connecté, l'écran affiche un état
 * « indisponible » explicite plutôt qu'une liste vide trompeuse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    onBack: () -> Unit,
    onOpen: (NotificationTarget) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationCenterViewModel = viewModel(
        factory = NotificationCenterViewModel.factory(LocalContext.current),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        EmojiIcon("←", contentDescription = "Retour")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                !state.available -> EmptyState(
                    title = "Notifications indisponibles",
                    message = "Connectez-vous et vérifiez votre connexion pour " +
                        "consulter vos notifications.",
                )

                state.items.isEmpty() -> EmptyState(
                    title = "Aucune notification",
                    message = "Vous êtes à jour ! Les nouveautés de vos amis " +
                        "apparaîtront ici.",
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { notification ->
                        NotificationRow(
                            notification = notification,
                            onClick = {
                                viewModel.markRead(notification)
                                onOpen(
                                    NotificationTarget(
                                        type = notification.type,
                                        flowerServerId = notification.flowerServerId,
                                    ),
                                )
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: NotificationDto,
    onClick: () -> Unit,
) {
    val unread = notification.readAt == null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = notification.emoji(),
            style = MaterialTheme.typography.titleLarge,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = notification.title(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = relativeTime(notification.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Point « non lu » discret, aligné à droite.
        if (unread) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(10.dp),
            ) {}
        }
    }
}

/** Emoji illustrant le type de notification. */
private fun NotificationDto.emoji(): String = when (type) {
    "friend_request" -> "🤝"
    "friend_accepted" -> "✅"
    "flower_shared" -> "🌸"
    "species_proposed" -> "🌿"
    "species_confirmed" -> "✅"
    "identification_requested" -> "🔎"
    "flower_liked" -> "❤️"
    "flower_commented" -> "💬"
    "comment_mention" -> "📣"
    else -> "🔔"
}

/**
 * Libellé humain d'une notification. Les données in-app ne portent que des
 * identifiants bruts (pas de nom d'émetteur figé, cf. 2.1) : on affiche donc des
 * formulations génériques, enrichies de l'espèce quand elle est transmise
 * (propositions / confirmations).
 */
private fun NotificationDto.title(): String = when (type) {
    "friend_request" -> "Nouvelle demande d'ami"
    "friend_accepted" -> "Votre demande d'ami a été acceptée"
    "flower_shared" -> "Une fleur a été partagée avec vous"
    "species_proposed" -> species
        ?.let { "Espèce proposée pour votre fleur : $it" }
        ?: "Une espèce a été proposée pour votre fleur"
    "species_confirmed" -> species
        ?.let { "Votre proposition « $it » a été confirmée" }
        ?: "Votre proposition d'espèce a été confirmée"
    "identification_requested" -> "Un ami a besoin d'aide pour identifier une fleur"
    "flower_liked" -> "Quelqu'un a aimé votre fleur"
    "flower_commented" -> "Nouveau commentaire sur votre fleur"
    "comment_mention" -> "Vous avez été mentionné dans un commentaire"
    else -> "Nouvelle notification"
}

/** Ancienneté lisible d'un horodatage ISO-8601 (« à l'instant », « il y a 3 h »). */
private fun relativeTime(iso: String): String {
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val minutes = ChronoUnit.MINUTES.between(instant, Instant.now())
    return when {
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a $minutes min"
        minutes < 60 * 24 -> "il y a ${minutes / 60} h"
        else -> "il y a ${minutes / (60 * 24)} j"
    }
}
