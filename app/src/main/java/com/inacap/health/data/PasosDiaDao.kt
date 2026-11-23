package com.inacap.health.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PasosDiaDao {
    @Upsert
    suspend fun upsert(dia: PasosDia)

    @Query("SELECT * FROM pasos_dia WHERE fecha = :fecha")
    suspend fun get(fecha: String): PasosDia?

    @Query("SELECT * FROM pasos_dia WHERE fecha = :fecha")
    fun observar(fecha: String): Flow<PasosDia?>

    @Query("SELECT * FROM pasos_dia WHERE fecha >= :desde ORDER BY fecha ASC")
    fun desde(desde: String): Flow<List<PasosDia>>

    @Query("SELECT * FROM pasos_dia ORDER BY fecha ASC")
    suspend fun getAllUnaVez(): List<PasosDia>

    @Query("DELETE FROM pasos_dia")
    suspend fun deleteAll()

    /** Cambia el id de sincronización (cuando el anterior quedó asociado a otra sesión en la nube). */
    @Query("UPDATE pasos_dia SET uuid = :nuevo WHERE uuid = :viejo")
    suspend fun cambiarUuid(viejo: String, nuevo: String): Int
}
