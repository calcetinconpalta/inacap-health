package com.inacap.health.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RegistroBiometricoDao {
    @Upsert
    suspend fun upsert(registro: RegistroBiometrico)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(registro: RegistroBiometrico)

    @Query("SELECT * FROM registro_biometrico ORDER BY createdAt DESC")
    fun getAll(): Flow<List<RegistroBiometrico>>

    @Query("SELECT * FROM registro_biometrico ORDER BY createdAt ASC")
    suspend fun getAllUnaVez(): List<RegistroBiometrico>

    @Query("SELECT COALESCE(SUM(aguaMl), 0) FROM registro_biometrico WHERE createdAt >= :fromTimestamp")
    fun getAguaSumFrom(fromTimestamp: Long): Flow<Int>

    // IMC con o sin peso: el IMC recuperado desde la cuenta no trae peso (nunca se sube).
    @Query("SELECT * FROM registro_biometrico WHERE imc IS NOT NULL ORDER BY createdAt DESC LIMIT 1")
    fun getUltimoRegistroConPeso(): Flow<RegistroBiometrico?>

    @Query("SELECT * FROM registro_biometrico WHERE imc IS NOT NULL ORDER BY createdAt ASC")
    fun getRegistrosConPesoAsc(): Flow<List<RegistroBiometrico>>

    @Query("SELECT * FROM registro_biometrico WHERE aguaMl > 0 AND createdAt >= :fromTimestamp ORDER BY createdAt ASC")
    fun getRegistrosAguaFrom(fromTimestamp: Long): Flow<List<RegistroBiometrico>>

    @Query("SELECT * FROM registro_biometrico WHERE aguaMl > 0 AND createdAt >= :fromTimestamp ORDER BY createdAt DESC LIMIT 1")
    suspend fun getUltimoAguaDelDia(fromTimestamp: Long): RegistroBiometrico?

    @Query("DELETE FROM registro_biometrico WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM registro_biometrico WHERE frecuenciaCardiaca IS NOT NULL ORDER BY createdAt DESC")
    fun getPulsosDesc(): Flow<List<RegistroBiometrico>>

    @Query("SELECT * FROM registro_biometrico WHERE uuid = :uuid LIMIT 1")
    suspend fun getPorUuid(uuid: String): RegistroBiometrico?

    @Query("DELETE FROM registro_biometrico")
    suspend fun deleteAll()
}
