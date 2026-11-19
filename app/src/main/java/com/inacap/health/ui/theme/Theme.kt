package com.inacap.health.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val Claro = lightColorScheme(
    primary = Paleta.Rojo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFDE7E9),
    onPrimaryContainer = Color(0xFF5C0010),
    secondary = Paleta.Calma,
    onSecondary = Color.White,
    secondaryContainer = Paleta.CalmaContenedor,
    onSecondaryContainer = Paleta.CalmaTexto,
    tertiary = Paleta.Calma,
    background = Paleta.Fondo,
    onBackground = Paleta.Texto,
    surface = Paleta.Superficie,
    onSurface = Paleta.Texto,
    surfaceVariant = Paleta.SuperficieVariante,
    onSurfaceVariant = Paleta.TextoSuave,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBFC),
    surfaceContainer = Color(0xFFF0F3F5),
    surfaceContainerHigh = Color(0xFFEAEEF0),
    surfaceContainerHighest = Color(0xFFE4E9EC),
    outline = Paleta.Borde,
    outlineVariant = Paleta.BordeSuave,
    // Guía de diseño: el rojo NUNCA se usa para errores o alertas.
    error = Paleta.Texto,
    onError = Color.White,
    errorContainer = Paleta.SuperficieVariante,
    onErrorContainer = Paleta.Texto
)

private val Noche = darkColorScheme(
    primary = Paleta.RojoNoche,
    onPrimary = Color(0xFF3F0008),
    primaryContainer = Color(0xFF5A1019),
    onPrimaryContainer = Color(0xFFFFD9DC),
    secondary = Paleta.CalmaNoche,
    onSecondary = Color(0xFF00363A),
    secondaryContainer = Paleta.CalmaContenedorNoche,
    onSecondaryContainer = Paleta.CalmaTextoNoche,
    tertiary = Paleta.CalmaNoche,
    background = Paleta.FondoNoche,
    onBackground = Paleta.TextoNoche,
    surface = Paleta.SuperficieNoche,
    onSurface = Paleta.TextoNoche,
    surfaceVariant = Paleta.SuperficieVarianteNoche,
    onSurfaceVariant = Paleta.TextoSuaveNoche,
    surfaceContainerLowest = Color(0xFF0B0E10),
    surfaceContainerLow = Color(0xFF15181B),
    surfaceContainer = Color(0xFF1B1F22),
    surfaceContainerHigh = Color(0xFF23282B),
    surfaceContainerHighest = Color(0xFF2C3135),
    outline = Paleta.BordeNoche,
    outlineVariant = Paleta.BordeSuaveNoche,
    error = Paleta.TextoNoche,
    onError = Paleta.FondoNoche,
    errorContainer = Paleta.SuperficieVarianteNoche,
    onErrorContainer = Paleta.TextoNoche
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Modo de apariencia elegido por la persona (se guarda en el teléfono). */
enum class ModoTema(val etiqueta: String) { SISTEMA("Igual que el teléfono"), CLARO("Claro"), OSCURO("Noche") }

object Apariencia {
    private const val PREFS = "apariencia"
    private val _modo = MutableStateFlow(ModoTema.SISTEMA)
    val modo: StateFlow<ModoTema> = _modo.asStateFlow()

    fun cargar(context: Context) {
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("modo", null)
        _modo.value = ModoTema.entries.firstOrNull { it.name == v } ?: ModoTema.SISTEMA
    }

    fun cambiar(context: Context, modo: ModoTema) {
        _modo.value = modo
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("modo", modo.name).apply()
    }

    /** Alterna entre claro y noche partiendo de lo que se ve ahora. */
    fun alternar(context: Context, oscuroAhora: Boolean) = cambiar(context, if (oscuroAhora) ModoTema.CLARO else ModoTema.OSCURO)
}

@Composable
fun temaOscuroActivo(): Boolean {
    val modo by Apariencia.modo.collectAsState()
    return when (modo) {
        ModoTema.SISTEMA -> isSystemInDarkTheme()
        ModoTema.CLARO -> false
        ModoTema.OSCURO -> true
    }
}

@Composable
fun INACAPHealthTheme(oscuro: Boolean = temaOscuroActivo(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (oscuro) Noche else Claro,
        typography = Typography,
        shapes = Formas,
        content = content
    )
}
