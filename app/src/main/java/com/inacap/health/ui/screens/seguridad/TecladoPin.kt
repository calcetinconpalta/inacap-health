package com.inacap.health.ui.screens.seguridad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inacap.health.seguridad.Pin

/** Puntos que muestran cuántos dígitos se ingresaron (nunca el PIN). */
@Composable
fun PuntosPin(largo: Int, total: Int = Pin.LARGO_MAX) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics { contentDescription = "$largo dígitos ingresados" }
    ) {
        repeat(maxOf(Pin.LARGO_MIN, minOf(total, maxOf(largo, Pin.LARGO_MIN)))) { i ->
            Box(
                Modifier
                    .size(14.dp)
                    .background(if (i < largo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background, CircleShape)
                    .border(1.5.dp, if (i < largo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
            )
        }
    }
}

/** Teclado numérico. [accionExtra] ocupa la esquina inferior izquierda (p. ej. huella). */
@Composable
fun TecladoPin(
    onDigito: (Char) -> Unit,
    onBorrar: () -> Unit,
    habilitado: Boolean = true,
    accionExtra: Pair<String, () -> Unit>? = null
) {
    val filas = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        filas.forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                fila.forEach { d -> Tecla(d.toString(), habilitado) { onDigito(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (accionExtra != null) Tecla(accionExtra.first, habilitado, pequena = true, onClick = accionExtra.second)
            else Box(Modifier.size(72.dp))
            Tecla("0", habilitado) { onDigito('0') }
            Tecla("Borrar", habilitado, pequena = true, onClick = onBorrar)
        }
    }
}

@Composable
private fun Tecla(texto: String, habilitado: Boolean, pequena: Boolean = false, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .clickable(enabled = habilitado, onClick = onClick)
    ) {
        Text(
            texto,
            fontSize = if (pequena) 13.sp else 26.sp,
            fontWeight = if (pequena) FontWeight.Medium else FontWeight.SemiBold,
            color = if (habilitado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
