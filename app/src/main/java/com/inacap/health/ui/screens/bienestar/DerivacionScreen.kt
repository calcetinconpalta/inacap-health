package com.inacap.health.ui.screens.bienestar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inacap.health.data.remote.NubeConfig
import com.inacap.health.ui.theme.BorderGray
import androidx.compose.material3.OutlinedTextField
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val CANALES_DERIVACION = listOf(
    "Bienestar Estudiantil (atención presencial)",
    "Bienestar Estudiantil (contacto por correo)",
    "CESFAM (orientación hacia la red de salud pública)"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DerivacionScreen(
    /** contacto = datos para que Bienestar llame/escriba (se cifran de extremo a extremo), o null. */
    onSolicitudConfirmada: (canal: String, codigo: String, contacto: JSONObject?) -> Unit = { _, _, _ -> },
    onVerMensajes: () -> Unit = {},
    onBack: () -> Unit
) {
    var canalSeleccionado by rememberSaveable { mutableStateOf<String?>(null) }
    var autorizado by rememberSaveable { mutableStateOf(false) }
    var solicitudConfirmada by rememberSaveable { mutableStateOf(false) }
    var quiereContacto by rememberSaveable { mutableStateOf(false) }
    var nombreContacto by rememberSaveable { mutableStateOf("") }
    var medioContacto by rememberSaveable { mutableStateOf("") }
    var mensajeContacto by rememberSaveable { mutableStateOf("") }
    var conContacto by rememberSaveable { mutableStateOf(false) }
    val contactoDisponible = NubeConfig.actual.derivacionCifradaDisponible
    val contactoValido = !quiereContacto || (nombreContacto.isNotBlank() && medioContacto.trim().length >= 6)

    val codigoAtencion = rememberSaveable {
        "INH-" + (1..6).map { "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random() }.joinToString("")
    }

    val fechaHora = rememberSaveable {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apoyo de Bienestar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!solicitudConfirmada) {
                // Texto introductorio
                if (NubeConfig.sqlConfigurado) {
                    OutlinedButton(onClick = onVerMensajes, modifier = Modifier.fillMaxWidth()) {
                        Text("Ver mensajes del equipo de Bienestar", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Text(
                    text = "Puedes pedir apoyo al equipo de Bienestar de forma confidencial. Tú decides si quieres hacerlo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Selecciona un canal de atención",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Tarjetas tipo radio
                CANALES_DERIVACION.forEach { canal ->
                    val seleccionado = canalSeleccionado == canal
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { canalSeleccionado = canal },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (seleccionado) MaterialTheme.colorScheme.primary else BorderGray
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = seleccionado,
                                onClick = { canalSeleccionado = canal },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = canal,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (seleccionado) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                // Checkbox obligatorio
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { autorizado = !autorizado },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = autorizado,
                        onCheckedChange = { autorizado = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = "Autorizo que se comparta mi solicitud de apoyo con el equipo seleccionado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // Contacto directo (opcional), cifrado de extremo a extremo
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, BorderGray)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = contactoDisponible) { quiereContacto = !quiereContacto },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = quiereContacto && contactoDisponible,
                                onCheckedChange = { quiereContacto = it },
                                enabled = contactoDisponible,
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                "Quiero que Bienestar Estudiantil me contacte (opcional)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Text(
                            if (contactoDisponible) "Tus datos de contacto se cifran en tu teléfono: solo el equipo de Bienestar Estudiantil puede leerlos. Ni la app ni sus administradores pueden verlos."
                            else "El contacto directo no está configurado en esta versión de prueba.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (quiereContacto && contactoDisponible) {
                            OutlinedTextField(
                                value = nombreContacto, onValueChange = { nombreContacto = it.take(60) },
                                label = { Text("¿Cómo te llamamos?") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = medioContacto, onValueChange = { medioContacto = it.take(80) },
                                label = { Text("Correo o teléfono") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = mensajeContacto, onValueChange = { mensajeContacto = it.take(500) },
                                label = { Text("Mensaje breve (opcional)") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f, fill = false))

                // Botón Confirmar solicitud
                Button(
                    onClick = {
                        solicitudConfirmada = true
                        conContacto = quiereContacto && contactoDisponible
                        val contacto = if (conContacto) JSONObject()
                            .put("nombre", com.inacap.health.util.Saneamiento.linea(nombreContacto, 60))
                            .put("contacto", com.inacap.health.util.Saneamiento.linea(medioContacto, 80))
                            .put("mensaje", com.inacap.health.util.Saneamiento.texto(mensajeContacto, 500))
                            .put("codigo", codigoAtencion)
                            else null
                        canalSeleccionado?.let { onSolicitudConfirmada(it, codigoAtencion, contacto) }
                    },
                    enabled = canalSeleccionado != null && autorizado && contactoValido,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Confirmar solicitud")
                }
            } else {
                // Comprobante
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, BorderGray)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Solicitud registrada",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Canal: ${canalSeleccionado ?: ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Código: $codigoAtencion",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Fecha: $fechaHora",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = if (conContacto) "Tu solicitud se envía cifrada de extremo a extremo apenas haya internet. Bienestar Estudiantil te contactará al medio que indicaste; menciona tu código si te lo piden."
                            else "No dejaste datos de contacto, así que nadie te contactará: puedes acercarte a Bienestar Estudiantil con tu código. Si activaste \"Compartir datos anonimizados\", solo se registra el canal (sin tu nombre) para estadísticas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (NubeConfig.sqlConfigurado) OutlinedButton(onClick = onVerMensajes, modifier = Modifier.fillMaxWidth()) {
                    Text("Ver mensajes de Bienestar", color = MaterialTheme.colorScheme.onBackground)
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    border = BorderStroke(1.dp, BorderGray)
                ) {
                    Text("Volver", color = MaterialTheme.colorScheme.onBackground)
                }

                Spacer(modifier = Modifier.weight(1f, fill = false))

                Text(
                    text = "Si sientes que necesitas ayuda urgente, acude a un servicio de urgencia o llama a Salud Responde: 600 360 7777.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
