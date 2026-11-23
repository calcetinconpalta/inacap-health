package com.inacap.health.ui.screens.equipo

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inacap.health.data.remote.ExplicacionError
import com.inacap.health.equipo.ConversacionEquipo
import com.inacap.health.equipo.DerivacionEquipo
import com.inacap.health.equipo.EquipoRepositorio
import com.inacap.health.equipo.MensajeEquipo
import com.inacap.health.equipo.RolEquipo
import com.inacap.health.equipo.Supervision
import com.inacap.health.ui.components.BarraSuperior
import com.inacap.health.ui.components.BurbujaMensaje
import com.inacap.health.ui.components.FilaValor
import com.inacap.health.ui.components.Tarjeta
import com.inacap.health.ui.components.TextoSecundario
import com.inacap.health.ui.components.horaCorta
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TEMAS = mapOf(
    "alimentacion" to "Alimentación", "hidratacion" to "Hidratación", "actividad" to "Actividad física",
    "habitos" to "Hábitos y descanso", "otro" to "Otro", "apoyo_profesional" to "Atención profesional"
)
private val CANALES = mapOf("bienestar_presencial" to "Presencial", "bienestar_correo" to "Por correo", "cesfam" to "CESFAM")
private val ESTADOS_DER = listOf("pendiente" to "Pendiente", "en_contacto" to "En contacto", "atendida" to "Atendida")

private fun etiquetaEstudiante(id: String) = "Estudiante #" + id.take(6)

/** App del equipo: Solicitudes · Chats · Cuenta. */
@Composable
fun EquipoApp(repo: EquipoRepositorio, onSalir: () -> Unit) {
    val rol = repo.rol ?: return
    var pestana by remember { mutableIntStateOf(0) }
    var abierta by remember { mutableStateOf<String?>(null) }
    var convs by remember { mutableStateOf<List<ConversacionEquipo>>(emptyList()) }
    var ders by remember { mutableStateOf<List<DerivacionEquipo>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun recargar() {
        try {
            convs = repo.conversaciones()
            if (rol == RolEquipo.PROFESIONAL) ders = repo.derivaciones()
            error = null
        } catch (e: Exception) {
            error = ExplicacionError.explicar(e.message, (e as? com.inacap.health.data.remote.ErrorNube)?.codigo)
        }
    }
    LaunchedEffect(pestana, abierta) { while (true) { recargar(); delay(10_000) } }

    val misChats = convs.filter {
        when (rol) {
            RolEquipo.TUTOR -> it.tutorId == repo.miId
            RolEquipo.DOCENTE -> it.tipo == "orientacion"
            RolEquipo.PROFESIONAL -> it.tipo == "atencion" && it.profesionalId == repo.miId
            RolEquipo.ADMIN -> false
        }
    }
    val pendientes = when (rol) {
        RolEquipo.TUTOR -> convs.count { it.tutorId == null && it.estado == "abierta" }
        RolEquipo.DOCENTE -> convs.count { it.estado == "abierta" && it.tutorId == null }
        RolEquipo.PROFESIONAL -> ders.count { it.estado == "pendiente" }
        RolEquipo.ADMIN -> 0
    }

    // Botón atrás del teléfono: desde un chat vuelve a la lista; desde otra pestaña
    // vuelve a Solicitudes; solo desde la raíz (Solicitudes) sale de la app.
    BackHandler(enabled = abierta != null || pestana != 0) {
        if (abierta != null) { abierta = null; scope.launch { recargar() } } else pestana = 0
    }

    abierta?.let { id ->
        ChatEquipo(repo, rol, id, convs.firstOrNull { it.id == id }, onVolver = { abierta = null; scope.launch { recargar() } })
        return
    }

    Scaffold(
        topBar = { BarraSuperior("${rol.nombre} · ${repo.nombre}", onBack = {}, mostrarAtras = false) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val colores = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
                NavigationBarItem(pestana == 0, { pestana = 0 }, icon = {
                    BadgedBox(badge = { if (pendientes > 0) Badge { Text("$pendientes") } }) { Icon(Icons.Default.Notifications, null) }
                }, label = { Text("Solicitudes") }, colors = colores)
                NavigationBarItem(pestana == 1, { pestana = 1 }, icon = { Icon(Icons.Default.Email, null) }, label = { Text("Chats") }, colors = colores)
                NavigationBarItem(pestana == 2, { pestana = 2 }, icon = { Icon(Icons.Default.Person, null) }, label = { Text("Cuenta") }, colors = colores)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, modifier = Modifier.padding(16.dp)) }
            when (pestana) {
                0 -> Solicitudes(repo, rol, convs, ders, onAbrir = { abierta = it }, onCambio = { scope.launch { recargar() } })
                1 -> ListaChats(misChats, rol, onAbrir = { abierta = it })
                else -> CuentaEquipo(repo, rol, onSalir)
            }
        }
    }
}

@Composable
private fun Solicitudes(
    repo: EquipoRepositorio, rol: RolEquipo, convs: List<ConversacionEquipo>, ders: List<DerivacionEquipo>,
    onAbrir: (String) -> Unit, onCambio: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var mensaje by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (rol) {
            RolEquipo.TUTOR, RolEquipo.DOCENTE -> {
                val libres = convs.filter { it.tipo == "orientacion" && it.estado == "abierta" && it.tutorId == null }
                item {
                    Resumen(
                        listOf("Sin asignar" to libres.size, "Abiertas" to convs.count { it.estado == "abierta" }, "Derivadas" to convs.count { it.estado == "derivada" })
                    )
                }
                item { Titulo(if (rol == RolEquipo.TUTOR) "Consultas esperando tutor" else "Consultas sin tutor asignado") }
                if (libres.isEmpty()) item { TextoSecundario("No hay consultas pendientes. ¡Buen trabajo!") }
                items(libres, key = { it.id }) { c ->
                    Tarjeta(titulo = TEMAS[c.tema] ?: c.tema) {
                        TextoSecundario("${etiquetaEstudiante(c.id)} · ${horaCorta(c.actualizadaEn)}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (rol == RolEquipo.TUTOR) Button(
                                onClick = { scope.launch { try { repo.tomar(c.id); onAbrir(c.id) } catch (e: Exception) { mensaje = "Otra persona ya la tomó." ; onCambio() } } },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) { Text("Tomar consulta") }
                            OutlinedButton(onClick = { onAbrir(c.id) }) { Text("Ver", color = MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                }
                if (rol == RolEquipo.DOCENTE) {
                    item { Titulo("Derivadas recientemente") }
                    items(convs.filter { it.estado == "derivada" }.take(10), key = { "d" + it.id }) { c ->
                        Tarjeta(modifier = Modifier.clickable { onAbrir(c.id) }, titulo = TEMAS[c.tema] ?: c.tema) {
                            TextoSecundario("${etiquetaEstudiante(c.id)} · ${horaCorta(c.actualizadaEn)}")
                        }
                    }
                }
            }
            RolEquipo.PROFESIONAL -> {
                item {
                    Resumen(ESTADOS_DER.map { (k, n) -> n to ders.count { it.estado == k } })
                }
                item { Titulo("Solicitudes de apoyo") }
                if (ders.isEmpty()) item { TextoSecundario("No hay solicitudes por ahora.") }
                items(ders, key = { it.id }) { d ->
                    Tarjeta(titulo = "${d.codigo} · ${CANALES[d.canal] ?: d.canal}") {
                        TextoSecundario(horaCorta(d.fecha) + if (d.conContacto) " · dejó datos de contacto (cifrados, se ven en el panel web)" else " · sin datos de contacto")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ESTADOS_DER.forEach { (k, n) ->
                                FilterChip(
                                    selected = d.estado == k,
                                    onClick = { scope.launch { runCatching { repo.gestionarDerivacion(d.id, k) }; onCambio() } },
                                    label = { Text(n, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer)
                                )
                            }
                        }
                        Button(
                            onClick = { scope.launch { try { onAbrir(repo.iniciarAtencion(d.id)) } catch (e: Exception) { mensaje = "No se pudo abrir el chat." } } },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("Abrir chat de atención") }
                    }
                }
            }
            RolEquipo.ADMIN -> item {
                Tarjeta(titulo = "Administración") {
                    TextoSecundario("Las estadísticas, la gestión del equipo y la seguridad están por ahora en el panel web. Llegarán a la app en una próxima actualización.")
                }
            }
        }
        mensaje?.let { m -> item { TextoSecundario(m) } }
    }
}

@Composable
private fun Resumen(cifras: List<Pair<String, Int>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cifras.forEach { (n, v) ->
            Tarjeta(modifier = Modifier.weight(1f)) {
                Text("$v", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(n, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Titulo(t: String) = Text(t, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 6.dp))

@Composable
private fun ListaChats(chats: List<ConversacionEquipo>, rol: RolEquipo, onAbrir: (String) -> Unit) {
    var filtro by remember { mutableStateOf("abierta") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("abierta" to "Abiertos", "todos" to "Todos").forEach { (k, n) ->
                FilterChip(selected = filtro == k, onClick = { filtro = k }, label = { Text(n) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer))
            }
        }
        val lista = if (filtro == "todos") chats else chats.filter { it.estado == "abierta" }
        if (lista.isEmpty()) TextoSecundario(if (rol == RolEquipo.TUTOR) "Toma una consulta en Solicitudes para empezar a conversar." else "No hay chats.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lista, key = { it.id }) { c ->
                Tarjeta(modifier = Modifier.clickable { onAbrir(c.id) }, titulo = TEMAS[c.tema] ?: c.tema) {
                    TextoSecundario("${etiquetaEstudiante(c.id)} · ${c.estado.replaceFirstChar { it.uppercase() }} · ${horaCorta(c.actualizadaEn)}")
                }
            }
        }
    }
}

@Composable
private fun ChatEquipo(repo: EquipoRepositorio, rol: RolEquipo, id: String, conv: ConversacionEquipo?, onVolver: () -> Unit) {
    val scope = rememberCoroutineScope()
    var mensajes by remember { mutableStateOf<List<MensajeEquipo>>(emptyList()) }
    var bitacora by remember { mutableStateOf<List<Supervision>>(emptyList()) }
    var texto by remember { mutableStateOf("") }
    var nota by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val lista = rememberLazyListState()
    val estado = conv?.estado ?: "abierta"
    val propioRol = when (rol) { RolEquipo.PROFESIONAL -> "profesional"; RolEquipo.DOCENTE -> "docente"; RolEquipo.TUTOR -> "tutor"; RolEquipo.ADMIN -> "admin" }
    val puedeEscribir = estado == "abierta" && when (rol) {
        RolEquipo.TUTOR -> conv?.tutorId == repo.miId
        RolEquipo.DOCENTE -> true
        RolEquipo.PROFESIONAL -> conv?.profesionalId == repo.miId
        RolEquipo.ADMIN -> false
    }

    suspend fun cargar() {
        runCatching { mensajes = repo.mensajes(id) }
        if (rol == RolEquipo.DOCENTE) runCatching { bitacora = repo.bitacora(id) }
    }
    LaunchedEffect(id) { while (true) { cargar(); delay(5_000) } }
    LaunchedEffect(mensajes.size) { if (mensajes.isNotEmpty()) lista.animateScrollToItem(mensajes.size - 1) }

    fun accion(bloque: suspend () -> Unit) = scope.launch {
        try { bloque(); cargar(); aviso = null } catch (e: Exception) { aviso = ExplicacionError.explicar(e.message, (e as? com.inacap.health.data.remote.ErrorNube)?.codigo) }
    }

    Scaffold(topBar = { BarraSuperior("${TEMAS[conv?.tema] ?: "Chat"} · ${etiquetaEstudiante(id)}", onVolver) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (rol) {
                    RolEquipo.TUTOR -> if (puedeEscribir) {
                        AssistChip(onClick = { accion { repo.cambiarEstado(id, "derivada") } }, label = { Text("Derivar a profesional") })
                        AssistChip(onClick = { accion { repo.cambiarEstado(id, "cerrada") } }, label = { Text("Cerrar") })
                    } else if (conv?.tutorId == null && estado == "abierta") {
                        AssistChip(onClick = { accion { repo.tomar(id) } }, label = { Text("Tomar consulta") })
                    }
                    RolEquipo.DOCENTE -> {
                        AssistChip(onClick = { accion { repo.supervisar(id, "revisada", null) } }, label = { Text("Revisada") })
                        AssistChip(onClick = { nota = "" }, label = { Text("Observación") })
                        if (estado == "abierta") AssistChip(onClick = { accion { repo.supervisar(id, "derivada", "Derivada desde la app") } }, label = { Text("Derivar") })
                        else AssistChip(onClick = { accion { repo.supervisar(id, "reabierta", null) } }, label = { Text("Reabrir") })
                    }
                    RolEquipo.PROFESIONAL -> if (estado == "abierta") AssistChip(onClick = { accion { repo.cambiarEstado(id, "cerrada") } }, label = { Text("Cerrar atención") })
                    RolEquipo.ADMIN -> {}
                }
            }
            Text(
                if (rol == RolEquipo.PROFESIONAL) "Atención confidencial: solo tú y la persona ven esta conversación."
                else "Solo orientación educativa: sin diagnósticos, dietas ni tratamientos. Ante señales de TCA o riesgo, deriva.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = lista, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Spacer(Modifier.height(6.dp)) }
                items(mensajes, key = { it.id }) { m -> BurbujaMensaje(m.rol, m.texto, m.creadoEn, propio = m.rol == propioRol) }
                if (rol == RolEquipo.DOCENTE && bitacora.isNotEmpty()) {
                    item { Text("Bitácora de supervisión", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp)) }
                    items(bitacora) { b -> TextoSecundario("${horaCorta(b.fecha)} · ${b.accion}${b.nota?.let { ": $it" } ?: ""}") }
                }
            }
            aviso?.let { TextoSecundario(it, Modifier.padding(horizontal = 16.dp)) }
            if (puedeEscribir) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(texto, { texto = it.take(1000) }, placeholder = { Text("Escribe tu respuesta") }, modifier = Modifier.weight(1f), maxLines = 4)
                    Button(
                        onClick = { val t = texto; texto = ""; accion { repo.enviar(id, t) } }, enabled = texto.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Enviar") }
                }
            } else {
                Text(
                    when (estado) { "cerrada" -> "Conversación cerrada."; "derivada" -> "Conversación derivada a apoyo profesional."; else -> "Toma la consulta para responder." },
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
        }
    }
    nota?.let { n ->
        AlertDialog(
            onDismissRequest = { nota = null },
            title = { Text("Observación para la bitácora") },
            text = { OutlinedTextField(n, { nota = it.take(1000) }, minLines = 3) },
            confirmButton = { TextButton(onClick = { val t = nota; nota = null; accion { repo.supervisar(id, "observacion", t) } }, enabled = n.isNotBlank()) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { nota = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun CuentaEquipo(repo: EquipoRepositorio, rol: RolEquipo, onSalir: () -> Unit) {
    val scope = rememberCoroutineScope()
    var confirmar by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Tarjeta(titulo = "Tu sesión") {
            FilaValor("Nombre", repo.nombre)
            FilaValor("Correo", repo.email)
            FilaValor("Rol", rol.nombre)
            FilaValor("La sesión se cierra en", "${repo.minutosRestantes()} min (o tras 30 min sin uso)")
        }
        Tarjeta(titulo = "Tus compromisos") {
            TextoSecundario("• Lo que ves es confidencial: no lo compartas ni hagas capturas.")
            TextoSecundario("• No pidas datos que identifiquen a la persona si no son necesarios.")
            TextoSecundario(if (rol == RolEquipo.PROFESIONAL) "• Si detectas riesgo vital, sigue el protocolo de urgencia de tu institución." else "• Ante señales de TCA o de riesgo, deriva a un profesional.")
        }
        val contexto = androidx.compose.ui.platform.LocalContext.current
        val oscuro = com.inacap.health.ui.theme.temaOscuroActivo()
        Tarjeta {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Modo noche", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = oscuro, onCheckedChange = { com.inacap.health.ui.theme.Apariencia.alternar(contexto, oscuro) })
            }
        }
        OutlinedButton(onClick = { confirmar = true }, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión", color = MaterialTheme.colorScheme.onSurface) }
    }
    if (confirmar) AlertDialog(
        onDismissRequest = { confirmar = false },
        title = { Text("Cerrar sesión") },
        text = { Text("Saldrás del modo equipo en este teléfono.") },
        confirmButton = { TextButton(onClick = { confirmar = false; scope.launch { repo.cerrarSesion(); onSalir() } }) { Text("Cerrar sesión", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
    )
}

