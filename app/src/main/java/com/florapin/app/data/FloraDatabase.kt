package com.florapin.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base Room locale de FloraPin.
 *
 * `exportSchema = false` : pas de suivi de schéma versionné pour le POC.
 */
@Database(
    entities = [FlowerEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class FloraDatabase : RoomDatabase() {

    abstract fun flowerDao(): FlowerDao

    companion object {
        private const val DB_NAME = "florapin.db"

        /** v1 → v2 : champs de synchronisation (NODE-43). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flowers ADD COLUMN serverId TEXT")
                db.execSQL(
                    "ALTER TABLE flowers ADD COLUMN syncState TEXT NOT NULL " +
                        "DEFAULT 'PENDING'",
                )
                db.execSQL(
                    "ALTER TABLE flowers ADD COLUMN updatedAt INTEGER NOT NULL " +
                        "DEFAULT 0",
                )
                db.execSQL("ALTER TABLE flowers ADD COLUMN deletedAt INTEGER")
                // Initialise updatedAt sur la date de capture existante.
                db.execSQL("UPDATE flowers SET updatedAt = createdAt")
            }
        }

        /** v2 → v3 : URL image distante pour les fleurs multi-appareils (NODE-53). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flowers ADD COLUMN remoteImageUrl TEXT")
            }
        }

        @Volatile
        private var instance: FloraDatabase? = null

        /** Instance unique (créée à la demande, thread-safe). */
        fun getInstance(context: Context): FloraDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): FloraDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FloraDatabase::class.java,
                DB_NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
