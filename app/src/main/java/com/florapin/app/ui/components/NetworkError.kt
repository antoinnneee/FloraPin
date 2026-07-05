package com.florapin.app.ui.components

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Mapping commun des erreurs réseau (TÂCHE 6.16).
 *
 * Les ViewModels réseau (feed, détail, amis, auth…) attrapent des [Throwable]
 * bruts — [IOException] côté OkHttp, [HttpException] côté Retrofit — dont le
 * `message` est illisible pour un utilisateur (« Unable to resolve host… »,
 * « HTTP 503 »). Ce fichier centralise leur traduction en français humain et,
 * surtout, distingue deux situations que l'on confondait jusqu'ici :
 *
 * - **Pas de connexion** ([NetworkErrorKind.OFFLINE]) : l'appareil n'a même pas
 *   atteint le réseau (mode avion, Wi-Fi coupé). Typiquement [UnknownHostException]
 *   ou une [IOException] générique.
 * - **Serveur injoignable** ([NetworkErrorKind.SERVER_UNREACHABLE]) : la requête
 *   est partie mais le serveur n'a pas répondu à temps ([SocketTimeoutException])
 *   ou a renvoyé une 5xx.
 *
 * Dans les deux cas un nouvel essai a du sens (bouton « Réessayer »), alors qu'une
 * erreur applicative 4xx ([NetworkErrorKind.CLIENT]) est définitive tant que la
 * requête n'a pas changé.
 */
enum class NetworkErrorKind {
    /** Pas de connexion : mode avion, Wi-Fi/données coupés. */
    OFFLINE,

    /** Le serveur n'a pas répondu à temps ou a renvoyé une 5xx. */
    SERVER_UNREACHABLE,

    /** Erreur applicative (4xx) : la requête a abouti mais a été refusée. */
    CLIENT,

    /** Cause inconnue (exception hors réseau). */
    UNKNOWN,
}

/**
 * Résultat du mapping : un [message] prêt à afficher et la [kind] qui pilote
 * l'illustration et la présence d'un bouton « Réessayer » ([isRetryable]).
 */
data class NetworkErrorInfo(
    val message: String,
    val kind: NetworkErrorKind,
) {
    /**
     * Un nouvel essai a du sens tant que l'échec n'est pas une erreur applicative
     * définitive (4xx). Les coupures réseau, timeouts et 5xx sont réessayables.
     */
    val isRetryable: Boolean
        get() = kind != NetworkErrorKind.CLIENT
}

/**
 * Traduit un [error] en [NetworkErrorInfo] humain.
 *
 * [httpMessage] permet à un appelant de personnaliser le message d'un code HTTP
 * précis (ex. l'auth : 401 → « Identifiants invalides », 409 → « Compte déjà
 * existant »). S'il renvoie une chaîne non nulle, elle est utilisée telle quelle
 * et l'erreur est classée [NetworkErrorKind.CLIENT]. Sinon on retombe sur les
 * messages génériques par code.
 */
fun networkErrorInfo(
    error: Throwable,
    httpMessage: (code: Int) -> String? = { null },
): NetworkErrorInfo = when (error) {
    // Ordre important : ces deux sous-classes d'IOException doivent être testées
    // AVANT le cas IOException générique.
    is SocketTimeoutException -> NetworkErrorInfo(
        "Le serveur met trop de temps à répondre. Réessayez.",
        NetworkErrorKind.SERVER_UNREACHABLE,
    )
    is UnknownHostException -> NetworkErrorInfo(
        "Pas de connexion internet. Vérifiez le Wi-Fi ou le mode avion.",
        NetworkErrorKind.OFFLINE,
    )
    is IOException -> NetworkErrorInfo(
        "Pas de connexion internet. Vérifiez le Wi-Fi ou le mode avion.",
        NetworkErrorKind.OFFLINE,
    )
    is HttpException -> {
        val code = error.code()
        val custom = httpMessage(code)
        when {
            custom != null -> NetworkErrorInfo(custom, NetworkErrorKind.CLIENT)
            code in 500..599 -> NetworkErrorInfo(
                "Le serveur est indisponible. Réessayez dans un instant.",
                NetworkErrorKind.SERVER_UNREACHABLE,
            )
            code == 401 -> NetworkErrorInfo(
                "Session expirée. Reconnectez-vous.",
                NetworkErrorKind.CLIENT,
            )
            code == 403 -> NetworkErrorInfo(
                "Action non autorisée.",
                NetworkErrorKind.CLIENT,
            )
            code == 404 -> NetworkErrorInfo(
                "Contenu introuvable.",
                NetworkErrorKind.CLIENT,
            )
            else -> NetworkErrorInfo(
                "Requête refusée par le serveur ($code).",
                NetworkErrorKind.CLIENT,
            )
        }
    }
    else -> NetworkErrorInfo(
        error.message ?: "Erreur réseau. Réessayez.",
        NetworkErrorKind.UNKNOWN,
    )
}

/**
 * Raccourci pour les ViewModels qui ne stockent qu'un message (`error: String?`) :
 * renvoie uniquement le texte humain. Remplace les anciens `messageOf(e)` locaux.
 */
fun networkErrorMessage(
    error: Throwable,
    httpMessage: (code: Int) -> String? = { null },
): String = networkErrorInfo(error, httpMessage).message
