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
 * Vérifie l'enchaînement des migrations v1 → v5 (NODE-60).
 *
 * Schéma non exporté (exportSchema=false) : on crée une base v1 brute, on insère
 * une ligne, puis on ouvre via Room avec toutes les migrations et on contrôle
 * que la donnée survit et que les nouvelles colonnes sont exploitables. Room
 * valide en prime que le schéma final correspond aux entités.
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
        } finally {
            db.close()
        }
    }
}
