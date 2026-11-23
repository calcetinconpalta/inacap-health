package com.inacap.health.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncPendienteDao {
    @Insert
    suspend fun insert(item: SyncPendiente)

    /** Lote en orden de llegada; se saltan los que ya fallaron muchas veces. */
    @Query("SELECT * FROM sync_pendiente WHERE intentos < :maxIntentos ORDER BY id ASC LIMIT :limite")
    suspend fun getLote(limite: Int, maxIntentos: Int): List<SyncPendiente>

    @Query("DELETE FROM sync_pendiente WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE sync_pendiente SET intentos = intentos + 1, ultimoError = :error WHERE id = :id")
    suspend fun registrarFallo(id: Long, error: String)

    @Query("SELECT COUNT(*) FROM sync_pendiente")
    fun contar(): Flow<Int>

    @Query("DELETE FROM sync_pendiente")
    suspend fun deleteAll()

    /** Borra lo pendiente salvo lo que la persona pidió enviar explícitamente (p. ej. una derivación). */
    @Query("DELETE FROM sync_pendiente WHERE operacion != :explicito")
    suspend fun deleteNoExplicitos(explicito: String)

    @Query("DELETE FROM sync_pendiente WHERE tabla = :tabla")
    suspend fun deletePorTabla(tabla: String)

    @Query("UPDATE sync_pendiente SET registroId = :nuevo WHERE tabla = :tabla AND registroId = :viejo")
    suspend fun cambiarRegistroId(tabla: String, viejo: String, nuevo: String)
}
