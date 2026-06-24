package com.florapin.app.identify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Section « Demander une identification à mes amis » (NODE-134), affichée sur le
 * détail d'une fleur non identifiée. Le bouton est actif uniquement si la fleur
 * est synchronisée (UUID serveur disponible).
 */
@Composable
fun IdentificationRequestSection(
    flowerServerId: String?,
    viewModel: IdentificationRequestViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (state) {
            is IdentificationRequestState.Sent -> Text(
                text = "✅ Demande envoyée à vos amis.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            else -> {
                Button(
                    onClick = { flowerServerId?.let(viewModel::request) },
                    enabled = flowerServerId != null &&
                        state !is IdentificationRequestState.Sending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (state is IdentificationRequestState.Sending) {
                            "Envoi…"
                        } else {
                            "🔎 Demander une identification à mes amis"
                        },
                    )
                }
                if (flowerServerId == null) {
                    Text(
                        text = "Synchronisez la fleur pour solliciter vos amis.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                (state as? IdentificationRequestState.Error)?.let {
                    Text(
                        text = it.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
