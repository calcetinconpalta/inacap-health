package com.inacap.health.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "registro_biometrico")
data class RegistroBiometrico(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pesoKg: Double?,
    val estaturaCm: Double?,
    val imc: Double?,
    val aguaMl: Int,
    val frecuenciaCardiaca: Int?,
    val createdAt: Long = System.currentTimeMillis(),
    /** Identificador global: evita duplicados al sincronizar con la nube. */
    val uuid: String = UUID.randomUUID().toString(),
    /** De dónde viene: null/"app" (ingresado), "camara" (pulso PPG), "health_connect" (reloj). */
    val fuente: String? = null
)
