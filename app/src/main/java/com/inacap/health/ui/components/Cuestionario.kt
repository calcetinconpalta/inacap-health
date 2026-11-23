package com.inacap.health.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Una pregunta por pantalla, con opciones grandes (tareas 16 y 18f).
 * - Con 2 opciones (Sí / No) van lado a lado, grandes y bien separadas; con más, una bajo otra.
 * - «Siguiente» (o «Ver resultado» en la última) aparece solo después de elegir.
 * - «Volver a la pregunta anterior» es un enlace chico, separado de los botones.
 */
@Composable
fun PasoCuestionario(
    numero: Int,
    total: Int,
    pregunta: String,
    opciones: List<String>,
    elegida: Int?,
    onElegir: (Int) -> Unit,
    onSiguiente: () -> Unit,
    onAnterior: (() -> Unit)?,
    encabezado: String? = null
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pregunta $numero de $total", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            LinearProgressIndicator(
                progress = { numero / total.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )
        }
        encabezado?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(
            pregunta, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(vertical = 8.dp)
        )

        if (opciones.size == 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                opciones.forEachIndexed { i, texto ->
                    Opcion(texto, i == elegida, Modifier.weight(1f).heightIn(min = 72.dp)) { onElegir(i) }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                opciones.forEachIndexed { i, texto ->
                    Opcion(texto, i == elegida, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { onElegir(i) }
                }
            }
        }

        if (elegida != null) {
            Button(
                onClick = onSiguiente,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text(if (numero == total) "Ver resultado" else "Siguiente", fontWeight = FontWeight.Bold) }
        }

        if (onAnterior != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onAnterior, modifier = Modifier.fillMaxWidth()) {
                Text("Volver a la pregunta anterior", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Opcion(texto: String, elegida: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val forma = RoundedCornerShape(16.dp)
    if (elegida) {
        Button(
            onClick = onClick, modifier = modifier, shape = forma,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        ) { Text(texto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, shape = forma, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Text(texto, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        }
    }
}
