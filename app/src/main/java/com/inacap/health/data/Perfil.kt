package com.inacap.health.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "perfil")
data class Perfil(
    @PrimaryKey
    val id: Int = 1,
    val nombre: String,
    val edad: Int?,
    val sexo: String,
    val patologias: String,
    val alergias: String,
    val medicamentos: String,
    val consentimiento: Boolean,
    /** Versión de los Términos y Condiciones aceptada (0 = nunca aceptó). */
    @ColumnInfo(defaultValue = "0")
    val terminosVersion: Int = 0,
    /** Fecha (epoch ms) en que aceptó la versión actual de los términos. */
    @ColumnInfo(defaultValue = "0")
    val terminosAceptadosEn: Long = 0,
    /** Consentimiento opcional para enviar datos anonimizados a la nube. */
    @ColumnInfo(defaultValue = "0")
    val compartirNube: Boolean = false,
    /** Tarea 18c: sede INACAP elegida de [Sedes.LISTA] ("" = sin elegir). */
    @ColumnInfo(defaultValue = "''")
    val sede: String = ""
)

/**
 * ¿Tiene lo necesario para recomendaciones personalizadas? Si no, Inicio muestra
 * «Completa tus datos…» (tarea 14).
 */
fun Perfil?.datosCompletos(): Boolean = this != null && edad != null && sexo.isNotBlank() && sede in Sedes.LISTA
