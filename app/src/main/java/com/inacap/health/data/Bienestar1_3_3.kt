package com.inacap.health.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Tarea 18c · Sedes INACAP. ÚNICO lugar donde están: para agregar una sede, súmala aquí y
 * en la tabla `sedes` del servidor (schema.sql). No se usa GPS ni se pide dirección.
 */
object Sedes {
    val LISTA = listOf("Valdivia")
    const val SIN_DATO = "sin_dato"

    /** Lo que viaja a la nube: solo una sede conocida o "sin_dato". */
    fun paraNube(sede: String?): String = sede?.trim()?.takeIf { it in LISTA } ?: SIN_DATO
}

/** Tarea 18g · Check-in diario «¿Cómo te sientes hoy?» (1 = muy mal … 5 = muy bien). Solo en el teléfono. */
@Entity(tableName = "estado_animo")
data class EstadoAnimo(
    /** yyyy-MM-dd: un registro por día (se puede cambiar durante el día). */
    @PrimaryKey val fecha: String,
    val nivel: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface EstadoAnimoDao {
    @Upsert
    suspend fun guardar(e: EstadoAnimo)

    @Query("SELECT * FROM estado_animo WHERE fecha = :fecha")
    fun observar(fecha: String): Flow<EstadoAnimo?>

    @Query("SELECT * FROM estado_animo ORDER BY fecha DESC LIMIT :n")
    suspend fun ultimos(n: Int): List<EstadoAnimo>

    @Query("SELECT * FROM estado_animo ORDER BY fecha ASC")
    fun observarTodos(): Flow<List<EstadoAnimo>>

    @Query("SELECT * FROM estado_animo ORDER BY fecha ASC")
    suspend fun getAllUnaVez(): List<EstadoAnimo>

    @Query("DELETE FROM estado_animo")
    suspend fun deleteAll()
}

/**
 * Tarea 18f · Resultado de un cuestionario de Bienestar (WHO-5, PHQ-2, PHQ-9, GAD-2, GAD-7, PSS-10,
 * AUDIT-C, IPAQ). Las [respuestas] se quedan en el teléfono; a la nube solo va el puntaje (y solo
 * si la persona comparte datos anonimizados).
 */
@Entity(tableName = "resultado_cuestionario")
data class ResultadoCuestionario(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: String,
    val puntaje: Int,
    val respuestas: String,
    val alerta: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val uuid: String = UUID.randomUUID().toString()
)

@Dao
interface ResultadoCuestionarioDao {
    @Insert
    suspend fun insert(r: ResultadoCuestionario)

    @Query("SELECT * FROM resultado_cuestionario ORDER BY createdAt DESC")
    fun observarTodos(): Flow<List<ResultadoCuestionario>>

    @Query("SELECT * FROM resultado_cuestionario ORDER BY createdAt ASC")
    suspend fun getAllUnaVez(): List<ResultadoCuestionario>

    @Query("UPDATE resultado_cuestionario SET uuid = :nuevo WHERE uuid = :viejo")
    suspend fun cambiarUuid(viejo: String, nuevo: String): Int

    @Query("DELETE FROM resultado_cuestionario")
    suspend fun deleteAll()
}
