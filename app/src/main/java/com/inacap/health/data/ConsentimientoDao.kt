package com.inacap.health.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsentimientoDao {
    @Insert
    suspend fun insert(consentimiento: Consentimiento)

    @Query("SELECT * FROM consentimiento ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Consentimiento>>

    @Query("SELECT * FROM consentimiento ORDER BY createdAt ASC")
    suspend fun getAllUnaVez(): List<Consentimiento>

    @Query("DELETE FROM consentimiento")
    suspend fun deleteAll()
}
