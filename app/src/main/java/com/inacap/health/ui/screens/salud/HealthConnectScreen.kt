package com.inacap.health.ui.screens.salud

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inacap.health.salud.HealthConnectDisponibilidad
import com.inacap.health.salud.HealthConnectDisponibilidad.Estado
import com.inacap.health.salud.HealthConnectRepositorio
import com.inacap.health.ui.components.BarraSuperior
import com.inacap.health.ui.components.FilaValor
import com.inacap.health.ui.components.Tarjeta
import com.inacap.health.ui.components.TextoSecundario
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** HU-07 · Conectar el reloj: por Bluetooth (pulso en vivo) o a través de Health Connect (pasos y pulso del día). */
@Composable
fun HealthConnectScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val estado = remember { HealthConnectDisponibilidad.estado(context) }
    var vinculado by remember { mutableStateOf(HealthConnectDisponibilidad.vinculado(context)) }
    var ultima by remember { mutableStateOf(HealthConnectDisponibilidad.ultimaSincronizacion(context)) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }

    Scaffold(topBar = { BarraSuperior("Mi reloj", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextoSecundario("Hay dos formas de conectar tu reloj. Puedes usar una o las dos.")
            RelojBluetoothSeccion()
            Text("Health Connect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            TextoSecundario("Reúne los datos que guarda la app de tu reloj (Mi Fitness, Zepp, Samsung Health, Fitbit…). INACAP Health solo LEE tus pasos y tu frecuencia cardíaca; nunca escribe ni borra nada ahí.")
            when (estado) {
                Estado.NO_SOPORTADO -> Tarjeta { TextoSecundario("Health Connect no está disponible en este teléfono.") }
                Estado.NO_INSTALADO, Estado.ACTUALIZAR -> Tarjeta(titulo = if (estado == Estado.ACTUALIZAR) "Actualiza Health Connect" else "Instala Health Connect") {
                    TextoSecundario("Se descarga gratis desde Google Play.")
                    Button(onClick = {
                        val uri = "market://details?id=${HealthConnectDisponibilidad.PAQUETE}&url=healthconnect%3A%2F%2Fonboarding".toUri()
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending")) }
                        catch (_: ActivityNotFoundException) { mensaje = "No se encontró Google Play." }
                    }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Abrir Google Play") }
                }
                Estado.DISPONIBLE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val repo = remember { HealthConnectRepositorio(context) }
                    fun importar() {
                        ocupado = true
                        scope.launch {
                            mensaje = try {
                                val n = repo.importar()
                                vinculado = true
                                ultima = System.currentTimeMillis()
                                if (n == 0) "Vinculado. No encontramos pasos en los últimos 30 días." else "Listo: $n días importados."
                            } catch (e: Exception) { "No se pudo leer Health Connect (${e.javaClass.simpleName})." }
                            ocupado = false
                        }
                    }
                    val pedir = rememberLauncherForActivityResult(repo.contratoPermisos()) { otorgados ->
                        if (otorgados.containsAll(repo.permisos)) importar()
                        else mensaje = "Sin permisos no podemos leer tus datos. Puedes intentarlo de nuevo cuando quieras."
                    }
                    Tarjeta(titulo = if (vinculado) "Vinculado" else "Vincular mi reloj") {
                        if (vinculado) {
                            FilaValor("Última sincronización", if (ultima > 0) SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-CL")).format(Date(ultima)) else "—")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { importar() }, enabled = !ocupado, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                                    Text(if (ocupado) "Sincronizando…" else "Sincronizar ahora")
                                }
                                TextButton(onClick = {
                                    scope.launch { repo.desvincular(); vinculado = false; mensaje = "Desvinculado. Los datos ya importados siguen en tu historial." }
                                }) { Text("Desvincular", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        } else {
                            TextoSecundario("Se pedirán dos permisos de lectura: pasos y frecuencia cardíaca.")
                            Button(
                                onClick = { pedir.launch(repo.permisos) }, enabled = !ocupado,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) { Text("Vincular", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            mensaje?.let { TextoSecundario(it) }
            GuiaRelojesPorMarca()
            OutlinedButton(onClick = onBack) { Text("Volver", color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}
