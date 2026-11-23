package com.inacap.health.ui.screens.bienestar

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inacap.health.HealthApp
import com.inacap.health.data.Consentimiento
import com.inacap.health.data.TipoConsentimiento
import com.inacap.health.legal.TextosLegales
import com.inacap.health.orientacion.Conversacion
import com.inacap.health.orientacion.Mensaje
import com.inacap.health.orientacion.OrientacionRepositorio
import com.inacap.health.orientacion.SenalesDeAlerta
import com.inacap.health.orientacion.Temas
import com.inacap.health.ui.components.BarraSuperior
import com.inacap.health.ui.components.Tarjeta
import com.inacap.health.ui.components.TextoSecundario
import com.inacap.health.ui.theme.BorderGray
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OrientacionViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HealthApp
    val repo = OrientacionRepositorio(application)

    private val _conversaciones = MutableStateFlow<List<Conversacion>>(emptyList())
    val conversaciones = _conversaciones.asStateFlow()
    private val _mensajes = MutableStateFlow<List<Mensaje>>(emptyList())
    val mensajes = _mensajes.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _errorDetalle = MutableStateFlow<String?>(null)
    val errorDetalle = _errorDetalle.asStateFlow()
    private val _cargando = MutableStateFlow(false)
    val cargando = _cargando.asStateFlow()
    private val _scoffConAlerta = MutableStateFlow(false)
    val scoffConAlerta = _scoffConAlerta.asStateFlow()

    init {
        viewModelScope.launch {
            _scoffConAlerta.value = app.database.resultadoScoffDao().getAllUnaVez().lastOrNull()?.requiereDerivacion == true
        }
    }

    private suspend fun intentar(bloque: suspend () -> Unit) {
        try { bloque(); _error.value = null; _errorDetalle.value = null }
        catch (e: Exception) {
            val codigo = (e as? com.inacap.health.data.remote.ErrorNube)?.codigo
            _error.value = when {
                codigo == -1 -> (com.inacap.health.data.remote.ExplicacionError.fallaDeRed(e.message) ?: "No hubo respuesta del servidor.") + " Intenta de nuevo."
                codigo == 429 -> "Enviaste muchos mensajes seguidos. Espera un minuto e intenta de nuevo."
                codigo != null -> "El servicio de orientación rechazó la acción (error $codigo). Intenta de nuevo."
                else -> "No se pudo completar (${e.javaClass.simpleName}). Intenta de nuevo."
            }
            _errorDetalle.value = e.message ?: e.javaClass.name
        }
    }

    fun aceptar() {
        repo.aceptado = true
        viewModelScope.launch {
            app.database.consentimientoDao().insert(
                Consentimiento(tipo = TipoConsentimiento.ORIENTACION, version = TextosLegales.VERSION, aceptado = true)
                    .also { app.sync.encolarConsentimiento(it) }
            )
        }
        recargar()
    }

    fun recargar() = viewModelScope.launch { intentar { _conversaciones.value = repo.conversaciones() } }

    fun cargarMensajes(id: String) = viewModelScope.launch { intentar { _mensajes.value = repo.mensajes(id) } }

    fun abrir(tema: String, texto: String, onAbierta: (String) -> Unit) = viewModelScope.launch {
        _cargando.value = true
        intentar { val id = repo.abrir(tema, texto); recargar(); onAbierta(id) }
        _cargando.value = false
    }

    fun enviar(id: String, texto: String) = viewModelScope.launch {
        intentar { repo.enviar(id, texto); _mensajes.value = repo.mensajes(id) }
    }

    fun cerrar(id: String) = viewModelScope.launch { intentar { repo.cerrar(id); recargar() } }

    fun borrarTodo(onListo: () -> Unit) = viewModelScope.launch {
        intentar {
            repo.borrarTodo()
            app.database.consentimientoDao().insert(
                Consentimiento(tipo = TipoConsentimiento.ORIENTACION, version = TextosLegales.VERSION, aceptado = false)
                    .also { app.sync.encolarConsentimiento(it) }
            )
            _conversaciones.value = emptyList()
            onListo()
        }
    }
}

private fun etiquetaRol(rol: String) = when (rol) {
    "tutor" -> "Tutor par"
    "docente" -> "Docente supervisor"
    "profesional" -> "Profesional de Bienestar"
    "sistema" -> "Aviso"
    else -> "Tú"
}

/** HU-13 · Orientación entre pares supervisada. */
@Composable
fun OrientacionScreen(
    onNavigateToDerivacion: () -> Unit,
    onBack: () -> Unit,
    soloAtencion: Boolean = false,
    vm: OrientacionViewModel = viewModel()
) {
    // Los chats de atención los abre un profesional por una derivación pedida: no requieren
    // el consentimiento del chat entre pares.
    var aceptado by remember { mutableStateOf(vm.repo.aceptado || soloAtencion) }
    var abierta by remember { mutableStateOf<String?>(null) }
    var nueva by remember { mutableStateOf(false) }
    val error by vm.error.collectAsState()
    val errorDetalle by vm.errorDetalle.collectAsState()

    Scaffold(topBar = {
        BarraSuperior(if (soloAtencion) "Mensajes de Bienestar" else "Orientación entre pares", onBack = {
            when { abierta != null -> { abierta = null; vm.recargar() }; nueva -> nueva = false; else -> onBack() }
        })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when {
                !vm.repo.disponible -> Column(Modifier.padding(24.dp)) {
                    Tarjeta { TextoSecundario("La orientación entre pares no está disponible en esta versión.") }
                }
                !aceptado -> Consentimiento { vm.aceptar(); aceptado = true }
                abierta != null -> Conversacion(vm, abierta!!, onNavigateToDerivacion)
                nueva -> NuevaConsulta(vm, onNavigateToDerivacion) { id -> nueva = false; abierta = id }
                else -> Lista(vm, soloAtencion, onAbrir = { abierta = it }, onNueva = { nueva = true }, onBorrado = { aceptado = false })
            }
            error?.let {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface).padding(12.dp)
                ) { com.inacap.health.ui.components.DetalleError(resumen = it, error = errorDetalle) }
            }
        }
    }
}

@Composable
private fun Consentimiento(onAceptar: () -> Unit) {
    var check by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Tarjeta(titulo = "Antes de empezar") {
            TextoSecundario("• Te responde un tutor par: un estudiante de Nutrición y Dietética supervisado por un docente, que puede leer las conversaciones.")
            TextoSecundario("• Es orientación educativa sobre hábitos. No entrega diagnósticos, dietas ni tratamientos, y no reemplaza a un profesional.")
            TextoSecundario("• Los temas de trastornos de la conducta alimentaria se derivan a un profesional.")
            TextoSecundario("• Tus mensajes se guardan de forma segura con un código anónimo. No escribas tu nombre, RUT ni datos que te identifiquen.")
            TextoSecundario("• Puedes borrar todas tus conversaciones cuando quieras.")
        }
        Row(Modifier.fillMaxWidth().clickable { check = !check }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(check, { check = it }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
            Text("Entiendo y acepto usar la orientación entre pares", fontSize = 14.sp)
        }
        Button(onClick = onAceptar, enabled = check, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Continuar", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Lista(vm: OrientacionViewModel, soloAtencion: Boolean, onAbrir: (String) -> Unit, onNueva: () -> Unit, onBorrado: () -> Unit) {
    val todas by vm.conversaciones.collectAsState()
    val convs = if (soloAtencion) todas.filter { it.tipo == "atencion" } else todas
    var confirmar by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.recargar() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!soloAtencion) Button(onClick = onNueva, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Nueva consulta", fontWeight = FontWeight.Bold)
        }
        if (convs.isEmpty()) TextoSecundario(if (soloAtencion) "Aún no hay mensajes. Cuando un profesional de Bienestar responda tu solicitud, lo verás aquí." else "Aún no tienes conversaciones.")
        convs.forEach { c ->
            Tarjeta(Modifier.clickable { onAbrir(c.id) }, Temas.nombre(c.tema)) {
                TextoSecundario(when (c.estado) { "abierta" -> "Abierta"; "derivada" -> "Derivada a un profesional"; else -> "Cerrada" })
            }
        }
        if (convs.isNotEmpty() && !soloAtencion) TextButton(onClick = { confirmar = true }) { Text("Borrar todas mis conversaciones", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (confirmar) AlertDialog(
        onDismissRequest = { confirmar = false },
        title = { Text("Borrar conversaciones", fontWeight = FontWeight.Bold) },
        text = { Text("Se borrarán todas tus conversaciones de forma permanente. No se puede deshacer.") },
        confirmButton = { TextButton(onClick = { confirmar = false; vm.borrarTodo(onBorrado) }) { Text("Borrar", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) } },
        dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
    )
}

@Composable
private fun NuevaConsulta(vm: OrientacionViewModel, onDerivacion: () -> Unit, onAbierta: (String) -> Unit) {
    val alertaScoff by vm.scoffConAlerta.collectAsState()
    val cargando by vm.cargando.collectAsState()
    var tema by remember { mutableStateOf("alimentacion") }
    var texto by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (alertaScoff) {
            // Criterio de aceptación HU-13: los casos con señales de TCA no se atienden entre pares.
            Tarjeta(titulo = "Te recomendamos apoyo profesional") {
                TextoSecundario("Por tu último resultado del cuestionario de bienestar alimentario, lo mejor es conversar con un profesional. Es confidencial y tú decides.")
                Button(onClick = onDerivacion, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Ir a Apoyo profesional") }
            }
            return@Column
        }
        Text("¿Sobre qué quieres conversar?", fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Temas.TODOS.take(3).forEach { (k, n) -> ChipTema(n, tema == k) { tema = k } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Temas.TODOS.drop(3).forEach { (k, n) -> ChipTema(n, tema == k) { tema = k } }
        }
        OutlinedTextField(texto, { texto = it.take(1000) }, label = { Text("Tu pregunta") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        if (SenalesDeAlerta.hay(texto)) AvisoAyuda(onDerivacion)
        Button(
            onClick = { vm.abrir(tema, texto, onAbierta) }, enabled = texto.isNotBlank() && !cargando,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
        ) { Text(if (cargando) "Enviando…" else "Enviar", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ChipTema(texto: String, sel: Boolean, onClick: () -> Unit) = FilterChip(
    selected = sel, onClick = onClick, label = { Text(texto, fontSize = 13.sp) },
    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), selectedLabelColor = MaterialTheme.colorScheme.primary)
)

@Composable
private fun AvisoAyuda(onDerivacion: () -> Unit) {
    Tarjeta(titulo = "No estás solo/a") {
        TextoSecundario("Si lo que sientes es urgente, llama a Salud Responde 600 360 7777 o a la línea *4141 (prevención del suicidio, gratuita y 24 horas). También puedes pedir apoyo profesional confidencial.")
        OutlinedButton(onClick = onDerivacion) { Text("Apoyo profesional", color = MaterialTheme.colorScheme.onSurface) }
    }
}

@Composable
private fun Conversacion(vm: OrientacionViewModel, id: String, onDerivacion: () -> Unit) {
    val mensajes by vm.mensajes.collectAsState()
    val convs by vm.conversaciones.collectAsState()
    val estado = convs.firstOrNull { it.id == id }?.estado ?: "abierta"
    var texto by remember { mutableStateOf("") }
    val lista = rememberLazyListState()
    LaunchedEffect(id) { while (true) { vm.cargarMensajes(id); vm.recargar(); delay(5_000) } }
    LaunchedEffect(mensajes.size) { if (mensajes.isNotEmpty()) lista.animateScrollToItem(mensajes.size - 1) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), state = lista, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { TextoSecundario("Orientación educativa. No compartas datos que te identifiquen.", Modifier.padding(vertical = 8.dp)) }
            items(mensajes, key = { it.id }) { m ->
                val mio = m.rol == "estudiante"
                Box(Modifier.fillMaxWidth(), contentAlignment = if (mio) Alignment.CenterEnd else Alignment.CenterStart) {
                    Column(
                        Modifier.widthIn(max = 300.dp)
                            .background(if (mio) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(etiquetaRol(m.rol), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(m.texto, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            if (mensajes.none { it.rol != "estudiante" } && estado == "abierta") {
                item { TextoSecundario("Un tutor par te responderá pronto. Puedes cerrar la app: verás la respuesta cuando vuelvas.") }
            }
        }
        if (SenalesDeAlerta.hay(texto) || mensajes.any { it.rol == "estudiante" && SenalesDeAlerta.hay(it.texto) }) {
            Box(Modifier.padding(horizontal = 16.dp)) { AvisoAyuda(onDerivacion) }
        }
        if (estado == "abierta") {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(texto, { texto = it.take(1000) }, placeholder = { Text("Escribe un mensaje") }, modifier = Modifier.weight(1f), maxLines = 4)
                Button(onClick = { vm.enviar(id, texto); texto = "" }, enabled = texto.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Enviar") }
            }
            TextButton(onClick = { vm.cerrar(id) }, modifier = Modifier.padding(start = 8.dp)) { Text("Cerrar conversación", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            Text(
                if (estado == "derivada") "Esta conversación se derivó a apoyo profesional." else "Conversación cerrada.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().background(BorderGray.copy(alpha = 0.3f)).padding(16.dp)
            )
        }
    }
}
