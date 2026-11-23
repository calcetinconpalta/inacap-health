package com.inacap.health.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tarea 18e · Rediseño rojo y blanco INACAP. TODOS los ajustes de intensidad del hero están aquí:
 * si destaca demasiado, bajar [INTENSIDAD_MALLA], [SOMBRA_HERO] o [ALTO_HERO].
 * Esta versión parte más suave que la maqueta (docs/maqueta-inicio.html).
 */
object Diseno {
    /** 0 = rojo plano, 1 = malla como en la maqueta. */
    const val INTENSIDAD_MALLA = 0.6f
    /** Opacidad de los círculos blancos translúcidos y de la ola de cada dato. */
    const val OPACIDAD_CAPAS = 0.10f
    val SOMBRA_HERO = 6.dp
    val ALTO_HERO = 156.dp
    val ESQUINA_HERO = 20.dp
    val ESQUINA_TARJETA = 16.dp

    /** Parallax: cuánto se mueve cada capa respecto del deslizamiento (fondo el más lento, frente el más rápido). */
    const val PARALLAX_FONDO = 0.35f
    const val PARALLAX_OLA = 0.65f

    val BandaRoja = Color(0xFFD6001C)
    val BandaRojaOscura = Color(0xFFA80016)          // borde inferior del botón principal
    val BandaRojaNoche = Color(0xFFB00018)            // modo noche: banda algo más oscura
    val FondoClaro = Color(0xFFF7F7F8)
    val FondoNoche = Color(0xFF0C0C0E)

    /** Una mancha de la malla: color y centro (fracción del ancho/alto). */
    data class Mancha(val color: Color, val x: Float, val y: Float, val radio: Float)

    /** Mezcla de cada dato, anclada en el rojo (agua: toque azul; pasos: naranjo/dorado; pulso: magenta; IMC: verde azulado). */
    val MALLA_AGUA = listOf(Mancha(Color(0xFFFF5A73), 0f, 0f, 0.85f), Mancha(Color(0xFF2F6FF2), 1f, 1f, 0.8f), Mancha(Color(0xFF6E0016), 0f, 1f, 0.85f))
    val MALLA_PASOS = listOf(Mancha(Color(0xFFFFB23A), 1f, 0f, 0.8f), Mancha(Color(0xFFFF6A2B), 0f, 1f, 0.85f), Mancha(Color(0xFF82001A), 1f, 1f, 0.8f))
    val MALLA_PULSO = listOf(Mancha(Color(0xFFFF2D6F), 0f, 0f, 0.85f), Mancha(Color(0xFF8E0040), 1f, 1f, 0.85f), Mancha(Color(0xFF5E0012), 0f, 1f, 0.8f))
    val MALLA_IMC = listOf(Mancha(Color(0xFF00B39B), 1f, 1f, 0.82f), Mancha(Color(0xFFFF5A73), 0f, 0f, 0.8f), Mancha(Color(0xFF6E0016), 0f, 1f, 0.82f))

    /** Punto chico del color propio de cada dato (ya no es protagonista). */
    val PUNTO_AGUA = Color(0xFF1769E0)
    val PUNTO_PASOS = Color(0xFFE65C00)
    val PUNTO_PULSO = Color(0xFFC62828)
    val PUNTO_IMC = Color(0xFF00796B)
}
