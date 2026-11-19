package com.inacap.health.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Bandeja de salida (outbox) para la sincronización offline-first.
 * Cada cambio que debe ir a la nube se guarda aquí primero; un Worker lo
 * envía cuando hay internet y lo borra al confirmarse.
 */
@Entity(tableName = "sync_pendiente")
data class SyncPendiente(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** "sql" (Supabase) o "nosql" (Firestore). */
    val destino: String,
    /** "upsert" o "delete". */
    val operacion: String,
    /** Tabla SQL o colección NoSQL. */
    val tabla: String,
    /** UUID del registro: el mismo id se usa en la nube, así no hay duplicados. */
    val registroId: String,
    /** Datos en JSON. */
    val payload: String,
    val intentos: Int = 0,
    val ultimoError: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
