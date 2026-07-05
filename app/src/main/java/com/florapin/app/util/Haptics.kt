package com.florapin.app.util

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Retours haptiques sémantiques (QOL 6.15). Centralise les types de vibration
 * utilisés dans l'app pour rester cohérent d'un point d'appel à l'autre (like,
 * obturateur, déblocage de badge). S'appuie sur le [HapticFeedback] de Compose
 * (récupéré via `LocalHapticFeedback.current`), donc respecte le réglage système
 * de retour haptique et ne nécessite aucune permission.
 */
object Haptics {

    /**
     * Confirmation légère d'une action ponctuelle réussie (ex. like/réaction,
     * déclenchement de l'obturateur). Vibration brève et discrète.
     */
    fun tap(haptic: HapticFeedback) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /**
     * Retour plus marqué pour un événement notable à célébrer (ex. déblocage
     * d'un palier de badge). Vibration appuyée type « appui long ».
     */
    fun celebrate(haptic: HapticFeedback) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}
