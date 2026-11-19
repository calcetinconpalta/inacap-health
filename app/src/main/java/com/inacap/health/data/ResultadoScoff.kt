package com.inacap.health.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "resultado_scoff")
data class ResultadoScoff(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val puntaje: Int,
    val respuestas: String,
    val requiereDerivacion: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    /** Identificador global: evita duplicados al sincronizar con la nube. */
    val uuid: String = UUID.randomUUID().toString()
)
