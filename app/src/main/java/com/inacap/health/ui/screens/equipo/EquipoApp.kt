package com.inacap.health.ui.screens.equipo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
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
import com.inacap.health.equipo.MonitoreoReglas
import com.inacap.health.data.remote.ErrorNube
import androidx.compose.foundation.layout.PaddingValues
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
    "habitos" to "Hábitos y descanso", "otro" to "Otro", "apoyo_profesional" to "Atención de Bienestar"
)
private val CANALES = mapOf("bienestar_presencial" to "Presencial", "bienestar_correo" to "Por correo", "cesfam" to "CESFAM")
private val ESTADOS_DER = listOf("pendiente" to "Pendiente", "en_contacto" to "En contacto", "atendida" to "Atendida")

private fun etiquetaEstudiante(id: String) = "Estudiante #" + id.take(6)

/** Pestañas del modo equipo. Cada rol ve solo las suyas; los datos igual los filtra el servidor. */
enum class PestanaEquipo(val etiqueta: String) {
    SOLICITUDES("Solicitudes"), CHATS("Chats"), MONITOREO("Monitoreo"), BITACORA("Bitácora"),
    RESUMEN("Resumen"), EQUIPO("Equipo"), SEGURIDAD("Seguridad"), CUENTA("Cuenta");

    companion object {
        fun de(rol: RolEquipo): List<PestanaEquipo> = when (rol) {
            RolEquipo.TUTOR -> listOf(SOLICITUDES, CHATS, MONITOREO, CUENTA)
            RolEquipo.PROFESIONAL -> listOf(SOLICITUDES, CHATS, CUENTA)
            RolEquipo.DOCENTE -> listOf(SOLICITUDES, CHATS, BITACORA, CUENTA)
            RolEquipo.ADMIN -> listOf(RESUMEN, EQUIPO, SEGURIDAD, CUENTA)
        }
    }
}

/** App del equipo: pestañas según el rol (ver [PestanaEquipo.de]). */
@Composable
fun EquipoApp(repo: EquipoRepositorio, onSalir: () -> Unit) {
    val rol = repo.rol ?: return
    var pestana by remember { mutableIntStateOf(0) }
    var abierta by remember { mutableStateOf<String?>(null) }
    var convs by remember { mutableStateOf<List<ConversacionEquipo>>(emptyList()) }
    var ders by remember { mutableStateOf<List<DerivacionEquipo>>(emptyList()) }
    var error by remember { mutableStateOf<Exception?>(null) }
    val scope = rememberCoroutineScope()

    val pestanas = PestanaEquipo.de(rol)

    suspend fun recargar() {
        if (rol == RolEquipo.ADMIN) return   // el administrador no ve conversaciones: cada pestaña carga lo suyo
        try {
            convs = repo.conversaciones()
            if (rol == RolEquipo.PROFESIONAL) ders = repo.derivaciones()
            error = null
        } catch (e: Exception) {
            error = e
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
                pestanas.forEachIndexed { i, p ->
                    val icono = when (p) {
                        PestanaEquipo.SOLICITUDES -> Icons.Default.Notifications
                        PestanaEquipo.CHATS -> Icons.Default.Email
                        PestanaEquipo.MONITOREO, PestanaEquipo.RESUMEN -> Icons.Default.Info
                        PestanaEquipo.BITACORA, PestanaEquipo.EQUIPO -> Icons.AutoMirrored.Filled.List
                        PestanaEquipo.SEGURIDAD -> Icons.Default.Lock
                        PestanaEquipo.CUENTA -> Icons.Default.Person
                    }
                    NavigationBarItem(pestana == i, { pestana = i }, icon = {
                        if (p == PestanaEquipo.SOLICITUDES) BadgedBox(badge = { if (pendientes > 0) Badge { Text("$pendientes") } }) { Icon(icono, null) }
                        else Icon(icono, null)
                    }, label = { Text(p.etiqueta, fontSize = 11.sp, maxLines = 1) }, colors = colores)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            error?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { ErrorSimple(it) } }
            when (pestanas.getOrElse(pestana) { PestanaEquipo.CUENTA }) {
                PestanaEquipo.SOLICITUDES -> Solicitudes(repo, rol, convs, ders, onAbrir = { abierta = it }, onCambio = { scope.launch { recargar() } })
                PestanaEquipo.CHATS -> ListaChats(misChats, rol, onAbrir = { abierta = it })
                PestanaEquipo.MONITOREO -> MonitoreoTutor(repo, convs)
                PestanaEquipo.BITACORA -> BitacoraGeneral(repo, onAbrir = { abierta = it })
                PestanaEquipo.RESUMEN -> ResumenAdminVista(repo)
                PestanaEquipo.EQUIPO -> EquipoVista(repo)
                PestanaEquipo.SEGURIDAD -> SeguridadVista(repo)
                PestanaEquipo.CUENTA -> CuentaEquipo(repo, rol, onSalir)
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
                        TextoSecundario(horaCorta(d.fecha) + if (d.conContacto) " · dejó datos de contacto (cifrados: se descifran en el panel web con la clave de Bienestar)" else " · sin datos de contacto")
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
            RolEquipo.ADMIN -> {}
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
    var aviso by remember { mutableStateOf<Exception?>(null) }
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
        try { bloque(); cargar(); aviso = null } catch (e: Exception) { aviso = e }
    }

    Scaffold(topBar = { BarraSuperior("${TEMAS[conv?.tema] ?: "Chat"} · ${etiquetaEstudiante(id)}", onVolver) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (rol) {
                    RolEquipo.TUTOR -> if (puedeEscribir) {
                        AssistChip(onClick = { accion { repo.cambiarEstado(id, "derivada") } }, label = { Text("Derivar a Bienestar") })
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
            aviso?.let { Box(Modifier.padding(horizontal = 16.dp)) { ErrorSimple(it) } }
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
                    when (estado) { "cerrada" -> "Conversación cerrada."; "derivada" -> "Conversación derivada a Bienestar."; else -> "Toma la consulta para responder." },
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


/** Error en lenguaje simple; el técnico solo dentro de «Ver detalle del error». */
@Composable
private fun ErrorSimple(e: Exception) =
    com.inacap.health.ui.components.DetalleError(ExplicacionError.simple(e.message, (e as? ErrorNube)?.codigo), e.message ?: e.javaClass.simpleName)

// ---------- Vistas por rol ----------

/** Tutor: cifras sin nombres de las consultas que puede ver (lo filtra la RLS). */
@Composable
private fun MonitoreoTutor(repo: EquipoRepositorio, convs: List<ConversacionEquipo>) {
    var autores by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(convs) { runCatching { autores = repo.ultimosAutores() } }
    val m = MonitoreoReglas.calcular(convs, autores)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Resumen(listOf("Abiertas" to m.abiertas, "Sin tutor" to m.sinTutor, "Esperan respuesta" to m.esperanRespuesta)) }
        item { Titulo("Consultas abiertas por tema") }
        if (m.porTema.isEmpty()) item { TextoSecundario("No hay consultas abiertas.") }
        items(m.porTema.toList(), key = { it.first }) { (tema, n) -> Tarjeta { FilaValor(TEMAS[tema] ?: tema, "$n") } }
        item {
            TextoSecundario("Cifras sin nombres. «Esperan respuesta»: lo último lo escribió el estudiante. Revisa que ninguna quede mucho rato sin respuesta y deriva ante señales de riesgo.")
        }
    }
}

/** Docente: bitácora de supervisión de todas las conversaciones de orientación. */
@Composable
private fun BitacoraGeneral(repo: EquipoRepositorio, onAbrir: (String) -> Unit) = Cargando({ repo.bitacoraGeneral() }) { lista ->
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Titulo("Bitácora de supervisión (últimas 100)") }
        if (lista.isEmpty()) item { TextoSecundario("Aún no hay registros.") }
        items(lista) { b ->
            val conv = b.conversacionId
            Tarjeta(modifier = if (conv != null) Modifier.clickable { onAbrir(conv) } else Modifier, titulo = b.accion.replaceFirstChar { it.uppercase() }) {
                TextoSecundario(horaCorta(b.fecha) + (conv?.let { " · " + etiquetaEstudiante(it) } ?: " · conversación eliminada"))
                b.nota?.let { TextoSecundario(it) }
            }
        }
    }
}

/** Carga un dato una vez al abrir la pestaña y muestra "Cargando…" o el error en lenguaje simple. */
@Composable
private fun <T : Any> Cargando(cargar: suspend () -> T, contenido: @Composable (T) -> Unit) {
    var dato by remember { mutableStateOf<T?>(null) }
    var error by remember { mutableStateOf<Exception?>(null) }
    LaunchedEffect(Unit) {
        try { dato = cargar() } catch (e: Exception) { error = e }
    }
    val d = dato
    val e = error
    when {
        e != null -> Box(Modifier.padding(16.dp)) { ErrorSimple(e) }
        d == null -> TextoSecundario("Cargando…", Modifier.padding(16.dp))
        else -> contenido(d)
    }
}

/** Administrador: estadísticas agregadas (sin datos de personas). */
@Composable
private fun ResumenAdminVista(repo: EquipoRepositorio) = Cargando({ repo.resumenAdmin(30) }) { r ->
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Titulo("Últimos ${r.dias} días")
        Resumen(listOf("Personas" to r.usuarios, "Nuevas" to r.nuevos, "Activas" to r.activos))
        Tarjeta(titulo = "Orientación y apoyo") {
            FilaValor("Consultas nuevas", "${r.consultas}")
            FilaValor("Abiertas ahora", "${r.abiertas}")
            FilaValor("Sin tutor", "${r.sinTutor}")
            FilaValor("Solicitudes a Bienestar", "${r.derivaciones}")
        }
        Tarjeta(titulo = "Salud") {
            FilaValor("Tests SCOFF", "${r.tests}")
            FilaValor("Sugieren apoyo", "${r.sugierenApoyo}")
            FilaValor("Pulso promedio", r.pulsoPromedio?.let { "$it lpm" } ?: "—")
            FilaValor("Pasos por día (promedio)", "${r.pasosPromedio}")
        }
        TextoSecundario("Solo cifras agregadas de quienes comparten datos anonimizados. Los gráficos y el detalle están en el panel web.")
    }
}

/** Administrador: quién está en el equipo y con qué rol. */
@Composable
private fun EquipoVista(repo: EquipoRepositorio) = Cargando({ repo.equipo() }) { lista ->
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Titulo("Activos por rol") }
        items(RolEquipo.entries.toList(), key = { "r" + it.clave }) { r -> Tarjeta { FilaValor(r.nombre, "${lista.count { it.activo && it.rol == r.clave }}") } }
        item { Titulo("Personas") }
        items(lista, key = { it.userId }) { m ->
            Tarjeta(titulo = m.nombre.ifBlank { m.email }) {
                TextoSecundario("${RolEquipo.de(m.rol)?.nombre ?: m.rol} · ${if (m.activo) "Activo" else "Desactivado"}")
                TextoSecundario(m.email)
                m.ultimoAcceso?.let { TextoSecundario("Último acceso: " + horaCorta(it)) }
            }
        }
        item { TextoSecundario("Para asignar o quitar roles usa el panel web (pestaña Equipo).") }
    }
}

/** Administrador: resumen de seguridad (rechazos, IPs bloqueadas y auditoría). */
@Composable
private fun SeguridadVista(repo: EquipoRepositorio) = Cargando({ repo.seguridad() }) { s ->
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Resumen(listOf("Rechazos 24 h" to s.rechazos24h, "IPs bloqueadas" to s.ips.size)) }
        if (s.ips.isNotEmpty()) item { Titulo("IPs bloqueadas") }
        items(s.ips, key = { "ip" + it.ip }) { ip ->
            Tarjeta(titulo = ip.ip) { TextoSecundario("Hasta ${horaCorta(ip.hasta)}" + (ip.motivo?.let { " · $it" } ?: "")) }
        }
        item { Titulo("Auditoría reciente") }
        if (s.auditoria.isEmpty()) item { TextoSecundario("Sin eventos.") }
        items(s.auditoria) { a -> TextoSecundario("${horaCorta(a.fecha)} · ${a.accion} · ${a.actor}") }
        item { TextoSecundario("Para bloquear o desbloquear IPs usa el panel web (pestaña Seguridad).") }
    }
}
