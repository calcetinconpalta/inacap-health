package com.inacap.health.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Código anónimo oculto por defecto (evita que se vea o se filtre en una captura o al mostrar el teléfono).
 * Al mostrarlo se vuelve a ocultar solo después de 15 segundos.
 */
@Composable
fun CodigoOculto(etiqueta: String, codigo: String?) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) { delay(15_000); visible = false } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(
            when { codigo == null -> "—"; visible -> codigo.take(8); else -> "••••••••" },
            fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (codigo != null) TextButton(onClick = { visible = !visible }) { Text(if (visible) "Ocultar" else "Mostrar", fontSize = 13.sp) }
    }
}
