package com.florapin.app.friends

/**
 * Encodage/décodage du contenu des QR codes d'ajout d'ami (TÂCHE 4.5).
 *
 * On encode l'**identifiant** utilisateur (UUID), jamais l'email : le QR ne doit
 * pas divulguer d'information personnelle. Le préfixe permet de distinguer nos
 * codes d'un QR quelconque scanné par erreur.
 */
object FriendQrCodec {

    /** Préfixe des charges utiles FloraPin (schéma privé, aucun lien web requis). */
    private const val PREFIX = "florapin:friend:"

    /** UUID v4 canonique (8-4-4-4-12 hexadécimal). */
    private val UUID_REGEX =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /** Contenu à afficher dans le QR pour l'utilisateur [userId]. */
    fun encode(userId: String): String = PREFIX + userId

    /**
     * Extrait l'id utilisateur d'un contenu scanné, ou `null` si le QR n'est pas
     * un code d'ami FloraPin valide (préfixe absent ou UUID malformé).
     */
    fun decode(payload: String): String? {
        val trimmed = payload.trim()
        if (!trimmed.startsWith(PREFIX)) return null
        val id = trimmed.removePrefix(PREFIX)
        return if (UUID_REGEX.matches(id)) id else null
    }
}
