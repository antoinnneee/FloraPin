package com.florapin.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.florapin.app.R

/**
 * État plein écran réutilisable pour une erreur réseau (TÂCHE 6.16).
 *
 * S'appuie sur [EmptyState] pour l'illustration botanique cohérente avec le reste
 * de l'app, mais adapte le titre à la nature de l'erreur ([NetworkErrorKind]) et
 * n'affiche le bouton « Réessayer » que lorsqu'un nouvel essai a du sens
 * ([NetworkErrorInfo.isRetryable]) et qu'un [onRetry] est fourni. Ainsi l'écran
 * distingue visuellement « pas de connexion » (mode avion) de « serveur
 * injoignable » (timeout/5xx), sans jamais proposer de réessayer une erreur
 * applicative définitive (4xx).
 */
@Composable
fun NetworkErrorState(
    info: NetworkErrorInfo,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val title = when (info.kind) {
        NetworkErrorKind.OFFLINE -> "Pas de connexion"
        NetworkErrorKind.SERVER_UNREACHABLE -> "Serveur injoignable"
        else -> "Oups"
    }
    val canRetry = info.isRetryable && onRetry != null
    EmptyState(
        title = title,
        message = info.message,
        modifier = modifier,
        iconRes = R.drawable.ic_flower_botanical,
        actionLabel = if (canRetry) "Réessayer" else null,
        onAction = if (canRetry) onRetry else null,
    )
}
