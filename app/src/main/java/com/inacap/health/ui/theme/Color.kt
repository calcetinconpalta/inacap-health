package com.inacap.health.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/*
 * Paleta "calmada y clínica": neutros fríos, un acento verde azulado para la información de salud
 * y el rojo INACAP reservado para la marca y las acciones principales (nunca para errores).
 */
internal object Paleta {
    val Rojo = Color(0xFFD6001C)
    val RojoNoche = Color(0xFFFF8A93)

    // Claro
    val Fondo = Color(0xFFF5F7F8)
    val Superficie = Color(0xFFFFFFFF)
    val SuperficieVariante = Color(0xFFEDF1F3)
    val Texto = Color(0xFF1A1D20)
    val TextoSuave = Color(0xFF5A6268)
    val Borde = Color(0xFFC3CBD0)
    val BordeSuave = Color(0xFFE1E6E9)
    val Calma = Color(0xFF2F6B70)
    val CalmaContenedor = Color(0xFFD9ECEC)
    val CalmaTexto = Color(0xFF0E3A3E)

    // Noche
    val FondoNoche = Color(0xFF101315)
    val SuperficieNoche = Color(0xFF181C1F)
    val SuperficieVarianteNoche = Color(0xFF22272B)
    val TextoNoche = Color(0xFFE2E6E9)
    val TextoSuaveNoche = Color(0xFFA4ADB3)
    val BordeNoche = Color(0xFF4A5257)
    val BordeSuaveNoche = Color(0xFF2C3236)
    val CalmaNoche = Color(0xFF8FCFD3)
    val CalmaContenedorNoche = Color(0xFF1F4A4E)
    val CalmaTextoNoche = Color(0xFFCBEDEE)
}

// Nombres antiguos, ahora conectados al tema activo (claro u oscuro).
val RedInacap: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val SurfaceLight: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant
val BorderGray: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant
val TextPrimary: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
val TextSecondary: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
val BackgroundWhite: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
