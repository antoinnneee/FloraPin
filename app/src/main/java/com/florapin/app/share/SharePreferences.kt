package com.florapin.app.share

import android.content.Context

/** Destinataire présélectionné à l'ouverture de la feuille de partage. */
enum class DefaultRecipient {
    /** Aucun : l'utilisateur choisit à chaque partage. */
    NONE,

    /** Le partage réseau « Tous mes amis » (audience='all_friends'). */
    ALL_FRIENDS,
    ;

    companion object {
        fun fromKey(key: String?): DefaultRecipient =
            entries.firstOrNull { it.name == key } ?: NONE
    }
}

/**
 * Mémoire des destinataires récemment choisis. Extraite en interface pour que
 * [ShareViewModel] reste testable sans `Context`.
 */
interface RecentRecipientsStore {
    fun recentFriendIds(): List<String>

    fun rememberRecentFriend(friendId: String)
}

/**
 * Réglages de partage par appareil : valeurs proposées par défaut dans la
 * feuille de partage, et mémoire des destinataires récemment choisis (pour les
 * remonter en tête du sélecteur d'amis).
 *
 * Renseignés à la première ouverture de l'app (étape d'onboarding) et
 * modifiables ensuite dans Profil › Configuration.
 */
class SharePreferences(context: Context) : RecentRecipientsStore {
    private val prefs =
        context.applicationContext.getSharedPreferences("florapin_share", Context.MODE_PRIVATE)

    /** Joindre la position GPS aux fleurs partagées. */
    fun includeGps(): Boolean = prefs.getBoolean(KEY_INCLUDE_GPS, DEFAULT_INCLUDE_GPS)

    fun setIncludeGps(include: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_GPS, include).apply()
    }

    fun defaultRecipient(): DefaultRecipient =
        DefaultRecipient.fromKey(prefs.getString(KEY_RECIPIENT, null))

    fun setDefaultRecipient(recipient: DefaultRecipient) {
        prefs.edit().putString(KEY_RECIPIENT, recipient.name).apply()
    }

    /**
     * Partage automatique : chaque nouvelle fleur est partagée avec le
     * destinataire par défaut, sans passer par la feuille. Sans effet tant que
     * [defaultRecipient] vaut [DefaultRecipient.NONE] — il n'y aurait personne
     * à qui partager.
     */
    fun autoShare(): Boolean =
        prefs.getBoolean(KEY_AUTO_SHARE, DEFAULT_AUTO_SHARE) &&
            defaultRecipient() != DefaultRecipient.NONE

    fun setAutoShare(auto: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SHARE, auto).apply()
    }

    /**
     * Ids des amis récemment choisis comme destinataires, du plus récent au plus
     * ancien, au plus [RECENT_LIMIT] entrées.
     */
    override fun recentFriendIds(): List<String> =
        prefs.getString(KEY_RECENTS, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    /**
     * Remonte [friendId] en tête des destinataires récents (déduplication), et
     * tronque à [RECENT_LIMIT].
     */
    override fun rememberRecentFriend(friendId: String) {
        if (friendId.isBlank()) return
        val updated = (listOf(friendId) + recentFriendIds().filterNot { it == friendId })
            .take(RECENT_LIMIT)
        prefs.edit().putString(KEY_RECENTS, updated.joinToString(SEPARATOR)).apply()
    }

    /**
     * Grave les valeurs courantes (défauts compris) à la fin de l'étape
     * d'onboarding : sans cela, un utilisateur qui ne touche à rien laisserait
     * les clés absentes, et on ne saurait pas distinguer « accepté par défaut »
     * de « jamais demandé ».
     */
    fun markSetupDone() {
        prefs.edit()
            .putString(KEY_RECIPIENT, defaultRecipient().name)
            .putBoolean(KEY_INCLUDE_GPS, includeGps())
            .putBoolean(KEY_AUTO_SHARE, autoShare())
            .apply()
    }

    companion object {
        /** Nombre d'amis récents affichés en raccourci dans le sélecteur. */
        const val RECENT_LIMIT = 4

        private const val KEY_INCLUDE_GPS = "include_gps"
        private const val KEY_RECIPIENT = "default_recipient"
        private const val KEY_AUTO_SHARE = "auto_share"
        private const val KEY_RECENTS = "recent_friend_ids"

        /** Un id d'ami est un UUID : la virgule n'y apparaît jamais. */
        private const val SEPARATOR = ","

        private const val DEFAULT_INCLUDE_GPS = true
        private const val DEFAULT_AUTO_SHARE = false
    }
}
