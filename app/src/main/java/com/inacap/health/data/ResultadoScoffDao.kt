package com.inacap.health.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ResultadoScoffDao {
    @Upsert
    suspend fun upsert(resultado: ResultadoScoff)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(resultado: ResultadoScoff)

    @Query("SELECT * FROM resultado_scoff ORDER BY createdAt DESC")
    fun getAll(): Flow<List<ResultadoScoff>>

    @Query("SELECT * FROM resultado_scoff ORDER BY createdAt ASC")
    suspend fun getAllUnaVez(): List<ResultadoScoff>

    @Query("SELECT * FROM resultado_scoff WHERE uuid = :uuid LIMIT 1")
    suspend fun getPorUuid(uuid: String): ResultadoScoff?

    @Query("DELETE FROM resultado_scoff")
    suspend fun deleteAll()
}
