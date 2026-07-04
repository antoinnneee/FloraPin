package com.florapin.app.permission

import android.Manifest

/**
 * Permissions applicatives, avec libellés et justifications pour l'UI.
 *
 * Note stockage : les photos prises par FloraPin sont écrites dans le répertoire
 * privé de l'app (aucune permission requise). L'app ne lit pas la galerie de
 * l'appareil, donc aucune permission d'accès média n'est demandée.
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
    );

    companion object {
        /** Ensemble des permissions applicatives. */
        val poc: List<AppPermission> = entries
    }
}
