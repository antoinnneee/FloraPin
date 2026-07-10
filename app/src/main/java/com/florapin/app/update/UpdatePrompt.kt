package com.florapin.app.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun UpdatePrompt(
    onDismiss: (doNotShowAgain: Boolean) -> Unit,
    onOpenPlayStore: (doNotShowAgain: Boolean) -> Unit,
) {
    var doNotShowAgain by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onDismiss(doNotShowAgain) },
        title = { Text("Une nouvelle version est disponible") },
        text = {
            Column {
                Text(
                    "Mettez FloraPin à jour pour profiter des dernières " +
                        "améliorations et corrections.",
                )
                Row(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .toggleable(
                            value = doNotShowAgain,
                            role = Role.Checkbox,
                            onValueChange = { doNotShowAgain = it },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = doNotShowAgain,
                        onCheckedChange = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Ne plus afficher pour cette version")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onOpenPlayStore(doNotShowAgain) }) {
                Text("Mettre à jour")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss(doNotShowAgain) }) {
                Text("Plus tard")
            }
        },
    )
}

fun openPlayStore(context: Context) {
    val packageName = context.packageName
    val playStoreIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$packageName"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        context.startActivity(playStoreIntent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
