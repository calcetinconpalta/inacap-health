package com.inacap.health.ui.screens.seguridad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.inacap.health.seguridad.Biometria
import com.inacap.health.seguridad.BloqueoManager
import com.inacap.health.seguridad.Pin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cubre toda la app mientras está bloqueada: no se ve ni se puede tocar nada debajo. */
@Composable
fun PantallaBloqueo(bloqueo: BloqueoManager, onOlvidePin: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var verificando by remember { mutableStateOf(false) }
    var espera by remember { mutableLongStateOf(bloqueo.esperaRestante()) }
    var dialogoOlvido by remember { mutableStateOf(false) }
    val conBiometria = bloqueo.biometriaActiva && Biometria.disponible(context)

    fun pedirBiometria() {
        (context as? FragmentActivity)?.let { act ->
            Biometria.autenticar(act, onExito = { bloqueo.desbloquear() }, onFallo = { e -> if (e != null) mensaje = e })
        }
    }

    LaunchedEffect(Unit) { if (conBiometria) pedirBiometria() }
    LaunchedEffect(espera) {
        while (espera > 0) { delay(1000); espera = bloqueo.esperaRestante() }
    }

    fun verificar() {
        if (pin.length < Pin.LARGO_MIN || verificando) return
        verificando = true
        val intento = pin
        scope.launch {
            val ok = withContext(Dispatchers.Default) { bloqueo.verificarPin(intento) }
            verificando = false
            pin = ""
            if (ok) bloqueo.desbloquear() else {
                espera = bloqueo.esperaRestante()
                mensaje = if (espera > 0) null else "PIN incorrecto"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("INACAP Health está bloqueada", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text("Ingresa tu PIN para ver tus datos", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        PuntosPin(pin.length)
        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                espera > 0 -> "Demasiados intentos. Espera ${(espera + 999) / 1000} s."
                verificando -> "Verificando…"
                else -> mensaje ?: " "
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        TecladoPin(
            onDigito = { d ->
                mensaje = null
                if (pin.length < Pin.LARGO_MAX) pin += d
                if (pin.length == Pin.LARGO_MAX) verificar()
            },
            onBorrar = { pin = pin.dropLast(1) },
            habilitado = espera == 0L && !verificando,
            accionExtra = if (conBiometria) "Huella" to { pedirBiometria() } else null
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { verificar() }, enabled = pin.length >= Pin.LARGO_MIN && espera == 0L) {
            Text("Desbloquear", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = { dialogoOlvido = true }) {
            Text("¿Olvidaste tu PIN?", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (dialogoOlvido) {
        AlertDialog(
            onDismissRequest = { dialogoOlvido = false },
            title = { Text("¿Olvidaste tu PIN?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Por seguridad, el PIN no se puede recuperar. Puedes borrar todos los datos de la app y empezar de nuevo. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(onClick = { dialogoOlvido = false; onOlvidePin() }) {
                    Text("Borrar todo y empezar de nuevo", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogoOlvido = false }) { Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }
}
