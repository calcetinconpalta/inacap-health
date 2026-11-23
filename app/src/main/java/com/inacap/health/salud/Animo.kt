package com.inacap.health.salud

import com.inacap.health.data.EstadoAnimo
import java.time.LocalDate

/**
 * Tarea 18g · «¿Cómo te sientes hoy?»: un toque al día con 5 gatitos. Se guarda solo en el teléfono.
 * Los emojis se ven distinto según la marca del teléfono; para el mismo dibujo en todos, cambiar
 * [NIVELES] por ilustraciones propias.
 */
object Animo {
    data class Nivel(val valor: Int, val emoji: String, val nombre: String)

    val NIVELES = listOf(
        Nivel(1, "😿", "Muy mal"),
        Nivel(2, "🙀", "Mal"),
        Nivel(3, "😺", "Normal"),
        Nivel(4, "😸", "Bien"),
        Nivel(5, "😻", "Muy bien")
    )

    fun nivel(valor: Int) = NIVELES.firstOrNull { it.valor == valor }

    const val SUGERENCIA =
        "Estos días han sido difíciles. Si quieres, puedes responder un cuestionario breve o conversar con el equipo de Bienestar. Tú decides."

    /** ¿«Mal» o «Muy mal» los 3 últimos días seguidos (terminando hoy)? Entonces se sugiere apoyo, con suavidad. */
    fun sugerirApoyo(registros: List<EstadoAnimo>, hoy: LocalDate): Boolean {
        val porFecha = registros.associateBy { it.fecha }
        return (0L..2L).all { d -> (porFecha[hoy.minusDays(d).toString()]?.nivel ?: return false) <= 2 }
    }
}
