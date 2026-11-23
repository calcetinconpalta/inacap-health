package com.inacap.health.ui.screens.inicio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inacap.health.salud.Animo

/**
 * Color del acento del gatito elegido. POR DECIDIR (el usuario dirá dónde va el morado):
 * se cambia solo aquí.
 */
val AcentoAnimo: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary

/** Tarea 18g · «¿Cómo te sientes hoy?» con 5 gatitos; el elegido queda hundido con un borde de color. */
@Composable
fun CheckInAnimo(nivelHoy: Int?, sugerirApoyo: Boolean, onElegir: (Int) -> Unit, onCuestionarios: () -> Unit, onBienestar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("¿Cómo te sientes hoy?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Animo.NIVELES.forEach { n ->
                    val elegido = n.valor == nivelHoy
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(52.dp)
                            .background(if (elegido) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, CircleShape)
                            .border(if (elegido) 2.dp else 0.dp, if (elegido) AcentoAnimo else Color.Transparent, CircleShape)
                            .clickable { onElegir(n.valor) }
                            .semantics { contentDescription = n.nombre; selected = elegido }
                    ) { Text(n.emoji, fontSize = if (elegido) 26.sp else 28.sp) }
                }
            }
            val elegido = nivelHoy?.let { Animo.nivel(it) }
            Text(
                if (elegido == null) buildAnnotatedString { append("Toca el gatito que más se parece a tu día.") }
                else buildAnnotatedString { append("Hoy te sientes "); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(elegido.nombre) } },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (sugerirApoyo) {
                Text(Animo.SUGERENCIA, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCuestionarios) { Text("Cuestionario breve") }
                    TextButton(onClick = onBienestar) { Text("Hablar con Bienestar") }
                }
            }
        }
    }
}
