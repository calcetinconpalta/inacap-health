package com.inacap.health.ui.screens.perfil

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inacap.health.data.Consentimiento
import com.inacap.health.data.TipoConsentimiento
import com.inacap.health.legal.TextosLegales
import com.inacap.health.ui.theme.BorderGray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fecha(ms: Long): String =
    if (ms <= 0) "—" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))

private fun nombreConsentimiento(tipo: String): String = when (tipo) {
    TipoConsentimiento.TERMINOS -> "Términos y privacidad"
    TipoConsentimiento.MAYORIA_EDAD -> "Mayoría de edad / autorización"
    TipoConsentimiento.NUBE -> "Compartir datos anonimizados"
    else -> tipo
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacidadScreen(
    viewModel: PerfilViewModel,
    onBack: () -> Unit,
    onVerTerminos: () -> Unit,
    onDataDeleted: () -> Unit
) {
    val perfil by viewModel.perfil.collectAsState()
    val nube by viewModel.estadoNube.collectAsState()
    val consentimientos by viewModel.consentimientos.collectAsState()

    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showRevocarDialog by rememberSaveable { mutableStateOf(false) }
    var verHistorial by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Privacidad y mis datos",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // --- Términos ---
            Tarjeta(titulo = "Términos y condiciones") {
                Texto(
                    if ((perfil?.terminosVersion ?: 0) > 0) {
                        "Aceptaste la versión ${perfil?.terminosVersion} el ${fecha(perfil?.terminosAceptadosEn ?: 0)}."
                    } else {
                        "Aún no hay una aceptación registrada."
                    }
                )
                TextButton(onClick = onVerTerminos) {
                    Text("Leer términos y política de privacidad", color = MaterialTheme.colorScheme.primary)
                }
            }

            // --- Qué se guarda ---
            Tarjeta(titulo = "Qué se guarda") {
                FilaDato("Nombre, patologías, alergias, medicamentos", "Solo en tu teléfono")
                FilaDato("Peso y estatura", "Solo en tu teléfono")
                FilaDato("Respuestas del test SCOFF", "Solo en tu teléfono")
                FilaDato("Imágenes de la cámara (pulso)", "Nunca se guardan")
                FilaDato("IMC, agua, pasos, pulso y puntaje SCOFF", "En tu teléfono · anónimo si compartes")
                FilaDato("Uso de la app", "Solo si compartes, anónimo")
                FilaDato("Contacto en una solicitud de apoyo", "Protegido · solo Bienestar")
                FilaDato("Mensajes con tu tutor par", "Tu tutor y su docente supervisor")
                FilaDato("Mensajes con un profesional", "Solo tú y el profesional")
            }

            // --- Nube ---
            Tarjeta(titulo = "Compartir datos anonimizados") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Texto(
                        "Envía una copia sin tu nombre ni datos clínicos para estadísticas de bienestar estudiantil.",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = perfil?.compartirNube == true,
                        onCheckedChange = { activar ->
                            if (activar) viewModel.cambiarCompartirNube(true) else showRevocarDialog = true
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                HorizontalDivider(color = BorderGray)
                if (!nube.configurada) {
                    Texto("Compartir datos no está disponible en esta versión: nada sale de tu teléfono.")
                } else {
                    FilaDato("Estado", if (nube.compartiendo) "Activado" else "Desactivado")
                    FilaDato("Envíos pendientes", nube.pendientes.toString())
                    FilaDato("Último envío", fecha(nube.ultimoEnvio))
                    com.inacap.health.ui.components.CodigoOculto("Tu código anónimo", nube.idAnonimo)
                    if (nube.borradoPendiente) {
                        Texto("El borrado de tus datos compartidos está pendiente: se completará cuando haya internet.")
                    }
                    if (nube.ultimoError != null) {
                        com.inacap.health.ui.components.DetalleError(
                            resumen = com.inacap.health.data.remote.ExplicacionError.paraPersona(nube.ultimoError, Regex("HTTP (\\d{3})").find(nube.ultimoError!!)?.groupValues?.get(1)?.toIntOrNull()),
                            error = nube.ultimoError
                        )
                    }
                }
            }

            // --- Historial de consentimientos ---
            Tarjeta(titulo = "Historial de consentimientos") {
                if (consentimientos.isEmpty()) {
                    Texto("Sin registros.")
                } else {
                    val visibles = if (verHistorial) consentimientos else consentimientos.take(3)
                    visibles.forEach { c: Consentimiento ->
                        FilaDato(
                            "${nombreConsentimiento(c.tipo)} (v${c.version})",
                            (if (c.aceptado) "Aceptado" else "Revocado") + " · " + fecha(c.createdAt)
                        )
                    }
                    if (consentimientos.size > 3) {
                        TextButton(onClick = { verHistorial = !verHistorial }) {
                            Text(
                                if (verHistorial) "Ver menos" else "Ver todo (${consentimientos.size})",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { showConfirmDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Eliminar todos mis datos",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
            Texto("Contacto para ejercer tus derechos: ${TextosLegales.CORREO_CONTACTO}")
        }
    }

    if (showRevocarDialog) {
        AlertDialog(
            onDismissRequest = { showRevocarDialog = false },
            title = { Text("Dejar de compartir", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Se dejarán de enviar datos y se borrará la copia anónima que ya compartiste. Tus datos en el teléfono no cambian.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRevocarDialog = false
                    viewModel.cambiarCompartirNube(false)
                }) {
                    Text("Dejar de compartir", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevocarDialog = false }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "Eliminar todos tus datos",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Se borrarán de forma permanente tu perfil, registros de salud, resultados de tests y consentimientos, y también la copia anónima si la compartías. Esta acción no se puede deshacer.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.eliminarTodo {
                            onDataDeleted()
                        }
                    }
                ) {
                    Text(
                        text = "Eliminar",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false }
                ) {
                    Text(
                        text = "Cancelar",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
private fun Tarjeta(titulo: String, contenido: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, BorderGray),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = titulo,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            contenido()
        }
    }
}

@Composable
private fun Texto(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun FilaDato(etiqueta: String, valor: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = etiqueta,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = valor,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.8f)
        )
    }
}
