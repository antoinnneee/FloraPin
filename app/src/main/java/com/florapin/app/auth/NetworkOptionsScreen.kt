package com.florapin.app.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.florapin.app.ui.theme.FloraPinTheme
import com.florapin.app.ui.components.swipeToContinue

/**
 * Écran « Options réseau » affiché après une connexion mail/mot de passe.
 *
 * FloraPin est device-first (100 % local par défaut) : cet écran laisse
 * l'utilisateur activer explicitement la synchronisation cloud et explique ce
 * qu'elle apporte. Le choix est un réglage par appareil ([SyncPreferences]),
 * pré-rempli sur l'état courant et modifiable ensuite dans Profil.
 *
 * @param initialEnabled état courant de la préférence de sync (pré-coche).
 * @param onContinue invoqué avec le choix final ; l'appelant persiste la
 *   préférence, amorce (ou non) la sync, puis navigue vers la galerie.
 */
@Composable
fun NetworkOptionsScreen(
    initialEnabled: Boolean,
    onContinue: (enabled: Boolean) -> Unit,
    swipeEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var enabled by rememberSaveable { mutableStateOf(initialEnabled) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .swipeToContinue(enabled = swipeEnabled) { onContinue(enabled) }
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Options réseau", style = MaterialTheme.typography.headlineMedium)

        Text(
            text = "FloraPin fonctionne à 100 % sur ton appareil. Active la " +
                "synchronisation cloud si tu souhaites en plus les fonctions en " +
                "ligne.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Synchronisation cloud",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Benefit("Sauvegarde tes fleurs sur le serveur")
                        Benefit("Retrouve-les sur tes autres appareils")
                        Benefit("Permet de partager tes fleurs avec tes amis")
                    }
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
        }

        Text(
            text = "Désactivée, l'app reste entièrement locale. Tu pourras changer " +
                "ce choix à tout moment dans Profil.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = { onContinue(enabled) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continuer")
        }
    }
}

/** Puce d'un bénéfice de la synchronisation cloud. */
@Composable
private fun Benefit(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun NetworkOptionsScreenPreview() {
    FloraPinTheme {
        NetworkOptionsScreen(initialEnabled = false, onContinue = {})
    }
}
