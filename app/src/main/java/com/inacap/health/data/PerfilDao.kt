package com.inacap.health.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PerfilDao {
    @Upsert
    suspend fun upsertPerfil(perfil: Perfil)

    @Query("SELECT * FROM perfil WHERE id = 1")
    fun getPerfil(): Flow<Perfil?>

    @Query("SELECT * FROM perfil WHERE id = 1")
    suspend fun getPerfilUnaVez(): Perfil?

    @Query("DELETE FROM perfil")
    suspend fun deleteAll()
}
