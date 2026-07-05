package com.florapin.app.profile

import android.content.Context
import android.net.Uri
import com.florapin.app.data.backup.BackupExporter
import com.florapin.app.data.backup.BackupExportResult
import com.florapin.app.data.backup.BackupImportResult
import com.florapin.app.data.backup.BackupImporter

/**
 * Passerelle sauvegarde locale pour le profil (TÂCHE 1.5) : ouvre le flux du
 * document SAF choisi puis délègue à [BackupExporter] / [BackupImporter]. Isolée
 * derrière une interface pour garder [ProfileViewModel] testable sans Android
 * (contentResolver, Room, stockage de fichiers).
 */
interface ProfileBackup {

    /** Écrit l'archive de sauvegarde dans le document [destination]. */
    suspend fun export(destination: Uri): BackupExportResult

    /** Fusionne l'archive [source] dans la base locale (idempotent). */
    suspend fun import(source: Uri): BackupImportResult

    companion object {
        /** Implémentation par défaut inerte (tests / factory absente). */
        val NOOP: ProfileBackup = object : ProfileBackup {
            override suspend fun export(destination: Uri) =
                throw UnsupportedOperationException("Sauvegarde non disponible")
            override suspend fun import(source: Uri) =
                throw UnsupportedOperationException("Restauration non disponible")
        }

        /** Câble la passerelle sur le contentResolver et les composants ZIP. */
        fun from(context: Context): ProfileBackup {
            val app = context.applicationContext
            return AndroidProfileBackup(
                context = app,
                exporter = BackupExporter.from(app),
                importer = BackupImporter.from(app),
            )
        }
    }
}

/** Implémentation Android : ouvre les flux du document via le contentResolver. */
private class AndroidProfileBackup(
    private val context: Context,
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
) : ProfileBackup {

    override suspend fun export(destination: Uri): BackupExportResult =
        context.contentResolver.openOutputStream(destination)?.use { exporter.export(it) }
            ?: error("Flux de sortie indisponible")

    override suspend fun import(source: Uri): BackupImportResult =
        context.contentResolver.openInputStream(source)?.use { importer.import(it) }
            ?: error("Flux d'entrée indisponible")
}
