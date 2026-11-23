package com.inacap.health.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inacap.health.BuildConfig
import com.inacap.health.data.remote.ExplicacionError

/**
 * Mensaje simple del error + «Ver detalle del error» con el error real (tipo, petición y respuesta),
 * para saber qué pasa y poder reportarlo. En la versión publicada se ocultan las direcciones de los servidores.
 */
@Suppress("DEPRECATION") // LocalClipboard (nuevo) es suspend; para copiar texto basta el anterior.
@Composable
fun DetalleError(resumen: String, error: String?) {
    var abierto by remember { mutableStateOf(false) }
    // En las compilaciones de prueba, el detalle parte con qué configurar (puede nombrar servicios:
    // solo se ve al abrir «Ver detalle del error»).
    val detalle = ExplicacionError.detalle(error, completo = BuildConfig.DEBUG)?.let {
        if (BuildConfig.DEBUG) ExplicacionError.explicar(error) + "\n\n" + it else it
    }
    val portapapeles = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth()) {
        TextoSecundario(resumen)
        if (detalle != null) {
            TextButton(onClick = { abierto = !abierto }) {
                Text(if (abierto) "Ocultar detalle" else "Ver detalle del error", fontSize = 13.sp)
            }
            if (abierto) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Text(detalle, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(10.dp))
                    }
                }
                TextButton(onClick = { portapapeles.setText(AnnotatedString(detalle)) }) { Text("Copiar detalle", fontSize = 13.sp) }
            }
        }
    }
}
