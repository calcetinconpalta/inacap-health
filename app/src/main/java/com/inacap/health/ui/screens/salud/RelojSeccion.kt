package com.inacap.health.ui.screens.salud

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inacap.health.HealthApp
import com.inacap.health.data.RegistroBiometrico
import com.inacap.health.salud.EstadoReloj
import com.inacap.health.salud.RelojBluetooth
import com.inacap.health.ui.components.Tarjeta
import com.inacap.health.ui.components.TextoSecundario
import kotlinx.coroutines.launch

/** Conexión directa por Bluetooth con relojes y bandas que transmiten la frecuencia cardíaca. */
@Composable
fun RelojBluetoothSeccion() {
    val context = LocalContext.current
    val app = context.applicationContext as HealthApp
    val reloj = remember { RelojBluetooth(context.applicationContext) }
    val estado by reloj.estado.collectAsState()
    val scope = rememberCoroutineScope()
    var aviso by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { reloj.detenerBusqueda(); reloj.desconectar() } }

    val encender = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { reloj.buscar() }
    val permisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) {
            if (reloj.encendido) reloj.buscar() else runCatching { encender.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
        } else aviso = "Sin el permiso de Bluetooth no podemos buscar tu reloj. Puedes darlo cuando quieras."
    }
    fun empezar() {
        aviso = null
        when {
            !reloj.disponible -> aviso = "Este teléfono no tiene Bluetooth de bajo consumo."
            !reloj.tienePermisos() -> permisos.launch(reloj.permisosNecesarios)
            !reloj.encendido -> runCatching { encender.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                .onFailure { aviso = "Enciende el Bluetooth del teléfono." }
            else -> reloj.buscar()
        }
    }

    Tarjeta(titulo = "Conectar por Bluetooth", modifier = Modifier.animateContentSize()) {
        when (val e = estado) {
            EstadoReloj.Inactivo, is EstadoReloj.Problema -> {
                TextoSecundario("Funciona con bandas de pecho y relojes que tengan la opción de transmitir la frecuencia cardíaca (mira la guía de tu marca más abajo).")
                if (e is EstadoReloj.Problema) Text(e.mensaje, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                BotonPrincipal("Buscar mi reloj") { empezar() }
                reloj.ultimoReloj()?.let { (dir, nombre) ->
                    if (reloj.tienePermisos()) TextButton(onClick = { reloj.conectar(dir, nombre) }) { Text("Reconectar con $nombre") }
                }
            }
            is EstadoReloj.Buscando -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    TextoSecundario("Buscando… deja el reloj cerca y con la transmisión activada.")
                }
                e.encontrados.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clickable { reloj.conectar(r.direccion, r.nombre) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(r.nombre, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(if (r.senal > -70) "Señal buena" else "Señal débil", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                TextButton(onClick = { reloj.detenerBusqueda(); reloj.desconectar() }) { Text("Cancelar") }
            }
            is EstadoReloj.Conectando -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                TextoSecundario("Conectando con ${e.nombre}…")
            }
            is EstadoReloj.Conectado -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Column {
                        Text(e.bpm?.let { "$it lpm" } ?: "Esperando pulso…", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        TextoSecundario(buildString {
                            append(e.nombre)
                            e.bateria?.let { append(" · batería $it %") }
                            if (e.contacto == false) append(" · ajusta el reloj a tu muñeca")
                        })
                    }
                }
                BotonPrincipal("Guardar en mi historial", habilitado = reloj.promedioReciente() != null) {
                    val bpm = reloj.promedioReciente() ?: return@BotonPrincipal
                    scope.launch {
                        val reg = RegistroBiometrico(pesoKg = null, estaturaCm = null, imc = null, aguaMl = 0, frecuenciaCardiaca = bpm, fuente = "reloj")
                        app.database.registroBiometricoDao().insert(reg)
                        app.sync.encolarRegistro(reg)
                        app.sync.registrarEvento("pulso_medido", mapOf("calidad" to "reloj"))
                        aviso = "Guardado: $bpm lpm (promedio de los últimos latidos)."
                    }
                }
                TextButton(onClick = { reloj.desconectar() }) { Text("Desconectar", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        aviso?.let { TextoSecundario(it) }
    }
}

private data class GuiaMarca(val marca: String, val bluetooth: String?, val healthConnect: String?)

private val GUIAS = listOf(
    GuiaMarca("Xiaomi / Redmi (Mi Band)",
        "En la banda: Ajustes → Frecuencia cardíaca → \"Transmitir frecuencia cardíaca\" (o desde Mi Fitness → tu dispositivo → \"Compartir frecuencia cardíaca\").",
        "Mi Fitness → Perfil → Health Connect → activar pasos y frecuencia cardíaca."),
    GuiaMarca("Amazfit (Zepp)",
        "En el reloj: Ajustes → \"Compartir frecuencia cardíaca\" / \"Heart rate sharing\".",
        "Zepp → Perfil → Agregar cuentas → Health Connect."),
    GuiaMarca("Samsung Galaxy Watch",
        null,
        "Samsung Health → ⋮ → Ajustes → Health Connect → permitir pasos y frecuencia cardíaca."),
    GuiaMarca("Google Pixel Watch / Fitbit",
        null,
        "App Fitbit → Tú → Ajustes de la app → Health Connect → activar."),
    GuiaMarca("Garmin",
        "En el reloj: mantén Menú → Sensores → Frecuencia cardíaca → \"Transmitir FC\" (Broadcast heart rate).",
        null),
    GuiaMarca("Huawei / Honor",
        "Algunos modelos: Huawei Health → tu reloj → \"Compartir datos de FC\" (HR data broadcasts).",
        null),
    GuiaMarca("Polar, Coros, Wahoo y bandas de pecho",
        "Se conectan directo: póntela y toca \"Buscar mi reloj\".",
        null),
)

/** Guía corta por marca: qué camino usar para cada reloj. */
@Composable
fun GuiaRelojesPorMarca() {
    var abierta by remember { mutableStateOf<String?>(null) }
    Tarjeta(titulo = "Guía por marca") {
        TextoSecundario("Cada marca usa su propio sistema. Elige la tuya para ver cómo conectarla. Los menús pueden cambiar un poco según el modelo.")
        GUIAS.forEach { g ->
            val abiertaEsta = abierta == g.marca
            Row(
                Modifier.fillMaxWidth().clickable { abierta = if (abiertaEsta) null else g.marca }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(g.marca, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Icon(if (abiertaEsta) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (abiertaEsta) Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                g.bluetooth?.let { Text("Bluetooth (pulso en vivo): $it", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface) }
                g.healthConnect?.let { Text("Health Connect (pasos y pulso del día): $it", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface) }
                if (g.bluetooth == null) TextoSecundario("No transmite el pulso por Bluetooth estándar: usa Health Connect.")
                if (g.healthConnect == null) TextoSecundario("Su app no comparte con Health Connect: usa la conexión por Bluetooth.")
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun BotonPrincipal(texto: String, habilitado: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = habilitado,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
    ) { Text(texto, fontWeight = FontWeight.Bold) }
}
