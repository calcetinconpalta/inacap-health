package com.inacap.health.ui.screens.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.inacap.health.HealthApp
import com.inacap.health.cuenta.CuentaRepositorio
import com.inacap.health.cuenta.EstadoCuenta
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import com.inacap.health.ui.components.BarraSuperior
import com.inacap.health.ui.components.FilaValor
import com.inacap.health.ui.components.Tarjeta
import com.inacap.health.ui.components.TextoSecundario
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cuenta del estudiante: anónima o con correo, y estado del envío de datos. */
@Composable
fun CuentaScreen(onBack: () -> Unit, onAcceso: () -> Unit = {}, onIniciarSesion: () -> Unit = {}, onCerrarSesion: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as HealthApp
    val pendientes by app.sync.pendientes.collectAsState(initial = 0)
    val scope = rememberCoroutineScope()
    var estado by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var diagnostico by remember { mutableStateOf<List<com.inacap.health.data.sync.SyncManager.Diagnostico>>(emptyList()) }
    val fecha = { ms: Long -> if (ms <= 0) "—" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-CL")).format(Date(ms)) }

    Scaffold(topBar = { BarraSuperior("Cuenta", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val estadoCuenta = remember { CuentaRepositorio(app).estado() }
            var confirmarSalida by remember { mutableStateOf(false) }
            Tarjeta(titulo = "Tu cuenta") {
                when (estadoCuenta) {
                    is EstadoCuenta.Registrada -> {
                        FilaValor("Correo", estadoCuenta.email)
                        TextoSecundario("Tus registros se respaldan en tu cuenta y puedes recuperarlos al iniciar sesión en otro teléfono.")
                        OutlinedButton(onClick = { confirmarSalida = true }) { Text("Cerrar sesión", color = MaterialTheme.colorScheme.onSurface) }
                    }
                    is EstadoCuenta.Pendiente -> {
                        TextoSecundario("Falta confirmar el correo ${estadoCuenta.email}.")
                        Button(onClick = onAcceso, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Terminar de crear la cuenta") }
                    }
                    EstadoCuenta.Anonima -> {
                        TextoSecundario("Usas la app sin correo (cuenta anónima). Si creas una cuenta, podrás recuperar tus registros al cambiar de teléfono.")
                        Button(onClick = onAcceso, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Crear cuenta o iniciar sesión") }
                    }
                    EstadoCuenta.NoDisponible -> TextoSecundario("Las cuentas no están disponibles en esta versión: la app funciona sin cuenta.")
                }
                com.inacap.health.ui.components.CodigoOculto("Código anónimo", app.sync.sesion.sbUid)
            }
            if (estadoCuenta != EstadoCuenta.NoDisponible) Tarjeta(titulo = "¿Eres del equipo?") {
                TextoSecundario("Tutores pares, docentes supervisores, equipo de Bienestar y administración entran con su cuenta y la app abre el modo equipo según su rol.")
                if (estadoCuenta is EstadoCuenta.Registrada) {
                    TextoSecundario("Para entrar con una cuenta del equipo, primero cierra tu sesión de estudiante.")
                } else {
                    OutlinedButton(onClick = onIniciarSesion) { Text("Iniciar sesión del equipo", color = MaterialTheme.colorScheme.onSurface) }
                }
            }
            if (confirmarSalida) AlertDialog(
                onDismissRequest = { confirmarSalida = false },
                title = { Text("Cerrar sesión", fontWeight = FontWeight.Bold) },
                text = { Text("Tus registros quedan guardados en tu cuenta. En este teléfono se borrarán, para que nadie más los vea. Podrás recuperarlos al volver a iniciar sesión.") },
                confirmButton = { TextButton(onClick = { confirmarSalida = false; onCerrarSesion() }) { Text("Cerrar sesión", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) } },
                dismissButton = { TextButton(onClick = { confirmarSalida = false }) { Text("Cancelar") } }
            )
            Tarjeta(titulo = "Respaldo y envío de datos") {
                FilaValor("Compartir datos anonimizados", if (app.sync.sesion.compartirNube) "Activado" else "Desactivado")
                FilaValor("Envíos pendientes", pendientes.toString())
                FilaValor("Último envío", fecha(app.sync.sesion.ultimoEnvio))
                app.sync.sesion.ultimoErrorSync?.let { error ->
                    val codigo = Regex("HTTP (\\d{3})").find(error)?.groupValues?.get(1)?.toIntOrNull()
                    // Mensaje simple + el error real bajo «Ver detalle del error».
                    com.inacap.health.ui.components.DetalleError(
                        resumen = "Último problema: " + com.inacap.health.data.remote.ExplicacionError.paraPersona(error, if (error.startsWith("Sin respuesta")) -1 else codigo),
                        error = error
                    )
                }
                if (!app.sync.sesion.compartirNube) {
                    TextoSecundario("Mientras esté desactivado no se envían estadísticas. Actívalo en Perfil → Privacidad y mis datos.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            ocupado = true; estado = "Enviando…"
                            scope.launch { estado = app.sync.sincronizarAhora(); ocupado = false }
                        },
                        enabled = !ocupado,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Sincronizar ahora") }
                    if (com.inacap.health.BuildConfig.DEBUG) OutlinedButton(
                        onClick = {
                            ocupado = true; estado = "Probando conexión…"; diagnostico = emptyList()
                            scope.launch { diagnostico = app.sync.diagnosticar(); estado = null; ocupado = false }
                        },
                        enabled = !ocupado
                    ) { Text("Probar conexión", color = MaterialTheme.colorScheme.onSurface) }
                }
                estado?.let { TextoSecundario(it) }
                diagnostico.forEach { d ->
                    FilaValor(d.etiqueta, if (d.errorTecnico == null) d.resultado else "Con problemas")
                    d.errorTecnico?.let { com.inacap.health.ui.components.DetalleError(resumen = d.resultado, error = it) }
                }
            }
        }
    }
}
