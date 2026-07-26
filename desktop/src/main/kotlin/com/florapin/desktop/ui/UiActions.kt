package com.florapin.desktop.ui

/**
 * Actions transverses aux écrans, portées par la coquille applicative.
 *
 * Les mêmes opérations sont déclenchées depuis trois endroits — la barre
 * d'outils, le menu contextuel et un raccourci clavier. Les regrouper ici
 * garantit qu'elles se comportent identiquement partout ; c'est aussi ce qui
 * permet à la fenêtre de router un raccourci sans rien savoir de l'écran
 * affiché.
 */
class UiActions(
    /** Ouvre la visionneuse plein écran sur une photo. */
    val openViewer: (String) -> Unit,
    /** Déploie le menu contextuel sous le curseur pour la sélection courante. */
    val openContextMenu: () -> Unit,
    val exportSelection: () -> Unit,
    val addSelectionToAlbum: () -> Unit,
    val deleteSelection: () -> Unit,
    val requestIdentification: () -> Unit,
    /** Bascule sur la carte et centre sur la photo. */
    val showOnMap: (String) -> Unit,
)
