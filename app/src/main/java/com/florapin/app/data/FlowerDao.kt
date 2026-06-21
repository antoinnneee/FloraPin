package com.florapin.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Accès aux fleurs persistées. */
@Dao
interface FlowerDao {

    /** Flux de toutes les fleurs, des plus récentes aux plus anciennes. */
    @Query("SELECT * FROM flowers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FlowerEntity>>

    @Query("SELECT * FROM flowers WHERE id = :id")
    suspend fun getById(id: Long): FlowerEntity?

    /** Insère une fleur et renvoie son identifiant généré. */
    @Insert
    suspend fun insert(flower: FlowerEntity): Long

    @Update
    suspend fun update(flower: FlowerEntity)

    @Delete
    suspend fun delete(flower: FlowerEntity)
}
