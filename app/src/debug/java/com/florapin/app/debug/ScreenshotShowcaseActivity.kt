package com.florapin.app.debug

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.florapin.app.BuildConfig
import com.florapin.app.R
import com.florapin.app.capture.CameraMode
import com.florapin.app.capture.CameraModeSelector
import com.florapin.app.capture.CameraTopBar
import com.florapin.app.capture.ClassicZoomControl
import com.florapin.app.capture.FlashSetting
import com.florapin.app.capture.ShutterButton
import com.florapin.app.capture.linearZoomForRatio
import com.florapin.app.detail.CommentDraftStore
import com.florapin.app.detail.CommentsSection
import com.florapin.app.detail.CommentsViewModel
import com.florapin.app.detail.DetailPhotoMosaic
import com.florapin.app.detail.DetailSection
import com.florapin.app.detail.DetailSectionTabs
import com.florapin.app.detail.ObservationNotes
import com.florapin.app.feed.SharedFlowerCard
import com.florapin.app.feed.SharedFlowerItem
import com.florapin.app.feed.SharedHeaderAction
import com.florapin.app.identify.IdentificationRequestSection
import com.florapin.app.identify.IdentificationRequestViewModel
import com.florapin.app.likes.LikeButton
import com.florapin.app.location.GeoPoint
import com.florapin.app.location.GpsFixState
import com.florapin.app.map.OfflineMapDetail
import com.florapin.app.map.OfflineMapDialog
import com.florapin.app.map.OfflineMapRegionUi
import com.florapin.app.map.OfflineMapSelection
import com.florapin.app.navigation.FloraBottomBar
import com.florapin.app.network.api.CommentsApi
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.api.IdentificationApi
import com.florapin.app.network.dto.AddFriendByIdRequest
import com.florapin.app.network.dto.CreateCommentRequest
import com.florapin.app.network.dto.CreateFriendshipRequest
import com.florapin.app.network.dto.FlowerCommentDto
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.FriendProfileDto
import com.florapin.app.network.dto.FriendUserDto
import com.florapin.app.network.dto.FriendshipDto
import com.florapin.app.network.dto.MyIdentificationRequestDto
import com.florapin.app.network.dto.ProposalStatsDto
import com.florapin.app.network.dto.ProposeSpeciesRequest
import com.florapin.app.network.dto.SpeciesProposalDto
import com.florapin.app.network.dto.UpdateCommentRequest
import com.florapin.app.profile.AvatarPicker
import com.florapin.app.ui.components.BotanicalIcon
import com.florapin.app.ui.theme.FloraPinTheme
import kotlinx.coroutines.delay
import org.maplibre.android.geometry.LatLngBounds
import retrofit2.Response

/**
 * Galerie de validation visuelle disponible uniquement dans le build debug.
 *
 * Écrans : shared, detail, profile, comments, camera et offline.
 * Exemple :
 * adb shell am start -n com.florapin.app.debug/.ScreenshotShowcaseActivity \
 *   --es screen detail --ez dark true
 */
class ScreenshotShowcaseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        render(intent)
    }

    private fun render(intent: Intent) {
        val screen = intent.getStringExtra(EXTRA_SCREEN) ?: "shared"
        val dark = intent.getBooleanExtra(EXTRA_DARK, false)
        setContent {
            FloraPinTheme(darkTheme = dark) {
                val background = MaterialTheme.colorScheme.background
                val barColor = background.toArgb()
                SideEffect {
                    window.statusBarColor = barColor
                    window.navigationBarColor = barColor
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    when (screen) {
                        "detail" -> DetailShowcase()
                        "profile" -> ProfileShowcase()
                        "comments" -> CommentsShowcase()
                        "camera" -> CameraShowcase()
                        "offline" -> OfflineMapShowcase()
                        else -> SharedShowcase()
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val EXTRA_DARK = "dark"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedShowcase() {
    val flowers = remember {
        listOf(
            demoSharedFlower(
                id = "one",
                owner = "Antoine",
                species = "Épilobe en épi",
                drawable = R.drawable.avatar_default_butterfly,
                comments = 2,
            ),
            demoSharedFlower(
                id = "two",
                owner = "Véronique",
                species = "Géranium des prés",
                drawable = R.drawable.avatar_default_bee,
                comments = 1,
            ),
            demoSharedFlower(
                id = "three",
                owner = "Marie",
                species = "Lavande sauvage",
                drawable = R.drawable.avatar_default_fox,
                comments = 4,
            ),
            demoSharedFlower(
                id = "four",
                owner = "Thomas",
                species = "Coquelicot",
                drawable = R.drawable.avatar_default_owl,
                comments = 0,
            ),
        )
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Partagées",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    actions = {
                        Row(
                            modifier = Modifier.padding(end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SharedHeaderAction(
                                icon = R.drawable.ic_nav_map,
                                contentDescription = "Ouvrir la carte",
                                onClick = {},
                            )
                            SharedHeaderAction(
                                icon = R.drawable.ic_friends_botanical,
                                contentDescription = "Amis",
                                onClick = {},
                            )
                            SharedHeaderAction(
                                icon = R.drawable.ic_notification_bell_botanical,
                                contentDescription = "Notifications",
                                onClick = {},
                                badge = 5,
                                badgeContainerColor = MaterialTheme.colorScheme.error,
                                iconSize = 32.dp,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 8.dp,
                    end = 12.dp,
                    bottom = 104.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = true, onClick = {}, label = { Text("Récentes") })
                        FilterChip(selected = false, onClick = {}, label = { Text("Meilleures") })
                        FilterChip(
                            selected = false,
                            onClick = {},
                            label = { Text("★ Ma sélection") },
                        )
                    }
                }
                items(flowers) { item ->
                    SharedFlowerCard(
                        item = item,
                        saved = false,
                        onToggleSave = {},
                        onToggleLike = {},
                        onReact = {},
                        onComment = {},
                    )
                }
            }
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            FloraBottomBar(
                currentRoute = "feed",
                onSelect = {},
                onCapture = {},
                feedBadge = 3,
            )
        }
    }
}

private fun demoSharedFlower(
    id: String,
    owner: String,
    species: String,
    drawable: Int,
    comments: Int,
): SharedFlowerItem {
    val image = "android.resource://${BuildConfig.APPLICATION_ID}/$drawable"
    return SharedFlowerItem(
        flower = FlowerDto(
            id = id,
            ownerId = "owner-$id",
            imageUrl = image,
            thumbnailUrl = image,
            takenAt = "2026-07-27T10:00:00Z",
            notes = "",
            visibility = "friends",
            species = species,
            commentCount = comments,
            createdAt = "2026-07-27T10:00:00Z",
            updatedAt = "2026-07-27T10:00:00Z",
        ),
        ownerName = owner,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailShowcase() {
    val identificationViewModel = remember { demoIdentificationRequestViewModel() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Détail", fontWeight = FontWeight.Bold) },
                navigationIcon = { Text("←", modifier = Modifier.padding(16.dp)) },
                actions = { Text("⋯", modifier = Modifier.padding(16.dp)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            DetailPhotoMosaic(
                models = listOf(
                    R.drawable.avatar_default_butterfly,
                    R.drawable.avatar_default_bee,
                ),
                onOpen = {},
                onAddPhoto = {},
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IdentificationRequestSection(
                    flowerServerId = "flower-showcase",
                    viewModel = identificationViewModel,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {}) {
                    BotanicalIcon(
                        iconRes = R.drawable.ic_edit_botanical,
                        contentDescription = "Modifier le nom de l’espèce",
                        size = 24.dp,
                    )
                }
                LikeButton(
                    myReaction = null,
                    count = 0,
                    onToggle = {},
                    onReact = {},
                )
            }
            DetailSectionTabs(
                selected = DetailSection.NOTES,
                commentCount = 3,
                onSelect = {},
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            ObservationNotes(
                flowerId = 1L,
                storedNotes = "",
                onSaveNotes = {},
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileShowcase() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Profil",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            AvatarPicker(
                avatarUrl = null,
                seed = "showcase-user",
                uploading = false,
                onPick = {},
                onPickDefault = {},
            )
            Text("Antoine", style = MaterialTheme.typography.headlineMedium)
            Text(
                "antoines.flora@example.fr",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    ProfileStat("42", "Fleurs")
                    ProfileStat("12", "Espèces")
                    ProfileStat("8", "Régions")
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsShowcase() {
    val viewModel = remember { demoCommentsViewModel() }
    LaunchedEffect(viewModel) {
        viewModel.bind("flower-showcase")
        delay(150)
        viewModel.updateDraft("@")
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
    ) {
        Text(
            "Partagées",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(24.dp),
        )
        ModalBottomSheet(
            onDismissRequest = {},
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            CommentsSection(
                viewModel = viewModel,
                scrollComments = true,
                modifier = Modifier
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun CameraShowcase() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        androidx.compose.ui.graphics.Color(0xFF23372D),
                        androidx.compose.ui.graphics.Color(0xFF526A57),
                        androidx.compose.ui.graphics.Color(0xFF17221B),
                    ),
                ),
            ),
    ) {
        Text(
            "✿",
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f),
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.align(Alignment.Center),
        )
        CameraTopBar(
            gpsFix = GpsFixState.Fixed(GeoPoint(48.8566, 2.3522, 4.8f)),
            mode = CameraMode.CLASSIC,
            flash = FlashSetting.AUTO,
            torchEnabled = false,
            gridEnabled = false,
            macroEnabled = false,
            onFlash = {},
            onTorch = {},
            onGrid = {},
            onMacro = {},
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ClassicZoomControl(
                minZoom = 0.5f,
                maxZoom = 10f,
                zoomRatio = 1f,
                linearZoom = linearZoomForRatio(1f, 0.5f, 10f),
                onLinearZoom = {},
            )
            ShutterButton(
                isCapturing = false,
                accent = androidx.compose.ui.graphics.Color(0xFFB8E0C6),
                onClick = {},
            )
            CameraModeSelector(selected = CameraMode.CLASSIC, onSelect = {})
        }
    }
}

@Composable
private fun OfflineMapShowcase() {
    val bounds = remember { LatLngBounds.from(48.95, 2.55, 48.70, 2.15) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
    ) {
        OfflineMapDialog(
            selection = OfflineMapSelection(bounds = bounds, currentZoom = 14.0),
            suggestedName = "Paris et environs",
            regions = listOf(
                OfflineMapRegionUi(
                    id = 1L,
                    name = "Paris et environs",
                    styleId = "bright-v2",
                    progress = 1f,
                    completedBytes = 27_200_000L,
                    isComplete = true,
                    isActive = false,
                    createdAt = 1L,
                    bounds = bounds,
                    minimumZoom = 12.0,
                    maximumZoom = 18.0,
                    pixelRatio = 2f,
                ),
            ),
            isCreating = false,
            error = null,
            notice = null,
            onDownload = { _: String, _: OfflineMapDetail -> },
            onToggle = {},
            onDelete = {},
            onShow = {},
            onDismiss = {},
        )
    }
}

private fun demoCommentsViewModel(): CommentsViewModel {
    val friends = listOf(
        demoFriend("friend-1", "Véronique"),
        demoFriend("friend-2", "Marie"),
        demoFriend("friend-3", "Thomas"),
    )
    val comments = listOf(
        FlowerCommentDto(
            id = "comment-1",
            flowerId = "flower-showcase",
            authoredBy = "friend-1",
            authorName = "Véronique",
            body = "Très belle observation !",
            createdAt = "2026-07-27T12:00:00Z",
        ),
        FlowerCommentDto(
            id = "comment-2",
            flowerId = "flower-showcase",
            authoredBy = "me",
            authorName = "Antoine",
            body = "Merci, elle poussait près du sentier.",
            canEdit = true,
            canDelete = true,
            createdAt = "2026-07-27T12:05:00Z",
        ),
    )
    return CommentsViewModel(
        api = object : CommentsApi {
            override suspend fun list(flowerId: String) = comments
            override suspend fun post(
                flowerId: String,
                body: CreateCommentRequest,
            ) = comments.first().copy(id = "new", body = body.body)

            override suspend fun update(
                flowerId: String,
                commentId: String,
                body: UpdateCommentRequest,
            ) = comments.first().copy(id = commentId, body = body.body)

            override suspend fun delete(
                flowerId: String,
                commentId: String,
            ): Response<Unit> = Response.success(Unit)
        },
        drafts = object : CommentDraftStore {
            private var draft = ""
            override fun load(flowerServerId: String) = draft
            override fun save(flowerServerId: String, draft: String) {
                this.draft = draft
            }
        },
        friendships = object : FriendshipsApi {
            override suspend fun list() = friends
            override suspend fun request(body: CreateFriendshipRequest): FriendshipDto =
                error("Unused")

            override suspend fun requestById(body: AddFriendByIdRequest): FriendshipDto =
                error("Unused")

            override suspend fun accept(id: String): FriendshipDto = error("Unused")
            override suspend fun remove(id: String): Response<Unit> = Response.success(Unit)
            override suspend fun profile(id: String): FriendProfileDto = error("Unused")
        },
    )
}

private fun demoIdentificationRequestViewModel() = IdentificationRequestViewModel(
    api = object : IdentificationApi {
        override suspend fun request(flowerId: String): Response<Unit> =
            Response.success(Unit)

        override suspend fun remind(flowerId: String): Response<Unit> =
            Response.success(Unit)

        override suspend fun cancel(flowerId: String): Response<Unit> =
            Response.success(Unit)

        override suspend fun listToIdentify(): List<FlowerDto> = emptyList()

        override suspend fun listMyRequests(): List<MyIdentificationRequestDto> =
            emptyList()

        override suspend fun propose(
            flowerId: String,
            body: ProposeSpeciesRequest,
        ): SpeciesProposalDto = error("Unused")

        override suspend fun listProposals(flowerId: String): List<SpeciesProposalDto> =
            emptyList()

        override suspend fun acceptProposal(
            flowerId: String,
            proposalId: String,
        ): SpeciesProposalDto = error("Unused")

        override suspend fun thankProposal(
            flowerId: String,
            proposalId: String,
        ): SpeciesProposalDto = error("Unused")

        override suspend fun rejectProposal(
            flowerId: String,
            proposalId: String,
        ): Response<Unit> = Response.success(Unit)

        override suspend fun proposalStats() = ProposalStatsDto(acceptedProposals = 0)
    },
)

private fun demoFriend(id: String, name: String) = FriendshipDto(
    id = "friendship-$id",
    status = "accepted",
    direction = "outgoing",
    user = FriendUserDto(id = id, displayName = name, email = "$id@example.fr"),
    createdAt = "2026-07-27T10:00:00Z",
)
