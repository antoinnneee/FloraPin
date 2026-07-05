package com.florapin.app.location

/**
 * Disponibilité de la position GPS **pendant la visée**, sondée en continu tant
 * que l'aperçu caméra est affiché. Sert à prévenir l'utilisateur *avant* la
 * prise de vue si le système n'a pas (encore) de position à associer à la fleur.
 */
sealed interface GpsFixState {
    /** Recherche en cours : aucune position encore obtenue (état initial). */
    data object Searching : GpsFixState

    /** Position disponible, prête à être associée à la prochaine capture. */
    data class Fixed(val point: GeoPoint) : GpsFixState

    /**
     * Aucune position disponible (permission refusée, GPS coupé ou fix
     * impossible) : la photo sera enregistrée sans localisation.
     */
    data object Unavailable : GpsFixState
}
