package com.inacap.health.ui.screens.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inacap.health.cuenta.CuentaRepositorio
import com.inacap.health.cuenta.EstadoCuenta
import com.inacap.health.cuenta.ReglasCuenta
import com.inacap.health.cuenta.ResultadoCuenta
import com.inacap.health.ui.components.BarraSuperior
import com.inacap.health.ui.components.Tarjeta
import com.inacap.health.ui.components.TextoSecundario
import kotlinx.coroutines.launch

/**
 * Crear cuenta / iniciar sesión con correo (opcional).
 * @param desdeBienvenida se muestra justo después de aceptar los términos, con "Seguir sin cuenta".
 */
@Composable
fun AccesoScreen(desdeBienvenida: Boolean, onListo: () -> Unit, onBack: () -> Unit, repositorio: CuentaRepositorio? = null) {
    val context = LocalContext.current
    val repo = remember { repositorio ?: CuentaRepositorio(context) }
    val scope = rememberCoroutineScope()
    var pestana by rememberSaveable { mutableIntStateOf(0) }       // 0 = crear, 1 = iniciar sesión
    var email by rememberSaveable { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }               // nunca se guarda en el estado persistente
    var repetir by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var pendiente by remember { mutableStateOf((repo.estado() as? EstadoCuenta.Pendiente)?.email) }
    var recuperando by remember { mutableStateOf(false) }

    fun ejecutar(accion: suspend () -> ResultadoCuenta) {
        ocupado = true; mensaje = null
        scope.launch {
            when (val r = accion()) {
                ResultadoCuenta.Listo -> { contrasena = ""; repetir = ""; onListo() }
                is ResultadoCuenta.ConfirmaCorreo -> pendiente = r.email
                is ResultadoCuenta.Error -> mensaje = r.mensaje
            }
            ocupado = false
        }
    }

    Scaffold(topBar = { BarraSuperior(if (desdeBienvenida) "Tu cuenta (opcional)" else "Cuenta con correo", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (repo.estado() == EstadoCuenta.NoDisponible) {
                Tarjeta { TextoSecundario("Las cuentas no están disponibles en esta versión.") }
                Boton("Continuar") { onListo() }
                return@Column
            }

            // ----- Esperando que confirme el correo -----
            pendiente?.let { correo ->
                Tarjeta(titulo = "Confirma tu correo") {
                    TextoSecundario("Te enviamos un enlace a $correo. Ábrelo desde tu correo y luego vuelve aquí.")
                    TextoSecundario("Escribe la contraseña que quieres usar para terminar de crear la cuenta.")
                }
                CampoContrasena("Contraseña", contrasena) { contrasena = it }
                Boton(if (ocupado) "Revisando…" else "Ya confirmé mi correo", habilitado = !ocupado && ReglasCuenta.contrasenaValida(contrasena)) {
                    ejecutar { repo.completarRegistro(contrasena).also { if (it is ResultadoCuenta.ConfirmaCorreo) mensaje = "Aún no vemos la confirmación. Abre el enlace del correo y vuelve a intentar." } }
                }
                mensaje?.let { TextoSecundario(it) }
                if (desdeBienvenida) TextButton(onClick = onListo) { Text("Terminar después", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                return@Column
            }

            if (desdeBienvenida) {
                TextoSecundario("Con una cuenta puedes recuperar tus registros si cambias de teléfono. Sin cuenta, la app funciona igual y tus datos quedan solo en este teléfono.")
            }

            SecondaryTabRow(selectedTabIndex = pestana, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Crear cuenta", "Iniciar sesión").forEachIndexed { i, t ->
                    Tab(selected = pestana == i, onClick = { pestana = i; mensaje = null; recuperando = false }, text = {
                        Text(t, color = if (pestana == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    })
                }
            }

            OutlinedTextField(
                value = email, onValueChange = { email = it.trim().take(120) }, label = { Text("Correo") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth()
            )

            if (recuperando) {
                TextoSecundario("Te enviaremos un correo con un enlace para crear una contraseña nueva.")
                Boton("Enviar enlace", habilitado = !ocupado && ReglasCuenta.emailValido(email)) {
                    ocupado = true
                    scope.launch {
                        mensaje = when (val r = repo.recuperarContrasena(email)) {
                            is ResultadoCuenta.Error -> r.mensaje
                            else -> "Listo. Si ese correo tiene cuenta, recibirás el enlace en unos minutos."
                        }
                        ocupado = false
                    }
                }
                TextButton(onClick = { recuperando = false; mensaje = null }) { Text("Volver", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                CampoContrasena("Contraseña", contrasena) { contrasena = it }
                if (pestana == 0) {
                    CampoContrasena("Repite la contraseña", repetir) { repetir = it }
                    TextoSecundario("Al menos 8 caracteres, con letras y números.")
                    Tarjeta {
                        TextoSecundario("Al crear una cuenta, tus registros (IMC, agua, pasos, pulso y puntaje SCOFF) se respaldan de forma segura asociados a tu correo para que puedas recuperarlos. Tu nombre, patologías, alergias, medicamentos y peso nunca se suben.")
                    }
                    val valido = ReglasCuenta.emailValido(email) && ReglasCuenta.contrasenaValida(contrasena) && contrasena == repetir
                    Boton(if (ocupado) "Creando…" else "Crear cuenta", habilitado = valido && !ocupado) { ejecutar { repo.crearCuenta(email, contrasena) } }
                    if (repetir.isNotEmpty() && repetir != contrasena) TextoSecundario("Las contraseñas no coinciden.")
                } else {
                    TextoSecundario("Recuperarás en este teléfono lo que tu cuenta tiene respaldado.")
                    Boton(if (ocupado) "Entrando…" else "Iniciar sesión", habilitado = ReglasCuenta.emailValido(email) && contrasena.isNotEmpty() && !ocupado) {
                        ejecutar { repo.iniciarSesion(email, contrasena) }
                    }
                    TextButton(onClick = { recuperando = true; mensaje = null }) { Text("¿Olvidaste tu contraseña?", color = MaterialTheme.colorScheme.primary) }
                }
            }
            mensaje?.let { Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Start) }

            if (desdeBienvenida) {
                TextButton(onClick = onListo, modifier = Modifier.fillMaxWidth()) {
                    Text("Seguir sin cuenta", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CampoContrasena(etiqueta: String, valor: String, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor, onValueChange = { onCambio(it.take(72)) }, label = { Text(etiqueta) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Boton(texto: String, habilitado: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = habilitado,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
    ) { Text(texto, fontWeight = FontWeight.Bold) }
}
