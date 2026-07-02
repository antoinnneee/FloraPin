package com.florapin.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Vérifie l'enchaînement des migrations (NODE-60).
 *
 * Schéma non exporté (exportSchema=false) : on crée une base brute à une
 * version donnée, on insère une ligne, puis on ouvre via Room avec les
 * migrations et on contrôle que la donnée survit et que les nouvelles colonnes
 * sont exploitables. Room valide en prime que le schéma final correspond aux
 * entités.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    @Before
    fun clean() {
        context.deleteDatabase(dbName)
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migratesFromV1ToLatest_preservingData() {
        // Base v1 brute (schéma généré par Room pour la v1).
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE flowers (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "imagePath TEXT NOT NULL, " +
                                "latitude REAL, longitude REAL, accuracyMeters REAL, " +
                                "createdAt INTEGER NOT NULL, " +
                                "notes TEXT NOT NULL)",
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build(),
        )
        helper.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO flowers (imagePath, createdAt, notes) " +
                    "VALUES ('/p.jpg', 1000, 'bonjour')",
            )
        }

        // Ouverture via Room avec la chaîne complète de migrations.
        val db = Room.databaseBuilder(context, FloraDatabase::class.java, dbName)
            .addMigrations(
                FloraDatabase.MIGRATION_1_2,
                FloraDatabase.MIGRATION_2_3,
                FloraDatabase.MIGRATION_3_4,
                FloraDatabase.MIGRATION_4_5,
                FloraDatabase.MIGRATION_5_6,
                FloraDatabase.MIGRATION_6_7,
                FloraDatabase.MIGRATION_7_8,
                FloraDatabase.MIGRATION_8_9,
                FloraDatabase.MIGRATION_9_10,
                FloraDatabase.MIGRATION_10_11,
                FloraDatabase.MIGRATION_11_12,
                FloraDatabase.MIGRATION_12_13,
            )
            .build()

        try {
            val flower = runBlocking { db.flowerDao().getById(1) }
            assertNotNull(flower)
            assertEquals("bonjour", flower!!.notes)
            // Colonnes ajoutées par les migrations, valeurs par défaut cohérentes.
            assertEquals(SyncState.PENDING.name, flower.syncState)
            assertEquals(emptyList<String>(), flower.tags)
            assertEquals(null, flower.species)
            assertEquals(null, flower.ownerId)
            assertEquals(null, flower.remoteImageUrl)
            assertEquals(false, flower.imagePendingUpload)
        } finally {
            db.close()
        }
    }

    @Test
    fun migratesFromV12ToV13_addsImagePendingUpload() {
        // Base v12 brute : schéma tel que produit par la chaîne de migrations
        // jusqu'à la v12 (fleurs, albums, appartenances, photos).
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE flowers (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "imagePath TEXT NOT NULL, " +
                                "latitude REAL, longitude REAL, accuracyMeters REAL, " +
                                "createdAt INTEGER NOT NULL, " +
                                "notes TEXT NOT NULL, " +
                                "serverId TEXT, " +
                                "syncState TEXT NOT NULL DEFAULT 'PENDING', " +
                                "updatedAt INTEGER NOT NULL DEFAULT 0, " +
                                "deletedAt INTEGER, " +
                                "remoteImageUrl TEXT, " +
                                "species TEXT, " +
                                "tags TEXT NOT NULL DEFAULT '', " +
                                "ownerId TEXT, " +
                                "speciesId TEXT, " +
                                "speciesScientificName TEXT, " +
                                "speciesCommonName TEXT, " +
                                "visibility TEXT NOT NULL DEFAULT 'private', " +
                                "feedIncludeGps INTEGER NOT NULL DEFAULT 1, " +
                                "remoteThumbnailUrl TEXT)",
                        )
                        db.execSQL(
                            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                                "index_flowers_serverId ON flowers(serverId)",
                        )
                        db.execSQL(
                            "CREATE TABLE albums (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "serverId TEXT, name TEXT NOT NULL, ownerId TEXT, " +
                                "createdAt INTEGER NOT NULL, " +
                                "updatedAt INTEGER NOT NULL, " +
                                "syncState TEXT NOT NULL DEFAULT 'PENDING', " +
                                "deletedAt INTEGER, " +
                                "clientId TEXT NOT NULL DEFAULT '')",
                        )
                        db.execSQL(
                            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                                "index_albums_clientId ON albums(clientId)",
                        )
                        db.execSQL(
                            "CREATE TABLE flower_album_cross_ref (" +
                                "albumId INTEGER NOT NULL, " +
                                "flowerId INTEGER NOT NULL, " +
                                "PRIMARY KEY(albumId, flowerId))",
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS " +
                                "index_flower_album_cross_ref_flowerId " +
                                "ON flower_album_cross_ref(flowerId)",
                        )
                        db.execSQL(
                            "CREATE TABLE flower_photos (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "flowerLocalId INTEGER NOT NULL, serverId TEXT, " +
                                "imagePath TEXT NOT NULL DEFAULT '', " +
                                "remoteUrl TEXT, " +
                                "position INTEGER NOT NULL DEFAULT 0, " +
                                "isCover INTEGER NOT NULL DEFAULT 0, " +
                                "syncState TEXT NOT NULL DEFAULT 'PENDING', " +
                                "deletedAt INTEGER, " +
                                "remoteThumbnailUrl TEXT)",
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS " +
                                "index_flower_photos_flowerLocalId " +
                                "ON flower_photos(flowerLocalId)",
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build(),
        )
        helper.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO flowers (imagePath, createdAt, notes, serverId, " +
                    "syncState) VALUES ('/p.jpg', 1000, 'v12', 'srv-1', 'SYNCED')",
            )
            db.execSQL(
                "INSERT INTO flower_photos (flowerLocalId, serverId, syncState) " +
                    "VALUES (1, 'srv-photo-1', 'SYNCED')",
            )
        }

        val db = Room.databaseBuilder(context, FloraDatabase::class.java, dbName)
            .addMigrations(FloraDatabase.MIGRATION_12_13)
            .build()

        try {
            runBlocking {
                val flower = db.flowerDao().getById(1)
                assertNotNull(flower)
                assertEquals("v12", flower!!.notes)
                // Nouvelle colonne : aucune image en souffrance par défaut.
                assertEquals(false, flower.imagePendingUpload)
                assertEquals(0, db.flowerDao().pendingImageUploads().size)

                val photo = db.photoDao().getById(1)
                assertNotNull(photo)
                assertEquals(false, photo!!.imagePendingUpload)
                assertEquals(0, db.photoDao().pendingImageUploads().size)
            }
        } finally {
            db.close()
        }
    }
}
