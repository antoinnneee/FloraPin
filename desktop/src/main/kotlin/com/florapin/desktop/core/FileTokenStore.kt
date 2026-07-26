package com.florapin.desktop.core

import com.florapin.app.network.auth.TokenStore
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.util.Properties

/**
 * Jetons de session persistés sur disque.
 *
 * L'app Android utilise `EncryptedSharedPreferences`, adossé au keystore
 * matériel. Windows n'offre pas d'équivalent accessible depuis la JVM sans
 * dépendance native (DPAPI passerait par JNA) : chiffrer avec une clé
 * elle-même stockée à côté ne protégerait de rien tout en le laissant croire.
 * On s'en tient donc à ce que font les outils de développement usuels — un
 * fichier en clair dans le profil utilisateur — mais avec une ACL réduite au
 * seul compte courant, de sorte qu'un autre utilisateur de la machine ne
 * puisse pas le lire.
 *
 * La session est de toute façon révocable : « Se déconnecter » invalide le
 * refresh token côté serveur.
 */
class FileTokenStore(
    private val file: File = File(DesktopConfig.dataDir, "session.properties"),
) : TokenStore {

    private val props = Properties()

    init {
        if (file.isFile) {
            runCatching { file.inputStream().use { props.load(it) } }
        }
    }

    override fun accessToken(): String? = props.getProperty(KEY_ACCESS)?.ifBlank { null }

    override fun refreshToken(): String? = props.getProperty(KEY_REFRESH)?.ifBlank { null }

    override fun save(accessToken: String, refreshToken: String) {
        props.setProperty(KEY_ACCESS, accessToken)
        props.setProperty(KEY_REFRESH, refreshToken)
        persist()
    }

    override fun clear() {
        props.clear()
        runCatching { file.delete() }
    }

    override fun userId(): String? = props.getProperty(KEY_USER_ID)?.ifBlank { null }

    override fun saveUserId(userId: String) {
        props.setProperty(KEY_USER_ID, userId)
        persist()
    }

    override fun displayName(): String? = props.getProperty(KEY_DISPLAY_NAME)?.ifBlank { null }

    override fun saveDisplayName(displayName: String) {
        props.setProperty(KEY_DISPLAY_NAME, displayName)
        persist()
    }

    /** Email du dernier compte connecté, pré-rempli à l'ouverture. */
    fun lastEmail(): String? = props.getProperty(KEY_EMAIL)?.ifBlank { null }

    fun saveLastEmail(email: String) {
        props.setProperty(KEY_EMAIL, email)
        persist()
    }

    @Synchronized
    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            val fresh = !file.exists()
            file.outputStream().use { props.store(it, "FloraPin — session (ne pas partager)") }
            if (fresh) restrictToOwner(file)
        }
    }

    private companion object {
        const val KEY_ACCESS = "accessToken"
        const val KEY_REFRESH = "refreshToken"
        const val KEY_USER_ID = "userId"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_EMAIL = "email"

        /**
         * Remplace l'ACL héritée par une entrée unique donnant tous les droits
         * au propriétaire du fichier. Best effort : sur un système de fichiers
         * sans ACL (montage réseau, exécution hors Windows) la vue est absente
         * et l'on garde simplement les droits par défaut.
         */
        fun restrictToOwner(file: File) {
            runCatching {
                val path = file.toPath()
                val view = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
                    ?: return
                val owner = Files.getOwner(path)
                val entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(AclEntryPermission.values().toSet())
                    .build()
                view.acl = listOf(entry)
            }
        }
    }
}
