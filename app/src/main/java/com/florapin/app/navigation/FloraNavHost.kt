package com.florapin.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.florapin.app.auth.AuthUiState
import com.florapin.app.auth.AuthViewModel
import com.florapin.app.auth.LoginScreen
import com.florapin.app.auth.RegisterScreen
import com.florapin.app.capture.CaptureFlow
import com.florapin.app.detail.DetailScreen
import com.florapin.app.friends.FriendsScreen
import com.florapin.app.gallery.GalleryScreen
import com.florapin.app.map.MapScreen
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.push.PushTokenRegistrar
import com.florapin.app.sync.SyncScheduler

/** Destinations de l'application. */
private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val GALLERY = "gallery"
    const val CAPTURE = "capture"
    const val MAP = "map"
    const val FRIENDS = "friends"
    const val DETAIL = "detail/{id}"

    fun detail(id: Long) = "detail/$id"
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

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
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
                onOpenMap = { navController.navigate(Routes.MAP) },
                onOpenFriends = { navController.navigate(Routes.FRIENDS) },
            )
        }
        composable(Routes.FRIENDS) {
            val authViewModel: AuthViewModel =
                viewModel(factory = AuthViewModel.factory(context))
            FriendsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    // Désenregistre le push tant que les jetons sont valides,
                    // puis déconnecte.
                    PushTokenRegistrar.unregister(context)
                    authViewModel.logout {
                        SyncScheduler.cancelAll(context)
                        navController.goToLogin()
                    }
                },
            )
        }
        composable(Routes.CAPTURE) {
            CaptureFlow(onFinished = { navController.popBackStack() })
        }
        composable(Routes.MAP) {
            MapScreen(
                onBack = { navController.popBackStack() },
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
            )
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
