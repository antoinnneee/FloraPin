package com.florapin.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.florapin.app.albums.AlbumsScreen
import com.florapin.app.auth.EmailVerifyViewModel
import com.florapin.app.auth.ForgotPasswordScreen
import com.florapin.app.auth.LoginScreen
import com.florapin.app.auth.PasswordResetViewModel
import com.florapin.app.auth.RegisterScreen
import com.florapin.app.auth.ResetPasswordScreen
import com.florapin.app.auth.VerifyEmailScreen
import com.florapin.app.capture.CaptureFlow
import com.florapin.app.detail.DetailScreen
import com.florapin.app.detail.SpeciesDetailScreen
import com.florapin.app.feed.SharedFeedScreen
import com.florapin.app.friends.FriendsScreen
import com.florapin.app.gallery.GalleryScreen
import com.florapin.app.identify.IdentifyScreen
import com.florapin.app.map.MapScreen
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.profile.ProfileScreen
import com.florapin.app.push.PushTokenRegistrar
import com.florapin.app.sync.SyncScheduler

/** Destinations de l'application. */
private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot-password"
    const val RESET_PASSWORD = "reset-password?token={token}"
    const val VERIFY_EMAIL = "verify-email?token={token}"
    const val GALLERY = "gallery"
    const val CAPTURE = "capture"
    const val MAP = "map"
    const val FRIENDS = "friends"
    const val FEED = "feed"
    const val PROFILE = "profile"
    const val ALBUMS = "albums"
    const val ALBUM_DETAIL = "album/{id}"
    const val DETAIL = "detail/{id}"
    const val SPECIES_DETAIL = "species/{id}"
    const val IDENTIFY = "identify"

    fun detail(id: Long) = "detail/$id"
    fun album(id: Long) = "album/$id"
    fun speciesDetail(id: String) = "species/$id"
}

/**
 * Graphe de navigation principal avec garde d'authentification : démarre sur
 * Login si l'utilisateur n'est pas connecté, sinon sur la galerie.
 */
@Composable
fun FloraNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val loggedIn = remember {
        EncryptedTokenStore(context).refreshToken() != null
    }
    val startDestination = if (loggedIn) Routes.GALLERY else Routes.LOGIN

    val currentRoute by navController.currentBackStackEntryAsState()
    val showBottomBar = currentRoute?.destination?.route in topLevelRoutes

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                FloraBottomBar(
                    currentRoute = currentRoute?.destination?.route,
                    onSelect = { destination -> navController.navigateToTab(destination.route) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
        composable(Routes.LOGIN) {
            val authViewModel: AuthViewModel =
                viewModel(factory = AuthViewModel.factory(context))
            val state by authViewModel.state.collectAsStateWithLifecycle()
            OnAuthSuccess(state) {
                startSync(context)
                navController.goToGallery()
            }

            LoginScreen(
                isLoading = state is AuthUiState.Loading,
                error = (state as? AuthUiState.Error)?.message,
                onLogin = authViewModel::login,
                onSwitchToRegister = { navController.navigate(Routes.REGISTER) },
                onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
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
                navDeepLink { uriPattern = "https://florapin.fr/reset?token={token}" },
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
                navDeepLink { uriPattern = "https://florapin.fr/verify?token={token}" },
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
                onRegister = authViewModel::register,
                onSwitchToLogin = { navController.popBackStack() },
            )
        }

        composable(Routes.GALLERY) {
            GalleryScreen(
                onCapture = { navController.navigate(Routes.CAPTURE) },
                onFlowerClick = { id -> navController.navigate(Routes.detail(id)) },
                onOpenFriends = { navController.navigate(Routes.FRIENDS) },
                onOpenAlbums = { navController.navigate(Routes.ALBUMS) },
                onOpenIdentify = { navController.navigate(Routes.IDENTIFY) },
            )
        }
        composable(Routes.IDENTIFY) {
            IdentifyScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ALBUMS) {
            AlbumsScreen(
                onBack = { navController.popBackStack() },
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
            )
        }
        composable(Routes.FEED) {
            SharedFeedScreen()
        }
        composable(Routes.FRIENDS) {
            FriendsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PROFILE) {
            val authViewModel: AuthViewModel =
                viewModel(factory = AuthViewModel.factory(context))
            ProfileScreen(
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
        composable(Routes.CAPTURE) {
            CaptureFlow(onFinished = { navController.popBackStack() })
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
                onOpenSpecies = { sid ->
                    navController.navigate(Routes.speciesDetail(sid))
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

/** Déclenche [onSuccess] une fois l'authentification réussie. */
@Composable
private fun OnAuthSuccess(state: AuthUiState, onSuccess: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(state) {
        if (state is AuthUiState.Success) onSuccess()
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
