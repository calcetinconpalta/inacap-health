package com.inacap.health.salud

/**
 * Tarea 18f · Cuestionarios de Bienestar (uso libre, versiones en español).
 * Orientan, NO diagnostican. Las respuestas se quedan en el teléfono; a la nube solo el puntaje.
 *
 * Fuentes verificadas (texto y puntos de corte; ver docs/CUESTIONARIOS.md):
 * - WHO-5: traducción oficial al español (Psychiatric Research Unit, Centro Colaborador OMS, 1998).
 *   Puntaje = suma × 4 (0–100). Se recomienda evaluar más si la suma es < 13 o algún ítem vale 0 o 1.
 * - PHQ-9 / PHQ-2: versión en español de Pfizer (PRIME-MD). Corte PHQ-2 ≥ 3 (aplicar PHQ-9);
 *   PHQ-9 ≥ 10 (síntomas moderados o más), según las referencias de la validación chilena (Errázuriz et al., 2022).
 *   Se corrigieron solo erratas tipográficas evidentes de la copia en PDF («Temer» → «Tener», tildes).
 * Pendientes de verificar su texto exacto en español antes de activarlos: GAD-2/GAD-7, PSS-10, AUDIT-C, IPAQ.
 */
data class Cuestionario(
    val clave: String,
    val nombre: String,
    val descripcion: String,
    val encabezado: String,
    /** Opciones (texto, puntos), iguales para todas las preguntas. */
    val opciones: List<Pair<String, Int>>,
    val preguntas: List<String>,
    /** false = texto aún no verificado en su versión validada: se muestra, pero no se puede responder. */
    val disponible: Boolean = true
)

data class ResultadoEvaluacion(
    val puntaje: Int,
    val nivel: String,
    val texto: String,
    /** Recomendar hablar con Bienestar (con el botón de pedir atención). */
    val alerta: Boolean,
    /** PHQ-9 pregunta 9 distinta de «Nunca»: pantalla de ayuda inmediata, sin importar el puntaje. */
    val ayudaUrgente: Boolean = false,
    /** PHQ-2 en alerta: ofrecer el PHQ-9 completo. */
    val ofrecer: String? = null
)

object Cuestionarios {
    const val NO_DIAGNOSTICO = "Esto no es un diagnóstico: es una orientación para tu autocuidado."

    private val FRECUENCIA_PHQ = listOf("Nunca" to 0, "Varios días" to 1, "Más de la mitad de los días" to 2, "Casi todos los días" to 3)
    private const val ENCABEZADO_PHQ = "Durante las últimas 2 semanas, ¿con qué frecuencia le han molestado los siguientes problemas?"
    private val ITEMS_PHQ9 = listOf(
        "Tener poco interés o placer en hacer las cosas",
        "Sentirse desanimado/a, deprimido/a, o sin esperanza",
        "Con problemas en dormirse o en mantenerse dormido/a, o en dormir demasiado",
        "Sentirse cansado/a o tener poca energía",
        "Tener poco apetito o comer en exceso",
        "Sentir falta de amor propio – o que sea un fracaso o que decepcionara a sí mismo/a o a su familia",
        "Tener dificultad para concentrarse en cosas tales como leer el periódico o mirar la televisión",
        "Se mueve o habla tan lentamente que otra gente se podría dar cuenta – o de lo contrario, está tan agitado/a o inquieto/a que se mueve mucho más de lo acostumbrado",
        "Se le han ocurrido pensamientos de que sería mejor estar muerto/a o de que se haría daño de alguna manera"
    )

    val WHO5 = Cuestionario(
        clave = "who5", nombre = "WHO-5 · Bienestar general", descripcion = "5 preguntas sobre cómo te has sentido",
        encabezado = "Durante las últimas dos semanas:",
        opciones = listOf("Todo el tiempo" to 5, "La mayor parte del tiempo" to 4, "Más de la mitad del tiempo" to 3,
            "Menos de la mitad del tiempo" to 2, "De vez en cuando" to 1, "Nunca" to 0),
        preguntas = listOf(
            "Me he sentido alegre y de buen humor",
            "Me he sentido tranquilo y relajado",
            "Me he sentido activo y enérgico",
            "Me he despertado fresco y descansado",
            "Mi vida cotidiana ha estado llena de cosas que me interesan"
        )
    )
    val PHQ2 = Cuestionario("phq2", "PHQ-2 · Ánimo", "2 preguntas breves sobre tu ánimo", ENCABEZADO_PHQ, FRECUENCIA_PHQ, ITEMS_PHQ9.take(2))
    val PHQ9 = Cuestionario("phq9", "PHQ-9 · Ánimo (completo)", "9 preguntas sobre tu ánimo", ENCABEZADO_PHQ, FRECUENCIA_PHQ, ITEMS_PHQ9)

    private fun pendiente(clave: String, nombre: String, descripcion: String) =
        Cuestionario(clave, nombre, descripcion, "", emptyList(), emptyList(), disponible = false)

    val TODOS = listOf(
        WHO5, PHQ2, PHQ9,
        pendiente("gad7", "GAD-2 / GAD-7 · Ansiedad", "Disponible cuando se verifique su versión validada en español"),
        pendiente("pss10", "PSS-10 · Estrés percibido", "Disponible cuando se verifique su versión validada en español"),
        pendiente("auditc", "AUDIT-C · Consumo de alcohol", "Disponible cuando se verifique su versión validada en español"),
        pendiente("ipaq", "IPAQ corto · Actividad física", "Disponible cuando se verifique su versión validada en español")
    )

    fun por(clave: String) = TODOS.first { it.clave == clave }

    /** [respuestas] son los puntos de cada pregunta, en orden. */
    fun evaluar(c: Cuestionario, respuestas: List<Int>): ResultadoEvaluacion {
        require(respuestas.size == c.preguntas.size) { "Faltan respuestas" }
        val suma = respuestas.sum()
        return when (c.clave) {
            "who5" -> {
                val alerta = suma < 13 || respuestas.any { it <= 1 }
                ResultadoEvaluacion(
                    puntaje = suma * 4, nivel = if (alerta) "Bienestar bajo" else "Buen bienestar",
                    texto = if (alerta) "Tus respuestas muestran que tu bienestar ha estado bajo estas semanas. Te recomendamos conversar con el equipo de Bienestar."
                    else "Tus respuestas muestran un buen nivel de bienestar. Sigue cuidándote.",
                    alerta = alerta
                )
            }
            "phq2" -> {
                val alerta = suma >= 3
                ResultadoEvaluacion(
                    puntaje = suma, nivel = if (alerta) "Conviene profundizar" else "Sin alerta",
                    texto = if (alerta) "Tus respuestas sugieren revisar tu ánimo con más detalle. Te recomendamos responder el PHQ-9 y hablar con el equipo de Bienestar."
                    else "Tus respuestas no muestran señales de alerta en tu ánimo por ahora.",
                    alerta = alerta, ofrecer = if (alerta) "phq9" else null
                )
            }
            "phq9" -> {
                val nivel = when {
                    suma <= 4 -> "Mínimo"; suma <= 9 -> "Leve"; suma <= 14 -> "Moderado"; suma <= 19 -> "Moderado a severo"; else -> "Severo"
                }
                val alerta = suma >= 10
                ResultadoEvaluacion(
                    puntaje = suma, nivel = nivel,
                    texto = if (alerta) "Tus respuestas muestran varios síntomas relacionados con el ánimo. Te recomendamos hablar con el equipo de Bienestar: es confidencial."
                    else "Tus respuestas no muestran señales importantes por ahora. Si algo te preocupa, puedes conversar con Bienestar cuando quieras.",
                    alerta = alerta || respuestas[8] > 0,
                    ayudaUrgente = respuestas[8] > 0
                )
            }
            else -> error("Cuestionario no disponible: ${c.clave}")
        }
    }
}
