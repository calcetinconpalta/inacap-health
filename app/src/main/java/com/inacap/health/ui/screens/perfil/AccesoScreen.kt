package com.inacap.health.ui.screens.perfil

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inacap.health.HealthApp
import com.inacap.health.cuenta.CuentaRepositorio
import com.inacap.health.cuenta.EstadoCuenta
import com.inacap.health.cuenta.ReglasCuenta
import com.inacap.health.cuenta.ResultadoAcceso
import com.inacap.health.cuenta.ResultadoCuenta
import com.inacap.health.ui.components.BarraSuperior
import com.inacap.health.ui.components.Tarjeta
import com.inacap.health.ui.components.TextoSecundario
import kotlinx.coroutines.launch

/**
 * Inicio de sesión ÚNICO (estudiantes y equipo) y creación de cuenta con correo (opcional).
 * Tras iniciar sesión, el servidor decide el rol: una cuenta del equipo abre el modo equipo
 * (después de aceptar el compromiso de confidencialidad); una sin rol queda como estudiante.
 *
 * @param desdeBienvenida se muestra justo después de aceptar los términos, con "Seguir sin cuenta".
 * @param inicioSesion abre directo en «Iniciar sesión».
 * @param permitirEstudiante false si aún no se aceptan los términos de estudiante (entró por «Soy del equipo»):
 *        solo se puede entrar con una cuenta del equipo.
 */
@Composable
fun AccesoScreen(
    desdeBienvenida: Boolean,
    onListo: () -> Unit,
    onBack: () -> Unit,
    repositorio: CuentaRepositorio? = null,
    inicioSesion: Boolean = false,
    permitirEstudiante: Boolean = true,
    mensajeInicial: String? = null
) {
    val context = LocalContext.current
    val repo = remember { repositorio ?: CuentaRepositorio(context) }
    val scope = rememberCoroutineScope()
    val teclado = LocalSoftwareKeyboardController.current
    // 0 = crear cuenta (estudiante), 1 = iniciar sesión
    var pestana by rememberSaveable { mutableIntStateOf(if (inicioSesion || !permitirEstudiante) 1 else 0) }
    var email by rememberSaveable { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }               // nunca se guarda en el estado persistente
    var repetir by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf(mensajeInicial) }
    var ocupado by remember { mutableStateOf(false) }
    var pendiente by remember { mutableStateOf((repo.estado() as? EstadoCuenta.Pendiente)?.email) }
    var recuperando by remember { mutableStateOf(false) }
    var creandoEquipo by remember { mutableStateOf(false) }
    var equipoPorConfirmar by remember { mutableStateOf<ResultadoAcceso.Equipo?>(null) }

    fun ejecutar(accion: suspend () -> ResultadoCuenta) {
        teclado?.hide()                 // el teclado tapaba el mensaje al final de la pantalla
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

    fun entrar() {
        teclado?.hide()
        ocupado = true; mensaje = null
        scope.launch {
            when (val r = repo.acceder(email, contrasena, permitirEstudiante)) {
                ResultadoAcceso.Estudiante -> { contrasena = ""; onListo() }
                is ResultadoAcceso.Equipo -> { contrasena = ""; equipoPorConfirmar = r }
                is ResultadoAcceso.Error -> mensaje = r.mensaje
            }
            ocupado = false
        }
    }

    val titulo = when {
        !permitirEstudiante -> "Iniciar sesión del equipo"
        desdeBienvenida -> "Tu cuenta (opcional)"
        else -> "Iniciar sesión o crear cuenta"
    }

    Scaffold(topBar = { BarraSuperior(titulo, onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (repo.estado() == EstadoCuenta.NoDisponible) {
                Tarjeta { TextoSecundario("Las cuentas no están disponibles en esta versión.") }
                Boton("Continuar") { onListo() }
                return@Column
            }

            // ----- Esperando que confirme el correo (cuenta de estudiante) -----
            if (permitirEstudiante) pendiente?.let { correo ->
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

            when {
                !permitirEstudiante -> TextoSecundario("Para tutores pares, docentes supervisores, equipo de Bienestar y administración. Usa tu propio teléfono: mientras estés en modo equipo, la app no guarda ni envía datos de estudiante.")
                desdeBienvenida -> TextoSecundario("Con una cuenta puedes recuperar tus registros si cambias de teléfono. Sin cuenta, la app funciona igual y tus datos quedan solo en este teléfono.")
            }

            if (permitirEstudiante && !creandoEquipo) {
                SecondaryTabRow(selectedTabIndex = pestana, containerColor = MaterialTheme.colorScheme.background) {
                    listOf("Crear cuenta", "Iniciar sesión").forEachIndexed { i, t ->
                        Tab(selected = pestana == i, onClick = { pestana = i; mensaje = null; recuperando = false }, text = {
                            Text(t, color = if (pestana == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        })
                    }
                }
            }

            OutlinedTextField(
                value = email, onValueChange = { email = it.trim().take(120) }, label = { Text("Correo") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth()
            )

            when {
                recuperando -> {
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
                }

                creandoEquipo -> {
                    CampoContrasena("Contraseña", contrasena) { contrasena = it }
                    TextoSecundario("Al menos 8 caracteres, con letras y números. Después de crearla, un administrador te asigna tu rol y entras con «Iniciar sesión».")
                    Boton(if (ocupado) "Creando…" else "Crear cuenta del equipo", habilitado = !ocupado && ReglasCuenta.emailValido(email) && ReglasCuenta.contrasenaValida(contrasena)) {
                        teclado?.hide()
                        ocupado = true; mensaje = null
                        scope.launch {
                            mensaje = (context.applicationContext as HealthApp).equipo.crearCuentaEquipo(email, contrasena)
                            contrasena = ""; creandoEquipo = false; pestana = 1
                            ocupado = false
                        }
                    }
                    TextButton(onClick = { creandoEquipo = false; mensaje = null }) { Text("Volver", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }

                pestana == 0 -> {
                    CampoContrasena("Contraseña", contrasena) { contrasena = it }
                    CampoContrasena("Repite la contraseña", repetir) { repetir = it }
                    TextoSecundario("Al menos 8 caracteres, con letras y números.")
                    Tarjeta {
                        TextoSecundario("Al crear una cuenta, tus registros (IMC, agua, pasos, pulso y puntaje SCOFF) se respaldan de forma segura asociados a tu correo para que puedas recuperarlos. Tu nombre, patologías, alergias, medicamentos y peso nunca se suben.")
                    }
                    val valido = ReglasCuenta.emailValido(email) && ReglasCuenta.contrasenaValida(contrasena) && contrasena == repetir
                    Boton(if (ocupado) "Creando…" else "Crear cuenta", habilitado = valido && !ocupado) { ejecutar { repo.crearCuenta(email, contrasena) } }
                    if (repetir.isNotEmpty() && repetir != contrasena) TextoSecundario("Las contraseñas no coinciden.")
                }

                else -> {
                    CampoContrasena("Contraseña", contrasena) { contrasena = it }
                    TextoSecundario(
                        if (permitirEstudiante) "Estudiantes: recuperarás en este teléfono lo que tu cuenta tiene respaldado. Equipo: entrarás al modo equipo según tu rol."
                        else "Entrarás al modo equipo según el rol que te asignó un administrador."
                    )
                    Boton(if (ocupado) "Entrando…" else "Iniciar sesión", habilitado = ReglasCuenta.emailValido(email) && contrasena.isNotEmpty() && !ocupado) { entrar() }
                    TextButton(onClick = { recuperando = true; mensaje = null }) { Text("¿Olvidaste tu contraseña?", color = MaterialTheme.colorScheme.primary) }
                    TextButton(onClick = { creandoEquipo = true; mensaje = null }) {
                        Text("¿Eres del equipo y aún no tienes cuenta? Créala aquí", color = MaterialTheme.colorScheme.primary)
                    }
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

    equipoPorConfirmar?.let { r ->
        val app = context.applicationContext as HealthApp
        var acepta by remember { mutableStateOf(app.equipo.aceptoConfidencialidad) }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Cuenta del equipo: ${r.rol.nombre}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Esta cuenta pertenece al equipo, así que se abrirá el modo equipo (no la app de estudiante). Mientras lo uses, la app no guarda ni envía datos de estudiante.")
                    Row(Modifier.fillMaxWidth().clickable { acepta = !acepta }, verticalAlignment = Alignment.Top) {
                        Checkbox(acepta, { acepta = it }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Me comprometo a mantener la confidencialidad de lo que vea y a usarlo solo para orientar o atender a estudiantes.",
                            fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { equipoPorConfirmar = null; repo.confirmarEquipo(r) }, enabled = acepta) {
                    Text("Entrar al modo equipo", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    equipoPorConfirmar = null
                    scope.launch { repo.cancelarEquipo(r); mensaje = "No se abrió el modo equipo. Seguiste con tu sesión anterior." }
                }) { Text("Cancelar") }
            }
        )
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
