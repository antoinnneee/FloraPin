package com.florapin.app.permission

import android.Manifest
import android.os.Build

/**
 * Permissions applicatives du POC, avec libellés et justifications pour l'UI.
 *
 * Note stockage : les photos prises par FloraPin sont écrites dans le répertoire
 * privé de l'app (aucune permission requise). [MEDIA_IMAGES] ne sert qu'à LIRE des
 * images existantes de la galerie, et cible la bonne permission selon la version
 * d'Android (READ_MEDIA_IMAGES dès Android 13, sinon READ_EXTERNAL_STORAGE).
 */
enum class AppPermission(
    val manifestPermission: String,
    val label: String,
    val rationale: String,
) {
    CAMERA(
        manifestPermission = Manifest.permission.CAMERA,
        label = "Caméra",
        rationale = "FloraPin utilise la caméra pour photographier vos plantes.",
    ),
    LOCATION(
        manifestPermission = Manifest.permission.ACCESS_FINE_LOCATION,
        label = "Localisation",
        rationale = "La position permet de placer vos observations sur la carte.",
    ),
    MEDIA_IMAGES(
        manifestPermission = mediaImagesPermission(),
        label = "Photos",
        rationale = "L'accès aux photos permet d'importer des images existantes.",
    );

    companion object {
        /** Ensemble des permissions demandées pour le POC. */
        val poc: List<AppPermission> = entries
    }
}

/** Permission de lecture d'images adaptée à la version d'Android. */
private fun mediaImagesPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        @Suppress("DEPRECATION")
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
