package com.florapin.app.profile

import android.content.Context
import android.net.Uri
import com.florapin.app.network.auth.SessionManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Passerelle avatar pour le profil (TÂCHE 5.1) : copie l'image choisie (Uri du
 * sélecteur média) dans un fichier temporaire puis délègue l'upload multipart à
 * [SessionManager]. Isolée derrière une interface pour garder [ProfileViewModel]
 * testable sans Android (contentResolver, fichiers).
 */
interface ProfileAvatar {

    /**
     * Téléverse l'image [source] comme avatar et renvoie la nouvelle URL
     * présignée (ou null si le serveur n'en fournit pas).
     */
    suspend fun upload(source: Uri): String?

    companion object {
        /** Implémentation par défaut inerte (tests / factory absente). */
        val NOOP: ProfileAvatar = object : ProfileAvatar {
            override suspend fun upload(source: Uri): String? =
                throw UnsupportedOperationException("Avatar non disponible")
        }

        /** Câble la passerelle sur le contentResolver et la session authentifiée. */
        fun from(context: Context, session: SessionManager): ProfileAvatar =
            AndroidProfileAvatar(context.applicationContext, session)
    }
}

/** Implémentation Android : matérialise l'Uri en fichier avant l'upload. */
private class AndroidProfileAvatar(
    private val context: Context,
    private val session: SessionManager,
) : ProfileAvatar {

    override suspend fun upload(source: Uri): String? = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("avatar-", ".img", context.cacheDir)
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Image illisible")
            session.uploadAvatar(temp).avatarUrl
        } finally {
            temp.delete()
        }
    }
}
