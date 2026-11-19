package com.inacap.health.ui.screens.seguridad

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inacap.health.HealthApp
import com.inacap.health.seguridad.Biometria
import com.inacap.health.seguridad.Pin
import com.inacap.health.ui.components.BarraSuperior
import com.inacap.health.ui.components.Tarjeta
import com.inacap.health.ui.components.TextoSecundario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Paso { RESUMEN, VERIFICAR_ACTUAL, NUEVO, CONFIRMAR }
private enum class Objetivo { CREAR, CAMBIAR, QUITAR }

/** HU-08 · Configurar el bloqueo con PIN, biometría y tiempo de expiración. */
@Composable
fun BloqueoConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val bloqueo = remember { (context.applicationContext as HealthApp).bloqueo }
    val scope = rememberCoroutineScope()
    var tienePin by remember { mutableStateOf(bloqueo.tienePin) }
    var biometria by remember { mutableStateOf(bloqueo.biometriaActiva) }
    var minutos by remember { mutableIntStateOf(bloqueo.minutosExpiracion) }
    var paso by remember { mutableStateOf(Paso.RESUMEN) }
    var objetivo by remember { mutableStateOf(Objetivo.CREAR) }
    var pin by remember { mutableStateOf("") }
    var primero by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    val bioDisponible = remember { Biometria.disponible(context) }

    fun empezar(o: Objetivo) {
        objetivo = o; pin = ""; primero = ""; mensaje = null
        paso = if (o == Objetivo.CREAR) Paso.NUEVO else Paso.VERIFICAR_ACTUAL
    }

    fun continuar() {
        when (paso) {
            Paso.VERIFICAR_ACTUAL -> {
                ocupado = true
                val intento = pin
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { bloqueo.verificarPin(intento) }
                    ocupado = false; pin = ""
                    when {
                        !ok -> mensaje = "PIN incorrecto"
                        objetivo == Objetivo.QUITAR -> {
                            bloqueo.quitarBloqueo(); tienePin = false; biometria = false
                            paso = Paso.RESUMEN; mensaje = "Bloqueo desactivado"
                        }
                        else -> { paso = Paso.NUEVO; mensaje = null }
                    }
                }
            }
            Paso.NUEVO -> when {
                !Pin.esValido(pin) -> mensaje = "Usa entre ${Pin.LARGO_MIN} y ${Pin.LARGO_MAX} dígitos"
                Pin.esTrivial(pin) -> { mensaje = "Ese PIN es muy fácil de adivinar"; pin = "" }
                else -> { primero = pin; pin = ""; paso = Paso.CONFIRMAR; mensaje = null }
            }
            Paso.CONFIRMAR -> {
                if (pin != primero) { mensaje = "Los PIN no coinciden, vuelve a intentarlo"; pin = ""; primero = ""; paso = Paso.NUEVO; return }
                ocupado = true
                val nuevo = pin
                scope.launch {
                    withContext(Dispatchers.Default) { bloqueo.configurarPin(nuevo) }
                    ocupado = false; pin = ""; tienePin = true; paso = Paso.RESUMEN
                    mensaje = if (objetivo == Objetivo.CREAR) "Bloqueo activado" else "PIN actualizado"
                }
            }
            Paso.RESUMEN -> Unit
        }
    }

    Scaffold(topBar = { BarraSuperior("Bloqueo de la app", onBack = { if (paso == Paso.RESUMEN) onBack() else paso = Paso.RESUMEN }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (paso != Paso.RESUMEN) {
                Text(
                    when (paso) {
                        Paso.VERIFICAR_ACTUAL -> "Ingresa tu PIN actual"
                        Paso.NUEVO -> "Elige un PIN de ${Pin.LARGO_MIN} a ${Pin.LARGO_MAX} dígitos"
                        else -> "Repite el PIN"
                    },
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                )
                PuntosPin(pin.length)
                Text(mensaje ?: " ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                TecladoPin(
                    onDigito = { if (pin.length < Pin.LARGO_MAX) pin += it; mensaje = null },
                    onBorrar = { pin = pin.dropLast(1) },
                    habilitado = !ocupado
                )
                Button(
                    onClick = { continuar() },
                    enabled = pin.length >= Pin.LARGO_MIN && !ocupado,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text(if (ocupado) "Un momento…" else "Continuar", fontWeight = FontWeight.Bold) }
                return@Column
            }

            Tarjeta(titulo = "Bloqueo con PIN") {
                TextoSecundario(
                    if (tienePin) "Activado. La app pide tu PIN al abrirse y al volver después de un tiempo sin uso."
                    else "Protege tus datos de salud si alguien más usa tu teléfono."
                )
                if (!tienePin) {
                    Button(
                        onClick = { empezar(Objetivo.CREAR) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Activar bloqueo", fontWeight = FontWeight.Bold) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { empezar(Objetivo.CAMBIAR) }) { Text("Cambiar PIN", color = MaterialTheme.colorScheme.onSurface) }
                        TextButton(onClick = { empezar(Objetivo.QUITAR) }) { Text("Desactivar", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            if (tienePin) {
                Tarjeta(titulo = "Huella o rostro") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextoSecundario(
                            if (bioDisponible) "Desbloquear con la biometría del teléfono (el PIN sigue funcionando)."
                            else "Tu teléfono no tiene huella o rostro configurado.",
                            Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = biometria && bioDisponible,
                            enabled = bioDisponible,
                            onCheckedChange = { biometria = it; bloqueo.biometriaActiva = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Tarjeta(titulo = "Bloquear después de") {
                    TextoSecundario("Tiempo con la app en segundo plano antes de pedir el PIN otra vez.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Al salir", 1 to "1 min", 5 to "5 min", 15 to "15 min").forEach { (m, etiqueta) ->
                            FilterChip(
                                selected = minutos == m,
                                onClick = { minutos = m; bloqueo.minutosExpiracion = m },
                                label = { Text(etiqueta) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }
            mensaje?.let { TextoSecundario(it) }
            TextoSecundario("Si olvidas el PIN no se puede recuperar: tendrás que borrar los datos de la app.")
        }
    }
}
