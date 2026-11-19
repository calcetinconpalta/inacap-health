package com.inacap.health.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/** Pasos de un día (HU-10). Se guarda el mayor valor entre el sensor y Health Connect. */
@Entity(tableName = "pasos_dia")
data class PasosDia(
    /** yyyy-MM-dd en la zona horaria del teléfono. */
    @PrimaryKey val fecha: String,
    val pasos: Int,
    /** "sensor" o "health_connect". */
    val fuente: String,
    val actualizadoEn: Long = System.currentTimeMillis(),
    /** Mismo id para toda la vida del día: al sincronizar se actualiza, no se duplica. */
    val uuid: String = UUID.randomUUID().toString()
)
