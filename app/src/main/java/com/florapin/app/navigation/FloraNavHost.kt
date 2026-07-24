package com.florapin.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.florapin.app.auth.AuthUiState
import com.florapin.app.auth.AuthViewModel
import com.florapin.app.albums.AlbumDetailScreen
import com.florapin.app.albums.AlbumInvitationScreen
import com.florapin.app.albums.AlbumsScreen
import com.florapin.app.auth.EmailVerifyViewModel
import com.florapin.app.auth.ForgotPasswordScreen
import com.florapin.app.auth.LoginScreen
import com.florapin.app.auth.NetworkOptionsScreen
import com.florapin.app.auth.PasswordResetViewModel
import com.florapin.app.auth.RegisterScreen
import com.florapin.app.auth.ResetPasswordScreen
import com.florapin.app.auth.VerifyEmailScreen
import com.florapin.app.capture.CaptureFlow
import com.florapin.app.detail.DetailScreen
import com.florapin.app.detail.SpeciesDetailScreen
import com.florapin.app.feed.SharedFeedScreen
import com.florapin.app.friends.FriendProfileScreen
import com.florapin.app.friends.FriendsScreen
import com.florapin.app.gallery.GalleryScreen
import com.florapin.app.identify.IdentifyScreen
import com.florapin.app.map.MapScreen
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.notifications.NotificationCenterScreen
import com.florapin.app.onboarding.OnboardingPrefs
import com.florapin.app.onboarding.OnboardingScreen
import com.florapin.app.data.FloraDatabase
import com.florapin.app.permission.RequestNotificationPermissionOnce
import com.florapin.app.profile.ProfileScreen
import com.florapin.app.push.NotificationTarget
import com.florapin.app.push.PushTokenRegistrar
import com.florapin.app.sync.SyncPreferences
import com.florapin.app.sync.SyncScheduler
import com.florapin.app.ui.transition.FloraSharedScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Destinations de l'application. */
private object Routes {
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val NETWORK_OPTIONS = "network-options"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot-password"
    const val RESET_PASSWORD = "reset-password?token={token}"
    const val VERIFY_EMAIL = "verify-email?token={token}"
    const val GALLERY = "gallery"
    const val CAPTURE = "capture?albumId={albumId}"
    const val MAP = "map"
    const val FRIENDS = "friends"
    const val FRIEND_PROFILE = "friend/{userId}"
    const val FEED = "feed"
    const val PROFILE = "profile"
    const val ALBUMS = "albums"
    const val ALBUM_DETAIL = "album/{id}"
    const val ALBUM_INVITATION = "album-invitation/{groupId}"
    const val DETAIL = "detail/{id}"
    const val SPECIES_DETAIL = "species/{id}"
    const val IDENTIFY = "identify"
    const val NOTIFICATIONS = "notifications"

    fun detail(id: Long) = "detail/$id"

    /** Clé savedStateHandle : id d'une fleur soft-supprimée à annuler (TÂCHE 6.13). */
    const val KEY_DELETED_FLOWER = "deleted_flower_id"
    fun album(id: Long) = "album/$id"
    fun albumInvitation(groupId: String) = "album-invitation/$groupId"
    fun capture(albumId: Long? = null) =
        albumId?.let { "capture?albumId=$it" } ?: "capture"
    fun speciesDetail(id: String) = "species/$id"
    fun friendProfile(userId: String) = "friend/$userId"
}

/**
 * Routes du flux d'authentification. Le [BackHandler] global du NavHost ne s'y
 * applique pas : on y laisse la gestion du retour par défaut.
 */
private val authRoutes: Set<String> = setOf(
    Routes.ONBOARDING,
    Routes.LOGIN,
    Routes.NETWORK_OPTIONS,
    Routes.REGISTER,
    Routes.FORGOT_PASSWORD,
    Routes.RESET_PASSWORD,
    Routes.VERIFY_EMAIL,
)

/**
 * Graphe de navigation principal avec garde d'authentification : démarre sur
 * Login si l'utilisateur n'est pas connecté, sinon sur la galerie.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FloraNavHost(
    modifier: Modifier = Modifier,
    notificationTarget: NotificationTarget? = null,
    onNotificationTargetHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val loggedIn = remember {
        EncryptedTokenStore(context).refreshToken() != null
    }
    // L'onboarding s'insère avant le choix Login/Galerie, uniquement à la première
    // installation (drapeau figé à vrai pour les installs existantes, cf.
    // OnboardingPrefs.markSeenForExistingInstall dans FlorapinApp).
    val onboardingDone = remember { OnboardingPrefs(context).isDone() }
    val startDestination = when {
        !onboardingDone -> Routes.ONBOARDING
        loggedIn -> Routes.GALLERY
        else -> Routes.LOGIN
    }

    val currentRoute by navController.currentBackStackEntryAsState()
    val currentRouteStr = currentRoute?.destination?.route
    val showBottomBar = currentRouteStr in topLevelRoutes

    // Badge de nouveautés sur l'onglet « Partagées » : recalculé à chaque
    // changement d'onglet (l'ouverture du feed marque ses fleurs vues → 0).
    val feedBadgeViewModel: com.florapin.app.feed.FeedBadgeViewModel = viewModel()
    val feedBadge by feedBadgeViewModel.badge.collectAsStateWithLifecycle()
    LaunchedEffect(currentRouteStr) {
        if (loggedIn && currentRouteStr in topLevelRoutes) feedBadgeViewModel.refresh()
    }

    // Gestion du retour hardware, en trois temps (hors flux d'auth) :
    //   1. Sur un écran poussé (détail fleur, espèce, albums…), on dépile pour
    //      revenir à la page courante d'où l'on venait.
    //   2. Sur un onglet secondaire (Albums, Partagées, Profil), on revient à
    //      l'Accueil.
    //   3. Sur l'Accueil, le handler est désactivé : le système quitte l'app.
    // Placé au niveau du NavHost, ce handler reste un repli : tout BackHandler
    // interne (visionneuse plein écran, bottom sheet, étapes de capture) est
    // composé plus bas et garde donc la priorité (LIFO du dispatcher).
    val homeRoute = TopLevelDestination.HOME.route
    val inMainArea = currentRouteStr != null && currentRouteStr !in authRoutes
    BackHandler(enabled = inMainArea && currentRouteStr != homeRoute) {
        if (currentRouteStr != null && currentRouteStr !in topLevelRoutes) {
            // Écran poussé → page précédente. Si rien à dépiler (arrivée directe
            // via notification/deep link), repli sur l'Accueil.
            if (!navController.popBackStack()) navController.navigateToTab(homeRoute)
        } else {
            // Onglet secondaire → Accueil.
            navController.navigateToTab(homeRoute)
        }
    }

    // Tap sur notification (TÂCHE 2.2) : route vers le contenu concerné dès que
    // la cible change. Keyé sur la cible pour ne router qu'une fois par tap.
    LaunchedEffect(notificationTarget) {
        val target = notificationTarget ?: return@LaunchedEffect
        // Sécurité : hors utilisateur connecté, on ignore (le flux d'auth n'a pas
        // de destination « contenu »). La cible est tout de même consommée.
        if (loggedIn) {
            routeFromNotification(navController, context, target, homeRoute)
        }
        onNotificationTargetHandled()
    }

    Scaffold(
        modifier = modifier,
        // Aucun inset système ici : les écrans (TopAppBar) gèrent l'inset haut et
        // la NavigationBar gère son propre inset bas. Évite le double comptage.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                FloraBottomBar(
                    currentRoute = currentRoute?.destination?.route,
                    onSelect = { destination -> navController.navigateToTab(destination.route) },
                    onCapture = { navController.navigate(Routes.capture()) },
                    feedBadge = feedBadge,
                )
            }
        },
    ) { innerPadding ->
        // Transitions partagées galerie ↔ détail (TÂCHE 6.17). Le
        // SharedTransitionLayout enveloppe le NavHost pour que la vignette de la
        // galerie et l'image du détail (même clé) s'animent d'un écran à l'autre.
        // API expérimentale isolée dans ui/transition : voir FloraSharedScope.
        SharedTransitionLayout {
            val transitionScope = this
            NavHost(
                navController = navController,
                startDestination = startDestination,
                // La navigation principale est flottante : sur les quatre onglets
                // racine, le contenu continue jusqu'au bas de l'écran et la barre
                // le recouvre. Les écrans poussés conservent le padding éventuel
                // fourni par le Scaffold.
                modifier = if (showBottomBar) {
                    Modifier
                } else {
                    Modifier.padding(innerPadding)
                },
            ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinish = {
                    // Ne plus ré-afficher l'onboarding, puis rejoindre le flux normal :
                    // galerie si déjà connecté (rare : nouvelle install), sinon Login.
                    OnboardingPrefs(context).setDone()
                    if (loggedIn) navController.goToGallery() else navController.goToLogin()
                },
            )
        }

        composable(Routes.LOGIN) {
            val authViewModel: AuthViewModel =
                viewModel(factory = AuthViewModel.factory(context))
            val state by authViewModel.state.collectAsStateWithLifecycle()
            // Après connexion, on passe par l'écran « Options réseau » qui amorce
            // (ou non) la synchronisation selon le choix de l'utilisateur.
            OnAuthSuccess(state) {
                navController.goToNetworkOptions()
            }

            LoginScreen(
                isLoading = state is AuthUiState.Loading,
                error = (state as? AuthUiState.Error)?.message,
                onLogin = authViewModel::login,
                onSwitchToRegister = { navController.navigate(Routes.REGISTER) },
                onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
            )
        }

        composable(Routes.NETWORK_OPTIONS) {
            NetworkOptionsScreen(
                initialEnabled = remember { SyncPreferences(context).isEnabled() },
                onContinue = { enabled ->
                    // Mémorise le choix (par appareil) puis amorce la sync :
                    // startSync est no-op si la sync est désactivée.
                    SyncPreferences(context).setEnabled(enabled)
                    startSync(context)
                    navController.goToGallery()
                },
            )
        }

        composable(Routes.FORGOT_PASSWORD) {
            val resetViewModel: PasswordResetViewModel =
                viewModel(factory = PasswordResetViewModel.factory(context))
            val state by resetViewModel.state.collectAsStateWithLifecycle()
            ForgotPasswordScreen(
                isLoading = state.loading,
                requestSent = state.requestSent,
                error = state.error,
                onSubmit = resetViewModel::requestReset,
                onBackToLogin = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.RESET_PASSWORD,
            arguments = listOf(
                navArgument("token") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://florapin.pattounecorp.ovh/reset?token={token}" },
            ),
        ) { backStackEntry ->
            val token = backStackEntry.arguments?.getString("token").orEmpty()
            val resetViewModel: PasswordResetViewModel =
                viewModel(factory = PasswordResetViewModel.factory(context))
            val state by resetViewModel.state.collectAsStateWithLifecycle()
            ResetPasswordScreen(
                initialToken = token,
                isLoading = state.loading,
                resetDone = state.resetDone,
                error = state.error,
                onSubmit = resetViewModel::resetPassword,
                onResetDone = { navController.goToLogin() },
                onBackToLogin = { navController.goToLogin() },
            )
        }

        composable(
            route = Routes.VERIFY_EMAIL,
            arguments = listOf(
                navArgument("token") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://florapin.pattounecorp.ovh/verify?token={token}" },
            ),
        ) { backStackEntry ->
            val token = backStackEntry.arguments?.getString("token").orEmpty()
            val verifyViewModel: EmailVerifyViewModel =
                viewModel(factory = EmailVerifyViewModel.factory(context))
            val state by verifyViewModel.state.collectAsStateWithLifecycle()
            VerifyEmailScreen(
                token = token,
                state = state,
                onVerify = verifyViewModel::verify,
                onContinue = {
                    if (loggedIn) navController.goToGallery() else navController.goToLogin()
                },
            )
        }

        composable(Routes.REGISTER) {
            val authViewModel: AuthViewModel =
                viewModel(factory = AuthViewModel.factory(context))
            val state by authViewModel.state.collectAsStateWithLifecycle()
            OnAuthSuccess(state) {
                startSync(context)
                navController.goToGallery()
            }

            RegisterScreen(
                isLoading = state is AuthUiState.Loading,
                error = (state as? AuthUiState.Error)?.message,
                onRegister = { email, password, displayName, syncEnabled ->
                    // Mémorise le choix de sync (par appareil) avant l'inscription :
                    // OnAuthSuccess appelle startSync, qui est no-op si désactivée.
                    SyncPreferences(context).setEnabled(syncEnabled)
                    authViewModel.register(email, password, displayName)
                },
                onSwitchToLogin = { navController.popBackStack() },
            )
        }

        composable(Routes.GALLERY) { backStackEntry ->
            // Android 13+ : demande la permission notifications une seule fois,
            // à l'arrivée sur l'écran principal d'un utilisateur connecté (I11).
            RequestNotificationPermissionOnce()
            // Suppression annulable (TÂCHE 6.13) : le détail dépose l'id soft-
            // supprimé dans le savedStateHandle de la galerie avant de dépiler ;
            // la galerie propose alors l'annulation via un snackbar.
            val deletedFlowerId by backStackEntry.savedStateHandle
                .getStateFlow<Long?>(Routes.KEY_DELETED_FLOWER, null)
                .collectAsStateWithLifecycle()
            GalleryScreen(
                onCapture = { navController.navigate(Routes.capture()) },
                onFlowerClick = { id -> navController.navigate(Routes.detail(id)) },
                onOpenFriends = { navController.navigate(Routes.FRIENDS) },
                onOpenIdentify = { navController.navigate(Routes.IDENTIFY) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenMap = { navController.navigate(Routes.MAP) },
                deletedFlowerId = deletedFlowerId,
                onDeletedFlowerHandled = {
                    backStackEntry.savedStateHandle[Routes.KEY_DELETED_FLOWER] = null
                },
                sharedScope = FloraSharedScope(transitionScope, this),
            )
        }
        composable(Routes.IDENTIFY) {
            IdentifyScreen(
                onBack = { navController.popBackStack() },
                onOpenProfile = { uid -> navController.navigate(Routes.friendProfile(uid)) },
            )
        }
        composable(Routes.NOTIFICATIONS) {
            // Le tap réutilise le routage des push (TÂCHE 2.2) : résolution
            // serverId → fleur locale, ou repli feed/amis/accueil selon le type.
            val scope = rememberCoroutineScope()
            NotificationCenterScreen(
                onBack = { navController.popBackStack() },
                onOpen = { target ->
                    scope.launch {
                        routeFromNotification(navController, context, target, homeRoute)
                    }
                },
            )
        }
        composable(Routes.ALBUMS) {
            // Onglet racine : pas de flèche retour (le BackHandler global gère le
            // retour vers l'Accueil).
            AlbumsScreen(
                onOpenAlbum = { id -> navController.navigate(Routes.album(id)) },
            )
        }
        composable(
            route = Routes.ALBUM_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: return@composable
            AlbumDetailScreen(
                albumId = id,
                onBack = { navController.popBackStack() },
                onFlowerClick = { fid -> navController.navigate(Routes.detail(fid)) },
                onCaptureInAlbum = { navController.navigate(Routes.capture(id)) },
            )
        }
        composable(
            route = Routes.ALBUM_INVITATION,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            AlbumInvitationScreen(
                groupId = groupId,
                onBack = { navController.popBackStack() },
                onAccepted = { navController.navigateToTab(Routes.ALBUMS) },
            )
        }
        composable(Routes.FEED) {
            SharedFeedScreen(
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenProfile = { uid -> navController.navigate(Routes.friendProfile(uid)) },
            )
        }
        composable(Routes.FRIENDS) {
            FriendsScreen(
                onBack = { navController.popBackStack() },
                onOpenProfile = { uid -> navController.navigate(Routes.friendProfile(uid)) },
            )
        }
        composable(
            route = Routes.FRIEND_PROFILE,
            arguments = listOf(navArgument("userId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            FriendProfileScreen(
                userId = userId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.PROFILE) {
            val authViewModel: AuthViewModel =
                viewModel(factory = AuthViewModel.factory(context))
            ProfileScreen(
                onOpenFlower = { fid -> navController.navigate(Routes.detail(fid)) },
                onLogout = {
                    // Désenregistre le push tant que les jetons sont valides,
                    // puis déconnecte.
                    PushTokenRegistrar.unregister(context)
                    authViewModel.logout {
                        SyncScheduler.cancelAll(context)
                        navController.goToLogin()
                    }
                },
                onAccountDeleted = {
                    // Le compte (et ses device tokens) est déjà purgé côté serveur :
                    // on annule la sync locale et on retourne à Login.
                    SyncScheduler.cancelAll(context)
                    navController.goToLogin()
                },
            )
        }
        composable(
            route = Routes.CAPTURE,
            arguments = listOf(
                navArgument("albumId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments
                ?.getLong("albumId")
                ?.takeIf { it > 0L }
            CaptureFlow(
                albumId = albumId,
                onFinished = { navController.popBackStack() },
            )
        }
        composable(Routes.MAP) {
            MapScreen(
                onFlowerClick = { id -> navController.navigate(Routes.detail(id)) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: return@composable
            DetailScreen(
                flowerId = id,
                onBack = { navController.popBackStack() },
                sharedScope = FloraSharedScope(transitionScope, this),
                onOpenSpecies = { sid ->
                    navController.navigate(Routes.speciesDetail(sid))
                },
                onOpenFlower = { nearbyFlowerId ->
                    navController.navigate(Routes.detail(nearbyFlowerId))
                },
                onOpenProfile = { uid ->
                    navController.navigate(Routes.friendProfile(uid))
                },
                onDeleted = { deletedId ->
                    // Dépose l'id soft-supprimé pour l'écran d'où l'on vient (la
                    // galerie l'exploite pour l'annulation) puis dépile. Si le
                    // détail a été ouvert ailleurs (carte, album…), l'écran cible
                    // ignore la clé : la fleur reste supprimée, sans snackbar.
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(Routes.KEY_DELETED_FLOWER, deletedId)
                    navController.popBackStack()
                },
            )
        }
        composable(
            route = Routes.SPECIES_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: return@composable
            SpeciesDetailScreen(
                speciesId = id,
                onBack = { navController.popBackStack() },
                onFlowerClick = { fid -> navController.navigate(Routes.detail(fid)) },
            )
        }
            }
        }
    }
}

/** Déclenche [onSuccess] une fois l'authentification réussie. */
@Composable
private fun OnAuthSuccess(state: AuthUiState, onSuccess: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(state) {
        if (state is AuthUiState.Success) onSuccess()
    }
}

/**
 * Route vers le contenu concerné par un tap sur notification (TÂCHE 2.2).
 *
 * Le payload ne porte que le serverId (UUID) de la fleur, jamais l'id local Room.
 * Deux chemins distincts :
 *  - fleur présente en local (mes fleurs : cœur, commentaire, proposition…) →
 *    on résout serverId → id local et on ouvre son détail ;
 *  - fleur d'ami (partage, demande d'identification), absente de Room → on ouvre
 *    le feed « Partagées », seul endroit qui sait afficher le contenu distant.
 *
 * Sans fleur (demande/acceptation d'ami), on route par type. Tout le reste
 * retombe sur l'Accueil.
 */
private suspend fun routeFromNotification(
    navController: NavHostController,
    context: android.content.Context,
    target: NotificationTarget,
    homeRoute: String,
) {
    val serverId = target.flowerServerId
    if (serverId != null) {
        val local = withContext(Dispatchers.IO) {
            FloraDatabase.getInstance(context).flowerDao().findByServerId(serverId)
        }
        if (local != null) {
            // launchSingleTop : un re-tap alors qu'on est déjà sur ce détail ne
            // ré-empile pas l'écran.
            navController.navigate(Routes.detail(local.id)) { launchSingleTop = true }
        } else {
            // Fleur d'ami : elle vit dans le feed, pas dans Room.
            navController.navigateToTab(Routes.FEED)
        }
        return
    }
    when (target.type) {
        "group_invited" -> target.groupId?.let { groupId ->
            navController.navigate(Routes.albumInvitation(groupId)) {
                launchSingleTop = true
            }
        } ?: navController.navigateToTab(Routes.ALBUMS)
        "friend_request", "friend_accepted" ->
            navController.navigate(Routes.FRIENDS) { launchSingleTop = true }
        // Contenu d'ami sans fleur ciblée (partage 'all'/'album'…) → feed.
        "flower_shared", "identification_requested" ->
            navController.navigateToTab(Routes.FEED)
        else -> navController.navigateToTab(homeRoute)
    }
}

/** Amorce la synchronisation et enregistre le push après authentification. */
private fun startSync(context: android.content.Context) {
    SyncScheduler.schedulePeriodic(context)
    SyncScheduler.syncNow(context)
    PushTokenRegistrar.register(context)
}

/**
 * Navigue vers un onglet racine façon bottom navigation : remonte jusqu'au point
 * de départ du graphe en sauvegardant l'état, évite d'empiler le même onglet et
 * restaure son état précédent (position de scroll, etc.).
 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Va à l'écran « Options réseau » après connexion, en vidant la back-stack : le
 * retour hardware ne doit pas ramener à l'écran de login.
 */
private fun NavHostController.goToNetworkOptions() {
    navigate(Routes.NETWORK_OPTIONS) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

/** Va à la galerie en vidant toute la back-stack (sortie du flux d'auth). */
private fun NavHostController.goToGallery() {
    navigate(Routes.GALLERY) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

/** Retour à Login en vidant toute la back-stack (déconnexion). */
private fun NavHostController.goToLogin() {
    navigate(Routes.LOGIN) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}
