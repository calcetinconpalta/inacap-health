package com.inacap.health.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.forLanguageTag("es-CL"))

fun horaCorta(iso: String): String =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(HORA) }.getOrDefault("")

fun nombreRolChat(rol: String) = when (rol) {
    "tutor" -> "Tutor par"
    "docente" -> "Docente supervisor"
    "profesional" -> "Profesional de Bienestar"
    "sistema" -> "Aviso"
    else -> "Estudiante"
}

/** Burbuja de mensaje: a la derecha los propios, a la izquierda los demás, al centro los avisos. */
@Composable
fun BurbujaMensaje(rol: String, texto: String, creadoEn: String, propio: Boolean, etiquetaPropia: String = "Tú") {
    val sistema = rol == "sistema"
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = when { sistema -> Alignment.Center; propio -> Alignment.CenterEnd; else -> Alignment.CenterStart }
    ) {
        Column(
            Modifier
                .widthIn(max = 310.dp)
                .background(
                    when {
                        sistema -> MaterialTheme.colorScheme.surfaceVariant
                        propio -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    },
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                (if (propio) etiquetaPropia else nombreRolChat(rol)) + "  ·  " + horaCorta(creadoEn),
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(texto, fontSize = 15.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
