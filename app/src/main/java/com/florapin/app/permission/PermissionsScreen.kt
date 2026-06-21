package com.florapin.app.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.florapin.app.ui.theme.FloraPinTheme

/**
 * Écran d'onboarding des permissions : liste chaque permission, son statut, et
 * propose l'action adaptée (demander, ou ouvrir les réglages si refus définitif).
 */
@Composable
fun PermissionsScreen(
    state: MultiplePermissionsState,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val needsSettings = state.statuses.values
        .any { it == PermissionStatus.PERMANENTLY_DENIED }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Autorisations",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "FloraPin a besoin de quelques accès pour fonctionner. " +
                "Vous pouvez les accorder maintenant ou plus tard.",
            style = MaterialTheme.typography.bodyMedium,
        )

        state.permissions.forEach { permission ->
            PermissionCard(
                permission = permission,
                status = state.statuses[permission] ?: PermissionStatus.DENIED,
            )
        }

        Spacer(Modifier.height(8.dp))

        if (state.allGranted) {
            Text(
                text = "Tout est prêt ✅",
                style = MaterialTheme.typography.titleMedium,
            )
        } else {
            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Autoriser")
            }
            if (needsSettings) {
                OutlinedButton(
                    onClick = { context.openAppSettings() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ouvrir les réglages")
                }
                Text(
                    text = "Certaines autorisations ont été refusées définitivement. " +
                        "Activez-les depuis les réglages de l'application.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    permission: AppPermission,
    status: PermissionStatus,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = permission.label,
                    style = MaterialTheme.typography.titleMedium,
                )
                StatusChip(status)
            }
            Text(
                text = permission.rationale,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun StatusChip(status: PermissionStatus) {
    val (label, color) = when (status) {
        PermissionStatus.GRANTED -> "Accordée" to MaterialTheme.colorScheme.primary
        PermissionStatus.DENIED -> "À autoriser" to MaterialTheme.colorScheme.secondary
        PermissionStatus.PERMANENTLY_DENIED ->
            "Refusée" to MaterialTheme.colorScheme.error
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = color,
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun PermissionsScreenPreview() {
    FloraPinTheme {
        // Aperçu statique : l'état réel dépend du système.
        Text("Aperçu PermissionsScreen (exécuter l'app pour l'état réel)")
    }
}
