package com.florapin.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base Room locale de FloraPin.
 *
 * `exportSchema = false` : pas de suivi de schéma versionné pour le POC.
 */
@Database(
    entities = [FlowerEntity::class, AlbumEntity::class, FlowerAlbumCrossRef::class],
    version = 6,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class FloraDatabase : RoomDatabase() {

    abstract fun flowerDao(): FlowerDao

    abstract fun albumDao(): AlbumDao

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

        /** v3 → v4 : espèce + étiquettes locales (NODE-55). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flowers ADD COLUMN species TEXT")
                db.execSQL(
                    "ALTER TABLE flowers ADD COLUMN tags TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        /** v4 → v5 : propriétaire serveur, pour le filtre « ami » (NODE-54). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flowers ADD COLUMN ownerId TEXT")
            }
        }

        /** v5 → v6 : albums locaux + appartenances (NODE-102). */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS albums (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "serverId TEXT, name TEXT NOT NULL, ownerId TEXT, " +
                        "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                        "syncState TEXT NOT NULL DEFAULT 'PENDING', deletedAt INTEGER)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS flower_album_cross_ref (" +
                        "albumId INTEGER NOT NULL, flowerId INTEGER NOT NULL, " +
                        "PRIMARY KEY(albumId, flowerId))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "index_flower_album_cross_ref_flowerId " +
                        "ON flower_album_cross_ref(flowerId)",
                )
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
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
            ).build()
    }
}
