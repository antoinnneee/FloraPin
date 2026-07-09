package com.florapin.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.florapin.app.auth.NetworkOptionsScreen
import com.florapin.app.permission.AppPermission
import com.florapin.app.permission.rememberMultiplePermissionsState
import com.florapin.app.share.SharePreferences
import com.florapin.app.share.SharingDefaultsForm
import com.florapin.app.sync.SyncPreferences
import com.florapin.app.ui.theme.FloraPinTheme

/**
 * Onboarding affiché à la première installation uniquement (cf. [OnboardingPrefs]).
 *
 *  1. Promesse sociale : capture géolocalisée → partage → identification par les
 *     amis.
 *  2. Permissions contextualisées : caméra + localisation, expliquées avant la
 *     sollicitation système (réutilise le socle [rememberMultiplePermissionsState]).
 *  3. Choix de synchronisation : délègue à [NetworkOptionsScreen] pour respecter
 *     le device-first (sync OFF par défaut, choix par appareil).
 *  4. Partage par défaut : destinataire, GPS, partage automatique — les valeurs
 *     qui prérempliront la feuille de partage (cf. [SharePreferences]).
 *
 * Le dernier écran n'a de sens qu'avec la synchronisation : partager suppose des
 * fleurs déposées sur le serveur. Refuser le cloud à l'écran 3 clôt donc
 * l'onboarding, et les réglages de partage gardent leurs valeurs par défaut.
 *
 * @param onFinish invoqué à la fin du dernier écran ; l'appelant persiste le
 *   drapeau « déjà vu » puis navigue vers Login ou la galerie.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }

    when (step) {
        0 -> OnboardingPage(
            emoji = "🌸",
            title = "Tes fleurs, entre amis",
            body = "Photographie une plante : FloraPin l'épingle à l'endroit où " +
                "tu l'as trouvée. Partage tes trouvailles avec tes amis, qui " +
                "peuvent t'aider à les identifier.",
            step = step,
            primaryLabel = "Suivant",
            onPrimary = { step = 1 },
            modifier = modifier,
        )

        1 -> PermissionsStep(
            step = step,
            onContinue = { step = 2 },
            modifier = modifier,
        )

        2 -> NetworkOptionsScreen(
            // Pré-rempli sur le défaut device-first (OFF) ; le choix est mémorisé
            // par appareil et re-proposé, déjà coché, après connexion.
            initialEnabled = remember { SyncPreferences(context).isEnabled() },
            onContinue = { enabled ->
                SyncPreferences(context).setEnabled(enabled)
                // Sans cloud, aucune fleur n'atteint le serveur : les questions de
                // partage seraient sans objet.
                if (enabled) step = 3 else onFinish()
            },
            modifier = modifier,
        )

        else -> SharingDefaultsStep(
            step = step,
            onContinue = {
                SharePreferences(context).markSetupDone()
                onFinish()
            },
            modifier = modifier,
        )
    }
}

/**
 * Deuxième écran : justifie caméra et localisation avant de déclencher la
 * demande système. L'utilisateur peut passer (les permissions seront de toute
 * façon redemandées au moment de la capture).
 */
@Composable
private fun PermissionsStep(
    step: Int,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (permissions, request) = rememberMultiplePermissionsState(
        listOf(AppPermission.CAMERA, AppPermission.LOCATION),
    )
    val allGranted = permissions.allGranted

    OnboardingScaffold(
        step = step,
        modifier = modifier,
        content = {
            EmojiBadge("📷")
            Text(
                text = "Deux accès pour capturer",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            AppPermission.entries.forEach { permission ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = permission.label,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = permission.rationale,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                text = "Tu pourras toujours les accorder plus tard, au moment de " +
                    "prendre ta première photo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        },
        actions = {
            if (allGranted) {
                Text(
                    text = "Accès accordés ✅",
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text("Continuer")
                }
            } else {
                Button(onClick = request, modifier = Modifier.fillMaxWidth()) {
                    Text("Autoriser")
                }
                TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text("Plus tard")
                }
            }
        },
    )
}

/**
 * Troisième écran : les réglages de partage par défaut. Chaque réponse est
 * persistée à la volée par [SharingDefaultsForm] ; « Continuer » ne fait que
 * graver le fait que la question a été posée.
 */
@Composable
private fun SharingDefaultsStep(
    step: Int,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingScaffold(
        step = step,
        modifier = modifier,
        content = {
            EmojiBadge("📤")
            Text(
                text = "Comment partages-tu ?",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Ces réponses préremplissent la feuille de partage. " +
                    "Tu pourras les changer à tout moment dans ton profil.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                SharingDefaultsForm(modifier = Modifier.padding(16.dp))
            }
        },
        actions = {
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("Continuer")
            }
        },
    )
}

/** Écran d'onboarding simple : emoji, titre, texte et un bouton principal. */
@Composable
private fun OnboardingPage(
    emoji: String,
    title: String,
    body: String,
    step: Int,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingScaffold(
        step = step,
        modifier = modifier,
        content = {
            EmojiBadge(emoji)
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        },
        actions = {
            Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
                Text(primaryLabel)
            }
        },
    )
}

/**
 * Ossature commune des écrans d'onboarding : contenu centré défilable, indicateur
 * de progression et zone d'actions ancrée en bas.
 */
@Composable
private fun OnboardingScaffold(
    step: Int,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            content = content,
        )
        Spacer(Modifier.height(24.dp))
        StepIndicator(current = step, total = ONBOARDING_STEPS)
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = actions,
        )
    }
}

/** Pastille circulaire portant l'emoji illustratif de l'écran. */
@Composable
private fun EmojiBadge(emoji: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(96.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = emoji, style = MaterialTheme.typography.displaySmall)
        }
    }
}

/** Points de progression (l'actif est plus large et coloré). */
@Composable
private fun StepIndicator(current: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val active = index == current
            Surface(
                shape = CircleShape,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier
                    .size(width = if (active) 24.dp else 8.dp, height = 8.dp),
            ) {}
        }
    }
}

/** Nombre total d'écrans de l'onboarding. */
private const val ONBOARDING_STEPS = 4

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    FloraPinTheme {
        OnboardingScreen(onFinish = {})
    }
}
