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
    entities = [
        FlowerEntity::class,
        AlbumEntity::class,
        FlowerAlbumCrossRef::class,
        PhotoEntity::class,
    ],
    version = 13,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class FloraDatabase : RoomDatabase() {

    abstract fun flowerDao(): FlowerDao

    abstract fun albumDao(): AlbumDao

    abstract fun photoDao(): PhotoDao

    companion object {
        /** Nom du fichier SQLite local (aussi utilisé pour détecter un install existant). */
        const val DB_NAME = "florapin.db"

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

        /** v6 → v7 : photos additionnelles par fleur (NODE-107). */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS flower_photos (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "flowerLocalId INTEGER NOT NULL, serverId TEXT, " +
                        "imagePath TEXT NOT NULL DEFAULT '', remoteUrl TEXT, " +
                        "position INTEGER NOT NULL DEFAULT 0, " +
                        "isCover INTEGER NOT NULL DEFAULT 0, " +
                        "syncState TEXT NOT NULL DEFAULT 'PENDING', deletedAt INTEGER)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_flower_photos_flowerLocalId " +
                        "ON flower_photos(flowerLocalId)",
                )
            }
        }

        /** v7 → v8 : rattachement au référentiel d'espèces (NODE-128). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flowers ADD COLUMN speciesId TEXT")
                db.execSQL(
                    "ALTER TABLE flowers ADD COLUMN speciesScientificName TEXT",
                )
                db.execSQL("ALTER TABLE flowers ADD COLUMN speciesCommonName TEXT")
            }
        }

        /** v8 → v9 : visibilité + diffusion GPS au flux d'amis (NODE-137). */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE flowers ADD COLUMN visibility TEXT NOT NULL " +
                        "DEFAULT 'private'",
                )
                db.execSQL(
                    "ALTER TABLE flowers ADD COLUMN feedIncludeGps INTEGER " +
                        "NOT NULL DEFAULT 1",
                )
            }
        }

        /**
         * v9 → v10 : anti-doublon de synchronisation. Nettoie les fleurs
         * « grises » dupliquées (re-tirées du serveur sans image locale alors
         * qu'une capture locale existait) puis impose l'unicité du serverId.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Supprime les lignes sans image qui font doublon (même date de
                //    capture) avec une fleur possédant bien son fichier local.
                db.execSQL(
                    "DELETE FROM flowers WHERE imagePath = '' AND EXISTS (" +
                        "SELECT 1 FROM flowers AS twin WHERE twin.id <> flowers.id " +
                        "AND twin.createdAt = flowers.createdAt " +
                        "AND twin.imagePath <> '' AND twin.deletedAt IS NULL)",
                )
                // 2. Dédoublonne ce qui partagerait encore un même serverId
                //    (on conserve le plus ancien) avant de poser l'index unique.
                db.execSQL(
                    "DELETE FROM flowers WHERE serverId IS NOT NULL AND id NOT IN (" +
                        "SELECT MIN(id) FROM flowers WHERE serverId IS NOT NULL " +
                        "GROUP BY serverId)",
                )
                // 3. Garantit qu'un serverId ne désigne qu'une seule ligne.
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_flowers_serverId " +
                        "ON flowers(serverId)",
                )
            }
        }

        /**
         * v10 → v11 : URL de miniature WebP distante (preview en galerie/feed),
         * pour fleurs et photos additionnelles.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flowers ADD COLUMN remoteThumbnailUrl TEXT")
                db.execSQL(
                    "ALTER TABLE flower_photos ADD COLUMN remoteThumbnailUrl TEXT",
                )
            }
        }

        /**
         * v11 → v12 : anti-doublon des albums. Ajoute un `clientId` (UUID stable
         * généré localement) envoyé au serveur pour rendre la création
         * idempotente (cf. AlbumSyncEngine / backend). On backfille chaque album
         * existant avec un UUID DISTINCT avant de poser l'index unique (deux ''
         * violeraient l'unicité). Les albums déjà synchronisés passent par le
         * chemin rename (serverId != null), donc ces clientId rétro-actifs ne
         * sont jamais envoyés en création — leur format importe peu.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE albums ADD COLUMN clientId TEXT NOT NULL " +
                        "DEFAULT ''",
                )
                // UUID v4-like par ligne (randomblob volatile → distinct par row).
                db.execSQL(
                    "UPDATE albums SET clientId = lower(" +
                        "substr(hex(randomblob(4)),1,8) || '-' || " +
                        "substr(hex(randomblob(2)),1,4) || '-' || " +
                        "substr(hex(randomblob(2)),1,4) || '-' || " +
                        "substr(hex(randomblob(2)),1,4) || '-' || " +
                        "substr(hex(randomblob(6)),1,12)) WHERE clientId = ''",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_albums_clientId " +
                        "ON albums(clientId)",
                )
            }
        }

        /**
         * v12 → v13 : suivi des uploads d'image en souffrance (I9). Quand
         * l'upload échoue APRÈS la création serveur (markSynced déjà passé),
         * on pose ce marqueur pour retenter l'envoi aux syncs suivantes au
         * lieu de perdre l'image.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE flowers ADD COLUMN imagePendingUpload " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE flower_photos ADD COLUMN imagePendingUpload " +
                        "INTEGER NOT NULL DEFAULT 0",
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
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
            ).build()
    }
}
