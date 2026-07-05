package com.florapin.app.notifications

import android.content.Context
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.NotificationsApi
import com.florapin.app.network.auth.EncryptedTokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Cloche du centre de notifications (TÂCHE 2.7), à poser dans les `actions` d'une
 * top bar. Affiche un badge quand des notifications sont non lues et ouvre le
 * centre au clic. Le compteur est rafraîchi à chaque (ré)affichage de l'écran
 * hôte — y compris au retour depuis le centre, où l'on vient d'en lire.
 *
 * Device-first : hors-ligne / non connecté, le comptage échoue silencieusement
 * (badge masqué) ; la cloche reste cliquable et le centre assume l'indisponibilité.
 */
@Composable
fun NotificationBell(
    onOpen: () -> Unit,
    viewModel: NotificationBellViewModel = viewModel(
        factory = NotificationBellViewModel.factory(LocalContext.current),
    ),
) {
    val unread by viewModel.unreadCount.collectAsStateWithLifecycle()

    // Recompté à l'affichage de l'écran hôte (arrivée + retour depuis le centre).
    LaunchedEffect(Unit) { viewModel.refresh() }

    BadgedBox(
        badge = {
            if (unread > 0) {
                Badge { Text(if (unread > 99) "99+" else "$unread") }
            }
        },
    ) {
        IconButton(onClick = onOpen) {
            Text("🔔", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Fournit le compteur de notifications non lues pour la cloche. */
class NotificationBellViewModel(
    private val api: NotificationsApi,
) : ViewModel() {

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    /** Recharge le compteur ; laisse la dernière valeur connue en cas d'échec. */
    fun refresh() {
        viewModelScope.launch {
            runCatching { api.unreadCount().count }
                .onSuccess { _unreadCount.value = it }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tokenStore = EncryptedTokenStore(context.applicationContext)
                    val apis = NetworkModule.createAuthenticated(tokenStore)
                    return NotificationBellViewModel(apis.notifications) as T
                }
            }
    }
}
