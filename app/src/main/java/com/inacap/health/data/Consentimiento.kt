package com.inacap.health.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Historial de consentimientos (auditoría). Cada vez que el usuario acepta o
 * revoca algo se agrega una fila nueva; nunca se modifica una existente.
 */
@Entity(tableName = "consentimiento")
data class Consentimiento(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    /** Ver [TipoConsentimiento]. */
    val tipo: String,
    val version: Int,
    val aceptado: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

object TipoConsentimiento {
    const val TERMINOS = "terminos"
    const val MAYORIA_EDAD = "mayoria_edad"
    const val NUBE = "nube"
    const val ORIENTACION = "orientacion"
}
