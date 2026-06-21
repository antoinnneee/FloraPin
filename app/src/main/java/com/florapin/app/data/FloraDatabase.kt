package com.florapin.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base Room locale de FloraPin.
 *
 * `exportSchema = false` : pas de suivi de schéma versionné pour le POC.
 */
@Database(
    entities = [FlowerEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FloraDatabase : RoomDatabase() {

    abstract fun flowerDao(): FlowerDao

    companion object {
        private const val DB_NAME = "florapin.db"

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
            ).build()
    }
}
