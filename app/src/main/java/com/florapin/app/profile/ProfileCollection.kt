package com.florapin.app.profile

import android.content.Context
import com.florapin.app.data.BadgeRepository
import com.florapin.app.data.FloraDatabase
import com.florapin.app.data.thumbnailModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aperçu d'une fleur récente pour l'onglet ① Profil (TÂCHE 5.1) : identifiant
 * local Room (pour ouvrir le détail), source d'image Coil et libellé d'espèce.
 */
data class RecentFlower(
    val id: Long,
    /** Modèle Coil (fichier local ou URL distante), cf. [thumbnailModel]. */
    val thumbnailModel: Any?,
    /** Nom d'espèce affichable, ou `null` (aperçu sans texte). */
    val label: String?,
)

/**
 * Passerelle « collection locale » du profil (TÂCHE 5.1) : alimente les deux
 * derniers éléments de l'onglet ① Profil — le **nombre de badges** débloqués et
 * l'aperçu des **dernières fleurs**. 100 % local (device-first, toujours
 * disponible hors-ligne). Isolée derrière une interface pour garder
 * [ProfileViewModel] testable sans Room ni Android.
 */
interface ProfileCollection {

    /** Nombre de paliers de badges « collection » débloqués localement. */
    suspend fun badgeCount(): Int

    /** Les [limit] fleurs actives les plus récentes (aperçu). */
    suspend fun recentFlowers(limit: Int): List<RecentFlower>

    companion object {
        /** Implémentation par défaut inerte (tests / factory absente). */
        val NOOP: ProfileCollection = object : ProfileCollection {
            override suspend fun badgeCount(): Int = 0
            override suspend fun recentFlowers(limit: Int): List<RecentFlower> = emptyList()
        }

        /** Câble la passerelle sur la base Room locale. */
        fun from(context: Context): ProfileCollection =
            AndroidProfileCollection(context.applicationContext)
    }
}

/** Implémentation Android : lit les agrégats de badges et les fleurs récentes en Room. */
private class AndroidProfileCollection(private val context: Context) : ProfileCollection {

    override suspend fun badgeCount(): Int = withContext(Dispatchers.IO) {
        // Recalcule d'abord (idempotent) pour que le compteur reste juste même si
        // l'onglet Badges n'a pas encore été ouvert ; le résolveur de régions est
        // chargé au mieux (dégradation device-first si les assets manquent).
        runCatching { BadgeRepository.from(context).recompute() }
        FloraDatabase.getInstance(context).badgeDao().count()
    }

    override suspend fun recentFlowers(limit: Int): List<RecentFlower> =
        withContext(Dispatchers.IO) {
            FloraDatabase.getInstance(context).flowerDao().recentActive(limit).map { f ->
                RecentFlower(
                    id = f.id,
                    thumbnailModel = f.thumbnailModel(),
                    label = f.speciesCommonName ?: f.speciesScientificName ?: f.species,
                )
            }
        }
}
