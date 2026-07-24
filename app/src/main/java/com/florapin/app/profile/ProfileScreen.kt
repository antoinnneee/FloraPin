package com.florapin.app.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.florapin.app.BuildConfig
import com.florapin.app.R
import com.florapin.app.share.SharingDefaultsForm
import com.florapin.app.sync.PrefsLastSyncStore
import com.florapin.app.sync.SyncOutcome
import com.florapin.app.sync.SyncPreferences
import com.florapin.app.sync.SyncScheduler
import com.florapin.app.sync.SyncStatus
import com.florapin.app.sync.SyncStatusStore
import com.florapin.app.ui.components.DefaultAvatars
import com.florapin.app.ui.components.FloraAvatar
import com.florapin.app.ui.components.rememberSingleLineKeyboardActions
import com.florapin.app.ui.components.singleLineKeyboardOptions
import com.florapin.app.ui.layout.topBarHeight
import com.florapin.app.util.formatCaptureDate

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
    onOpenFlower: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModel.factory(androidx.compose.ui.platform.LocalContext.current),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }

    if (showNameDialog) {
        EditDisplayNameDialog(
            currentName = state.displayName,
            saving = state.nameSaving,
            error = state.nameError,
            onConfirm = { newName ->
                viewModel.updateDisplayName(newName) {
                    showNameDialog = false
                }
            },
            onDismiss = {
                showNameDialog = false
                viewModel.clearNameFeedback()
            },
        )
    }

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

    val tabs = listOf("Profil", "Badges", "Configuration")
    val tabWeights = List(tabs.size) { 1f }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                expandedHeight = topBarHeight,
                title = { Text("Profil") },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val selectedTabColor = MaterialTheme.colorScheme.primary
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                tabs.forEachIndexed { index, label ->
                    val isSelected = selectedTab == index
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        modifier = Modifier
                            .weight(tabWeights[index])
                            .fillMaxHeight()
                            .then(
                                if (isSelected) {
                                    Modifier.drawBehind {
                                        val indicatorHeight = 3.dp.toPx()
                                        drawRect(
                                            color = selectedTabColor,
                                            topLeft = Offset(0f, size.height - indicatorHeight),
                                            size = Size(size.width, indicatorHeight),
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                        text = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                    )
                }
            }
                HorizontalDivider()
            }

            when (selectedTab) {
                0 -> ProfileTab(
                    state = state,
                    onPickAvatar = viewModel::uploadAvatar,
                    onPickDefaultAvatar = viewModel::uploadDefaultAvatar,
                    onEditName = { showNameDialog = true },
                    onVerify = viewModel::requestEmailVerification,
                    onChangeEmail = viewModel::changeEmail,
                    onOpenBadges = { selectedTab = 1 },
                    onOpenFlower = onOpenFlower,
                )

                1 -> BadgesTab()

                else -> ConfigurationTab(
                    state = state,
                    onExport = viewModel::exportBackup,
                    onImport = viewModel::importBackup,
                    onChangePassword = { showPasswordDialog = true },
                    onLogout = onLogout,
                    onDeleteAccount = { showDeleteDialog = true },
                )
            }
        }
    }
}

/**
 * Onglet ① Profil : avatar, identité, statistiques d'entraide et emplacements
 * et aperçu des dernières fleurs.
 */
@Composable
private fun ProfileTab(
    state: ProfileUiState,
    onPickAvatar: (Uri) -> Unit,
    onPickDefaultAvatar: (Int) -> Unit,
    onEditName: () -> Unit,
    onVerify: () -> Unit,
    onChangeEmail: (String) -> Unit,
    onOpenBadges: () -> Unit,
    onOpenFlower: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AvatarPicker(
                    avatarUrl = state.avatarUrl,
                    seed = state.userId.ifBlank {
                        state.email.ifBlank { state.displayName }
                    },
                    uploading = state.avatarUploading,
                    onPick = onPickAvatar,
                    onPickDefault = onPickDefaultAvatar,
                )
                state.avatarError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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
                OutlinedButton(
                    onClick = onEditName,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Modifier le nom")
                }
                state.nameMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Nombre de badges débloqués (TÂCHE 5.1) : raccourci vers l'onglet Badges.
        state.badgeCount?.let { count ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenBadges() },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = "🏅", style = MaterialTheme.typography.headlineSmall)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (count == 1) "1 badge débloqué" else "$count badges débloqués",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Voir ma collection de badges",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(text = "›", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }

        // Statistiques collaboratives : nombre de mes propositions acceptées.
        state.acceptedProposals?.let { count ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_identify_botanical),
                        contentDescription = "Identifications acceptées",
                        modifier = Modifier.size(32.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (count == 1) {
                                "1 identification"
                            } else {
                                "$count identifications"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (count == 1) {
                                "acceptée par un ami"
                            } else {
                                "acceptées par des amis"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Aperçu des dernières fleurs (TÂCHE 5.1) : device-first, toujours local.
        if (state.recentFlowers.isNotEmpty()) {
            RecentFlowersSection(
                flowers = state.recentFlowers,
                onOpenFlower = onOpenFlower,
            )
        }

        if (!state.emailVerified && state.email.isNotBlank()) {
            EmailVerificationSection(
                currentEmail = state.email,
                sending = state.verificationSending,
                verificationMessage = state.verificationMessage,
                emailSaving = state.emailSaving,
                emailError = state.emailError,
                onVerify = onVerify,
                onChangeEmail = onChangeEmail,
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
    }
}

/**
 * Section « Dernières fleurs » de l'onglet Profil (TÂCHE 5.1) : rangée
 * horizontale des captures les plus récentes ; un tap ouvre le détail de la
 * fleur. 100 % local (device-first), toujours disponible hors-ligne.
 */
@Composable
private fun RecentFlowersSection(
    flowers: List<RecentFlower>,
    onOpenFlower: (Long) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Dernières fleurs",
                style = MaterialTheme.typography.titleSmall,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(flowers, key = { it.id }) { flower ->
                    AsyncImage(
                        model = flower.thumbnailModel,
                        contentDescription = flower.label ?: "Fleur",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenFlower(flower.id) },
                    )
                }
            }
        }
    }
}

/** Avatar courant et choix entre compagnons FloraPin ou galerie du téléphone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvatarPicker(
    avatarUrl: String?,
    seed: String,
    uploading: Boolean,
    onPick: (Uri) -> Unit,
    onPickDefault: (Int) -> Unit,
) {
    var showChoices by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onPick) }

    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = !uploading) {
                showChoices = true
            },
        contentAlignment = Alignment.Center,
    ) {
        FloraAvatar(
            avatarUrl = avatarUrl,
            seed = seed,
            contentDescription = "Photo de profil",
            modifier = Modifier.fillMaxSize(),
        )
        if (uploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
    Text(
        text = "Modifier la photo",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(enabled = !uploading) { showChoices = true },
    )

    if (showChoices) {
        ModalBottomSheet(onDismissRequest = { showChoices = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Choisir une image de profil",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Compagnons FloraPin",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().height(208.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gridItems(DefaultAvatars.all, key = { it.id }) { avatar ->
                        Image(
                            painter = painterResource(avatar.resourceId),
                            contentDescription = avatar.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .clickable {
                                    showChoices = false
                                    onPickDefault(avatar.resourceId)
                                },
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        showChoices = false
                        launcher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choisir une photo du téléphone")
                }
                Text(
                    text = "Sans choix, un compagnon vous est attribué automatiquement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
    }
}

/**
 * Onglet ③ Configuration (TÂCHE 5.1) : regroupe les réglages du compte
 * préexistants — synchronisation, sauvegarde locale, sécurité, déconnexion,
 * confidentialité et zone de danger.
 */
@Composable
private fun ConfigurationTab(
    state: ProfileUiState,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onChangePassword: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SyncSettingsSection()

        DefaultSharingSection()

        LocalBackupSection(
            running = state.backupRunning,
            message = state.backupMessage,
            onExport = onExport,
            onImport = onImport,
        )

        SecuritySection(
            successMessage = state.passwordMessage,
            onChangePassword = onChangePassword,
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

        DangerZone(onDeleteAccount = onDeleteAccount)
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
                keyboardOptions = singleLineKeyboardOptions(KeyboardType.Email),
                keyboardActions = rememberSingleLineKeyboardActions(),
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
 * Réglages de partage par défaut (par appareil) : ils préremplissent la feuille
 * de partage d'une fleur. Renseignés à la première ouverture de l'app, modifiables
 * ici à tout moment.
 */
@Composable
private fun DefaultSharingSection() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Partage par défaut",
                style = MaterialTheme.typography.titleSmall,
            )
            SharingDefaultsForm()
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

    // État de la dernière passe de synchro (TÂCHE 6.14) : en cours / réussie /
    // échouée, réémis en direct par le worker. L'horodatage de la dernière synchro
    // réussie vient, lui, du curseur `last_sync_at` (PrefsLastSyncStore) : on le
    // relit à chaque changement d'état pour capter la mise à jour post-succès.
    val statusStore = remember(context) { SyncStatusStore(context) }
    val status by statusStore.flow()
        .collectAsStateWithLifecycle(initialValue = statusStore.read())
    val lastSyncStore = remember(context) { PrefsLastSyncStore(context) }
    val lastSyncLabel = remember(status) { formatLastSync(lastSyncStore.get()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Synchronisation cloud",
                style = MaterialTheme.typography.titleSmall,
            )

            SyncStatusRow(status = status, lastSyncLabel = lastSyncLabel)

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
 * Ligne d'état de la synchronisation (TÂCHE 6.14) : pastille + libellé du dernier
 * résultat du worker (en cours / réussie / échec + message), et horodatage de la
 * dernière synchro réussie (curseur `last_sync_at`). Device-first : à l'état
 * initial (aucune passe encore observée), on n'affiche rien d'alarmant.
 */
@Composable
private fun SyncStatusRow(status: SyncStatus, lastSyncLabel: String?) {
    val (dot, label, color) = when (status.outcome) {
        SyncOutcome.RUNNING -> Triple(
            "🔄",
            "Synchronisation en cours…",
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SyncOutcome.SUCCESS -> Triple(
            "✅",
            lastSyncLabel?.let { "Synchronisé le $it" } ?: "Synchronisé",
            MaterialTheme.colorScheme.primary,
        )
        SyncOutcome.ERROR -> Triple(
            "⚠️",
            "Échec de la synchronisation",
            MaterialTheme.colorScheme.error,
        )
        SyncOutcome.IDLE -> Triple(
            "☁️",
            lastSyncLabel?.let { "Dernière synchro le $it" } ?: "Jamais synchronisé",
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = dot, style = MaterialTheme.typography.bodyMedium)
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = color)
        }
        // Détail de l'erreur (message du worker), quand disponible.
        if (status.outcome == SyncOutcome.ERROR) {
            status.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Sur échec, on rappelle la dernière synchro réussie si connue.
            lastSyncLabel?.let {
                Text(
                    text = "Dernière synchro réussie le $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Formate le curseur `last_sync_at` (horodatage serveur ISO-8601) en date/heure
 * locale lisible, ou null s'il est absent (jamais synchronisé) ou illisible.
 */
private fun formatLastSync(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        formatCaptureDate(java.time.Instant.parse(iso).toEpochMilli())
    }.getOrNull()
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
                    keyboardOptions = singleLineKeyboardOptions(KeyboardType.Password),
                    keyboardActions = rememberSingleLineKeyboardActions(),
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
                    keyboardOptions = singleLineKeyboardOptions(KeyboardType.Password),
                    keyboardActions = rememberSingleLineKeyboardActions(),
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
                    keyboardOptions = singleLineKeyboardOptions(KeyboardType.Password),
                    keyboardActions = rememberSingleLineKeyboardActions(),
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

/**
 * Dialogue de modification du nom d'affichage (TÂCHE 1.7) : champ pré-rempli
 * avec le nom courant. Mêmes règles qu'à l'inscription (trim + 1..80 caractères,
 * validés côté serveur). [error] affiche un échec sans fermer le dialogue.
 */
@Composable
private fun EditDisplayNameDialog(
    currentName: String,
    saving: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    val trimmed = name.trim()
    val tooLong = trimmed.length > 80
    val canSubmit = trimmed.isNotEmpty() && !tooLong && trimmed != currentName && !saving

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Modifier le nom") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom affiché") },
                    singleLine = true,
                    keyboardOptions = singleLineKeyboardOptions(),
                    keyboardActions = rememberSingleLineKeyboardActions(),
                    enabled = !saving,
                    isError = tooLong,
                    supportingText = if (tooLong) {
                        { Text("80 caractères maximum.") }
                    } else {
                        null
                    },
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
                onClick = { onConfirm(trimmed) },
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
                    keyboardOptions = singleLineKeyboardOptions(KeyboardType.Password),
                    keyboardActions = rememberSingleLineKeyboardActions(),
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
