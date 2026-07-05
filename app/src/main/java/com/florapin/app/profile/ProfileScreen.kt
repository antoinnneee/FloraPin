package com.florapin.app.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florapin.app.BuildConfig
import com.florapin.app.sync.SyncPreferences
import com.florapin.app.sync.SyncScheduler

/**
 * Écran Profil (NODE-97) : nom + email de l'utilisateur courant et bouton de
 * déconnexion (déplacé depuis l'écran Amis). Le nom s'affiche immédiatement
 * depuis le stockage local, l'email est complété via le réseau.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModel.factory(androidx.compose.ui.platform.LocalContext.current),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            saving = state.passwordSaving,
            error = state.passwordError,
            onConfirm = { oldPassword, newPassword ->
                viewModel.changePassword(oldPassword, newPassword) {
                    showPasswordDialog = false
                }
            },
            onDismiss = {
                showPasswordDialog = false
                viewModel.clearPasswordFeedback()
            },
        )
    }

    if (showDeleteDialog) {
        DeleteAccountDialog(
            deleting = state.deleting,
            error = state.deleteError,
            onConfirm = { password ->
                viewModel.deleteAccount(password) {
                    showDeleteDialog = false
                    onAccountDeleted()
                }
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Profil") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = state.displayName.ifBlank { "—" },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = state.email.ifBlank { "Email indisponible" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (state.emailVerified) "✓ Email vérifié" else "⚠ Email non vérifié",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.emailVerified) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }

            // Statistiques collaboratives : nombre de mes propositions acceptées.
            state.acceptedProposals?.let { count ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "🌿 $count",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = if (count == 1) {
                                "identification acceptée par un ami"
                            } else {
                                "identifications acceptées par des amis"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!state.emailVerified && state.email.isNotBlank()) {
                EmailVerificationSection(
                    currentEmail = state.email,
                    sending = state.verificationSending,
                    verificationMessage = state.verificationMessage,
                    emailSaving = state.emailSaving,
                    emailError = state.emailError,
                    onVerify = viewModel::requestEmailVerification,
                    onChangeEmail = viewModel::changeEmail,
                )
            }

            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            SyncSettingsSection()

            LocalBackupSection(
                running = state.backupRunning,
                message = state.backupMessage,
                onExport = viewModel::exportBackup,
                onImport = viewModel::importBackup,
            )

            SecuritySection(
                successMessage = state.passwordMessage,
                onChangePassword = { showPasswordDialog = true },
            )

            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Se déconnecter")
            }

            PrivacyPolicyLink()

            DangerZone(onDeleteAccount = { showDeleteDialog = true })
        }
    }
}

/**
 * Section de vérification d'email (NODE-117), affichée tant que l'adresse n'est
 * pas vérifiée : bouton d'envoi du lien + champ pour corriger/changer l'adresse
 * avant de la vérifier.
 */
@Composable
private fun EmailVerificationSection(
    currentEmail: String,
    sending: Boolean,
    verificationMessage: String?,
    emailSaving: Boolean,
    emailError: String?,
    onVerify: () -> Unit,
    onChangeEmail: (String) -> Unit,
) {
    var email by remember(currentEmail) { mutableStateOf(currentEmail) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Vérifier votre adresse", style = MaterialTheme.typography.titleSmall)
            Text(
                "Vous pouvez corriger votre adresse avant de la vérifier ; " +
                    "le changement n'est plus possible une fois vérifiée.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                enabled = !emailSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            emailError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = { onChangeEmail(email.trim()) },
                enabled = email.trim() != currentEmail && email.isNotBlank() && !emailSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Enregistrer la nouvelle adresse")
            }

            // Vérification d'email désactivée tant que l'envoi d'emails n'est pas
            // opérationnel (configuration DNS en cours). Réactiver en repassant
            // `enabled = !sending` une fois le DNS/SMTP configuré.
            Button(
                onClick = onVerify,
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Vérifier mon email (bientôt)")
            }
            Text(
                "L'envoi d'email n'est pas encore activé (configuration DNS en cours).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            verificationMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * Réglage « Synchronisation cloud » (par appareil). Un bouton « Tout
 * synchroniser » force une passe immédiate (même si l'automatique est
 * désactivée) ; une case « Synchroniser automatiquement » planifie la sync en
 * arrière-plan (périodique + au retour réseau + après chaque modification) et,
 * une fois décochée, l'annule (les photos restent alors sur l'appareil).
 */
@Composable
private fun SyncSettingsSection() {
    val context = LocalContext.current
    val prefs = remember(context) { SyncPreferences(context) }
    var autoEnabled by remember { mutableStateOf(prefs.isEnabled()) }
    var syncRequested by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Synchronisation cloud",
                style = MaterialTheme.typography.titleSmall,
            )

            Button(
                onClick = {
                    SyncScheduler.syncNow(context, force = true)
                    syncRequested = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Tout synchroniser")
            }
            if (syncRequested) {
                Text(
                    text = "Synchronisation lancée.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = autoEnabled,
                    onCheckedChange = { value ->
                        autoEnabled = value
                        prefs.setEnabled(value)
                        if (value) {
                            SyncScheduler.schedulePeriodic(context)
                            SyncScheduler.syncNow(context)
                        } else {
                            SyncScheduler.cancelAll(context)
                        }
                    },
                )
                Text(
                    text = "Synchroniser automatiquement",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "Sans synchronisation automatique, vos photos restent sur cet " +
                    "appareil jusqu'à ce que vous lanciez « Tout synchroniser ». " +
                    "Activez-la pour tout sauvegarder en continu et retrouver votre " +
                    "bibliothèque sur vos autres appareils.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Section « Sauvegarde locale » (TÂCHE 1.5) : filet de sécurité du mode 100 %
 * local. « Exporter » écrit un ZIP (données + photos) via le sélecteur de
 * documents ; « Importer » restaure une archive (fusion sans écrasement). Aucun
 * réseau requis — device-first.
 */
@Composable
private fun LocalBackupSection(
    running: Boolean,
    message: String?,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
) {
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(onExport) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImport) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Sauvegarde locale",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Exportez toutes vos fleurs et photos dans un fichier ZIP que " +
                    "vous gardez où vous voulez, puis réimportez-le pour les " +
                    "retrouver. Fonctionne entièrement hors ligne.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { exportLauncher.launch(defaultBackupFileName()) },
                    enabled = !running,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Exporter")
                }
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf("application/zip", "application/octet-stream"),
                        )
                    },
                    enabled = !running,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Importer")
                }
            }

            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Nom de fichier suggéré pour l'export : `florapin-sauvegarde-AAAAMMJJ.zip`. */
private fun defaultBackupFileName(): String {
    val date = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
        .format(java.util.Date())
    return "florapin-sauvegarde-$date.zip"
}

/**
 * Lien vers la politique de confidentialité (exigence Play Store, NODE-119).
 * Ouvre l'URL configurée ([BuildConfig.PRIVACY_POLICY_URL]) dans le navigateur.
 */
@Composable
private fun PrivacyPolicyLink() {
    val context = LocalContext.current
    TextButton(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL))
            context.startActivity(intent)
        },
    ) {
        Text("Politique de confidentialité")
    }
}

/**
 * Section « Sécurité » (TÂCHE 1.6) : accès au changement de mot de passe. Le
 * message de succès s'affiche brièvement sous le bouton après une modification.
 */
@Composable
private fun SecuritySection(
    successMessage: String?,
    onChangePassword: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Sécurité",
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedButton(
                onClick = onChangePassword,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Changer le mot de passe")
            }
            successMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Dialogue de changement de mot de passe (TÂCHE 1.6) : re-saisie de l'ancien mot
 * de passe (vérifié côté serveur) + nouveau mot de passe confirmé localement.
 * [error] affiche un échec (ex. ancien mot de passe incorrect) sans fermer le
 * dialogue.
 */
@Composable
private fun ChangePasswordDialog(
    saving: Boolean,
    error: String?,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val tooShort = newPassword.isNotEmpty() && newPassword.length < 8
    val mismatch = confirmPassword.isNotEmpty() && confirmPassword != newPassword
    val canSubmit = oldPassword.isNotBlank() &&
        newPassword.length >= 8 &&
        confirmPassword == newPassword &&
        !saving

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Changer le mot de passe") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Vos autres appareils seront déconnectés ; celui-ci reste connecté.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text("Mot de passe actuel") },
                    singleLine = true,
                    enabled = !saving,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Nouveau mot de passe") },
                    singleLine = true,
                    enabled = !saving,
                    isError = tooShort,
                    supportingText = if (tooShort) {
                        { Text("Au moins 8 caractères.") }
                    } else {
                        null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirmer le nouveau mot de passe") },
                    singleLine = true,
                    enabled = !saving,
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text("Les mots de passe ne correspondent pas.") }
                    } else {
                        null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(oldPassword, newPassword) },
                enabled = canSubmit,
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text("Annuler")
            }
        },
    )
}

/** Section « zone de danger » regroupant l'effacement définitif du compte. */
@Composable
private fun DangerZone(onDeleteAccount: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Zone de danger",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(
            onClick = onDeleteAccount,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text("Supprimer mon compte")
        }
    }
}

/**
 * Dialogue de confirmation d'effacement du compte (NODE-118) : rappel RGPD,
 * re-saisie du mot de passe et bouton destructif. [error] affiche un échec
 * (ex. mot de passe incorrect) sans fermer le dialogue.
 */
@Composable
private fun DeleteAccountDialog(
    deleting: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text("Supprimer mon compte") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Cette action est définitive et efface toutes vos fleurs, " +
                        "photos et partages.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mot de passe") },
                    singleLine = true,
                    enabled = !deleting,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank() && !deleting,
            ) {
                Text(
                    "Supprimer définitivement",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !deleting) {
                Text("Annuler")
            }
        },
    )
}
