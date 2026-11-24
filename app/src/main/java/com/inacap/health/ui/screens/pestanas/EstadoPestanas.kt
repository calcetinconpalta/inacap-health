package com.inacap.health.ui.screens.pestanas

import android.content.Context
import androidx.core.content.edit
import com.inacap.health.data.Perfil
import com.inacap.health.data.ResultadoCuestionario
import com.inacap.health.data.ResultadoScoff
import com.inacap.health.data.Sedes
import com.inacap.health.orientacion.Mensaje
import java.time.Instant
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId

/** Tarea 20 · Lo que muestran las tarjetas de Salud, Bienestar y Perfil (lógica pura, probada). */
object EstadoPestanas {

    /** % del perfil completo y qué falta (para «Mis datos»). */
    fun avanceDatos(p: Perfil?): Pair<Int, List<String>> {
        val campos = listOf(
            "nombre" to (p?.nombre?.isNotBlank() == true),
            "edad" to (p?.edad != null),
            "sexo" to (p?.sexo?.isNotBlank() == true),
            "sede" to (p?.sede in Sedes.LISTA),
            "patologías" to (p?.patologias?.isNotBlank() == true),
            "alergias" to (p?.alergias?.isNotBlank() == true),
            "medicamentos" to (p?.medicamentos?.isNotBlank() == true)
        )
        val faltan = campos.filterNot { it.second }.map { it.first }
        return ((campos.size - faltan.size) * 100 / campos.size) to faltan
    }

    /** «ana…@correo.cl»: el correo abreviado, para no mostrarlo entero en la grilla. */
    fun abreviarCorreo(email: String?): String? {
        val e = email?.trim()?.takeIf { "@" in it } ?: return null
        val (usuario, dominio) = e.substringBefore('@') to e.substringAfter('@')
        return (if (usuario.length <= 3) usuario else usuario.take(3) + "…") + "@" + dominio
    }

    fun recordatoriosActivos(c: Context): Int {
        val p = c.getSharedPreferences("health_prefs", Context.MODE_PRIVATE)
        return listOf("recordatorio_agua_activo", "recordatorio_mov_activo").count { p.getBoolean(it, false) }
    }

    /** Cuestionarios hechos este mes (✓ en los chips de Bienestar). */
    fun cuestionariosDelMes(
        resultados: List<ResultadoCuestionario>,
        scoff: List<ResultadoScoff>,
        mes: YearMonth,
        zona: ZoneId = ZoneId.systemDefault()
    ): Map<String, Boolean> {
        fun delMes(ms: Long) = YearMonth.from(Instant.ofEpochMilli(ms).atZone(zona)) == mes
        return linkedMapOf(
            "who5" to resultados.any { it.tipo == "who5" && delMes(it.createdAt) },
            "phq2" to resultados.any { it.tipo == "phq2" && delMes(it.createdAt) },
            "phq9" to resultados.any { it.tipo == "phq9" && delMes(it.createdAt) },
            "scoff" to scoff.any { delMes(it.createdAt) }
        )
    }

    fun ms(iso: String): Long = runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrDefault(0L)

    /** Mensajes del equipo que llegaron después de la última vez que la persona abrió el chat. */
    fun noLeidos(mensajes: List<Mensaje>, vistoHasta: Long): Int =
        mensajes.count { it.rol != "estudiante" && it.rol != "sistema" && ms(it.creadoEn) > vistoHasta }

    /** Último mensaje recibido (del equipo). */
    fun ultimoRecibido(mensajes: List<Mensaje>): Mensaje? =
        mensajes.filter { it.rol != "estudiante" }.maxByOrNull { ms(it.creadoEn) }
}

/** Cuándo abrió la persona cada chat por última vez (solo en el teléfono), para contar los no leídos. */
object ChatsVistos {
    private fun prefs(c: Context) = c.getSharedPreferences("chats_vistos", Context.MODE_PRIVATE)
    fun marcar(c: Context, conversacionId: String, ahora: Long = System.currentTimeMillis()) =
        prefs(c).edit { putLong(conversacionId, ahora) }
    fun visto(c: Context, conversacionId: String): Long = prefs(c).getLong(conversacionId, 0L)
}
