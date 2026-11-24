package com.inacap.health.ui.screens.pestanas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inacap.health.HealthApp
import com.inacap.health.legal.TextosLegales
import com.inacap.health.orientacion.Conversacion
import com.inacap.health.orientacion.Mensaje
import com.inacap.health.orientacion.OrientacionRepositorio
import com.inacap.health.salud.RelojResumen
import com.inacap.health.ui.components.Iconos
import com.inacap.health.ui.nav.Routes
import com.inacap.health.ui.screens.inicio.BotonPrincipal
import com.inacap.health.ui.screens.perfil.PerfilViewModel
import com.inacap.health.ui.screens.salud.HistorialViewModel
import com.inacap.health.ui.screens.salud.SaludLogic
import com.inacap.health.ui.screens.salud.SaludViewModel
import com.inacap.health.ui.theme.Apariencia
import com.inacap.health.ui.theme.Diseno
import com.inacap.health.ui.theme.temaOscuroActivo
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

private val CL = Locale.forLanguageTag("es-CL")
private val VERDE = Color(0xFF2E7D32)
private val VERDE_NOCHE = Color(0xFF81C784)

// ---------------------------------------------------------------------
// Piezas comunes (mismo lenguaje que Inicio, tarea 18e)
// ---------------------------------------------------------------------

@Composable
private fun bandaRoja() = if (temaOscuroActivo()) Diseno.BandaRojaNoche else Diseno.BandaRoja

@Composable
private fun verde() = if (temaOscuroActivo()) VERDE_NOCHE else VERDE

/** Banda roja INACAP arriba de cada pestaña. */
@Composable
private fun BandaPestana(titulo: String, subtitulo: String, extra: @Composable ColumnScope.() -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().background(bandaRoja(), RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(titulo, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitulo, color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.bodyMedium)
        extra()
    }
}

/** Tarjeta blanca con relieve suave. */
@Composable
private fun TarjetaBlanca(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, contenido: @Composable ColumnScope.() -> Unit) {
    val forma = RoundedCornerShape(Diseno.ESQUINA_TARJETA)
    Column(
        modifier.shadow(3.dp, forma).clip(forma).background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(14.dp),
        content = contenido
    )
}

@Composable
private fun IconoCirculo(icono: ImageVector, tinte: Color = MaterialTheme.colorScheme.primary, tamano: Dp = 40.dp) {
    Box(Modifier.size(tamano).background(tinte.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icono, contentDescription = null, tint = tinte, modifier = Modifier.size(tamano * 0.55f))
    }
}

@Composable
private fun Titulo(texto: String, modifier: Modifier = Modifier) =
    Text(texto, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = modifier)

@Composable
private fun Sub(texto: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(texto, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun Flecha() = Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

// ---------------------------------------------------------------------
// Salud
// ---------------------------------------------------------------------

/** Tarea 20 · Salud: resumen de hoy en la banda, pulso con cámara como tarjeta estrella. */
@Composable
fun SaludPestana(salud: SaludViewModel, onNavigate: (String) -> Unit) {
    val historial: HistorialViewModel = viewModel()
    val agua by salud.aguaHoyMl.collectAsState()
    val meta by salud.metaAguaMl.collectAsState()
    val pasos by salud.pasosHoy.collectAsState()
    val pulso by salud.ultimoPulso.collectAsState()
    val reloj by salud.reloj.collectAsState()
    val registros by historial.todosRegistros.collectAsState()
    val hoy = remember { LocalDate.now() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BandaPestana("Salud", "Tus mediciones de hoy") {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniResumen("Agua", "${SaludLogic.vasos(agua)}/${SaludLogic.metaVasos(meta)}")
                MiniResumen("Pasos", String.format(CL, "%,d", pasos))
                MiniResumen("Pulso", (reloj?.bpm ?: pulso?.frecuenciaCardiaca)?.toString() ?: "—")
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Tarjeta estrella: lo que más gusta de la app.
            TarjetaBlanca(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Lo más usado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("Medir pulso con la cámara", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Sub("Pon el dedo sobre la cámara y la linterna")
                    }
                    IconoCirculo(Icons.Default.Favorite, tamano = 56.dp)
                }
                Spacer(Modifier.height(12.dp))
                BotonPrincipal("Medir ahora") { onNavigate(Routes.PULSO) }
            }
            // Historial con adelanto de la semana actual del mes.
            TarjetaBlanca(Modifier.fillMaxWidth(), onClick = { onNavigate(Routes.HISTORIAL) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Titulo("Historial", Modifier.weight(1f))
                    val semana = (hoy.dayOfMonth - 1) / 7 + 1
                    Sub("${hoy.month.getDisplayName(java.time.format.TextStyle.FULL, CL).replaceFirstChar { it.titlecase(CL) }} · semana $semana ›")
                }
                Spacer(Modifier.height(10.dp))
                BarrasSemana(registros, meta, hoy)
                Sub("Vasos de agua por día · gris = bajo la meta")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TarjetaBlanca(Modifier.weight(1f), onClick = { onNavigate(Routes.IMC_AGUA) }) {
                    IconoCirculo(Iconos.Gota, MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(8.dp))
                    Titulo("Calculadoras"); Sub("IMC e hidratación")
                }
                TarjetaBlanca(Modifier.weight(1f), onClick = { onNavigate(Routes.HEALTH_CONNECT) }) {
                    IconoCirculo(Iconos.Reloj, MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(8.dp))
                    Titulo("Mi reloj")
                    val r = reloj
                    Sub(if (r != null && r.actualizadoEn > 0) "Conectado · ${RelojResumen.hace(r.actualizadoEn, System.currentTimeMillis())}" else "Sin conectar")
                }
            }
            TarjetaBlanca(Modifier.fillMaxWidth(), onClick = { onNavigate(Routes.RESUMEN_PDF) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconoCirculo(Icons.Default.Share)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Titulo("Enviar resumen a Bienestar"); Sub("PDF de tu historial") }
                    Flecha()
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RowScope.MiniResumen(etiqueta: String, valor: String) {
    Column(Modifier.weight(1f).background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(etiqueta, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
        Text(valor, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
    }
}

/** Barras de agua de la semana actual del mes (días 1–7, 8–14…), hasta hoy. */
@Composable
private fun BarrasSemana(registros: List<com.inacap.health.data.RegistroBiometrico>, metaMl: Int, hoy: LocalDate) {
    val zona = ZoneId.systemDefault()
    val inicio = hoy.withDayOfMonth(((hoy.dayOfMonth - 1) / 7) * 7 + 1)
    val dias = (0L..6L).map { inicio.plusDays(it) }.filter { it.month == hoy.month }
    val porDia = registros.filter { it.aguaMl > 0 }.groupBy { java.time.Instant.ofEpochMilli(it.createdAt).atZone(zona).toLocalDate() }
        .mapValues { (_, l) -> l.sumOf { it.aguaMl } }
    val gris = MaterialTheme.colorScheme.outlineVariant
    val lleno = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        dias.forEach { d ->
            val ml = porDia[d] ?: 0
            val f = if (metaMl > 0) (ml.toFloat() / metaMl).coerceIn(0.06f, 1f) else 0.06f
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(f * 0.78f).background(if (d.isAfter(hoy)) Color.Transparent else if (ml >= metaMl) lleno else gris, RoundedCornerShape(6.dp)))
                Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------------
// Bienestar
// ---------------------------------------------------------------------

private data class Articulo(val emoji: String, val titulo: String, val texto: String) {
    val minutos get() = maxOf(1, texto.split(" ").size / 180)
}

private val ARTICULOS = listOf(
    Articulo("🥗", "Planifica tus colaciones", "Tener fruta, frutos secos o yogur a mano evita saltarte comidas durante las clases."),
    Articulo("💧", "Hidratación", "Lleva una botella contigo y toma agua a lo largo del día, no solo cuando tengas sed."),
    Articulo("🌙", "Descanso", "Dormir bien mejora tu ánimo, tu memoria y tu apetito. Intenta mantener horarios regulares."),
    Articulo("🚶", "Pausas activas", "Cada hora de estudio, levántate y muévete unos minutos. Ayuda a tu concentración."),
    Articulo("🍽️", "Comer con calma", "Comer sin pantallas y con tiempo te ayuda a reconocer cuándo estás satisfecho/a.")
)

/** Resumen del último chat (cargado de la nube sin bloquear la pantalla). */
private data class ResumenChat(val conversacion: Conversacion, val ultimo: Mensaje?, val noLeidos: Int)

/** Tarea 20 · Bienestar: pedir atención primero; luego conversaciones, cuestionarios y Aprende. */
@Composable
fun BienestarPestana(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HealthApp
    var chat by remember { mutableStateOf<ResumenChat?>(null) }
    var cargado by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        chat = runCatching {
            val repo = OrientacionRepositorio(context)
            val c = repo.conversaciones().maxByOrNull { EstadoPestanas.ms(it.actualizadaEn) } ?: return@runCatching null
            val mensajes = repo.mensajes(c.id)
            ResumenChat(c, EstadoPestanas.ultimoRecibido(mensajes), EstadoPestanas.noLeidos(mensajes, ChatsVistos.visto(context, c.id)))
        }.getOrNull()
        cargado = true
    }
    val resultados by app.database.resultadoCuestionarioDao().observarTodos().collectAsState(initial = emptyList())
    val scoff by app.database.resultadoScoffDao().getAll().collectAsState(initial = emptyList())
    val hechos = EstadoPestanas.cuestionariosDelMes(resultados, scoff, YearMonth.now())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BandaPestana("Bienestar", "Apoyo confidencial y autocuidado")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Primero y más visible: pedir atención.
            val forma = RoundedCornerShape(Diseno.ESQUINA_TARJETA)
            Column(Modifier.fillMaxWidth().shadow(4.dp, forma).clip(forma).background(bandaRoja()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Apoyo de Bienestar", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                Text("¿Necesitas conversar con alguien?", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Te atiende el equipo de Bienestar. Es confidencial.", color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).clickable { onNavigate(Routes.DERIVACION) }.padding(horizontal = 18.dp, vertical = 10.dp)
                ) { Text("Pedir atención", color = Diseno.BandaRoja, fontWeight = FontWeight.Bold) }
            }

            // Tus conversaciones
            val c = chat
            TarjetaBlanca(Modifier.fillMaxWidth(), onClick = {
                onNavigate(if (c?.conversacion?.tipo == "atencion") Routes.ATENCION else Routes.ORIENTACION)
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Titulo("Tus conversaciones", Modifier.weight(1f))
                    if ((c?.noLeidos ?: 0) > 0) {
                        Text("${c!!.noLeidos} ${if (c.noLeidos == 1) "nuevo" else "nuevos"}", color = Color.White, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.background(Diseno.BandaRoja, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                when {
                    c?.ultimo != null -> {
                        Text(c.ultimo.texto, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 14.dp)).padding(10.dp))
                        Spacer(Modifier.height(6.dp))
                        Sub("${if (c.conversacion.tipo == "atencion") "Atención de Bienestar" else "Orientación"} · ${RelojResumen.hace(EstadoPestanas.ms(c.ultimo.creadoEn), System.currentTimeMillis())}")
                    }
                    c != null -> Sub("Tu consulta está abierta; te avisaremos aquí cuando respondan.")
                    cargado -> Sub("Aún no tienes conversaciones. Toca para conversar con un tutor par.")
                    else -> Sub("Cargando…")
                }
            }

            // Cuestionarios con el avance del mes.
            TarjetaBlanca(Modifier.fillMaxWidth(), onClick = { onNavigate(Routes.CUESTIONARIOS) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Titulo("Cuestionarios", Modifier.weight(1f))
                    Sub("${hechos.count { it.value }} de ${hechos.size} este mes")
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("who5" to "WHO-5", "phq2" to "PHQ-2", "phq9" to "PHQ-9", "scoff" to "SCOFF").forEach { (k, n) ->
                        val hecho = hechos[k] == true
                        Text(
                            (if (hecho) "✓ " else "") + n, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                            color = if (hecho) verde() else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .border(1.dp, if (hecho) verde() else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .clickable { onNavigate(if (k == "scoff") Routes.SCOFF else Routes.CUESTIONARIOS) }
                                .padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Aprende: fila que se desliza.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Titulo("Aprende", Modifier.weight(1f))
                Text("Ver todo", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { onNavigate(Routes.EDUCATIVO) }.padding(4.dp))
            }
        }
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ARTICULOS, key = { it.titulo }) { a ->
                TarjetaBlanca(Modifier.width(150.dp).height(130.dp), onClick = { onNavigate(Routes.EDUCATIVO) }) {
                    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Text(a.emoji, fontSize = 20.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(a.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                    Sub("${a.minutos} min")
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            TarjetaBlanca(Modifier.fillMaxWidth(), onClick = { onNavigate(Routes.ORIENTACION) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Titulo("Orientación entre pares"); Sub("Conversa con un tutor par supervisado") }
                    Flecha()
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------------
// Perfil · bento grid
// ---------------------------------------------------------------------

private val ALTO_FILA = 86.dp
private val SEPARACION = 10.dp

/** Tarea 20 · Perfil como bento grid: 2 columnas iguales, filas de igual altura, estado real en cada tarjeta. */
@Composable
fun PerfilPestana(perfilViewModel: PerfilViewModel, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HealthApp
    val perfil by perfilViewModel.perfil.collectAsState()
    val oscuro = temaOscuroActivo()
    val correo = remember { app.sync.sesion.cuentaEmail }
    val (avance, faltan) = EstadoPestanas.avanceDatos(perfil)
    val recordatorios = remember { EstadoPestanas.recordatoriosActivos(context) }
    val nombre = perfil?.nombre?.trim().orEmpty()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BandaPestana(if (nombre.isBlank()) "Perfil" else nombre, "Tus datos, tu cuenta y tus preferencias") {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(36.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Text((nombre.firstOrNull() ?: '·').uppercase(), color = Diseno.BandaRoja, fontWeight = FontWeight.Bold)
                }
                Etiqueta(perfil?.sede?.takeIf { it.isNotBlank() }?.let { "Sede $it" } ?: "Sin sede")
                Etiqueta(if (correo != null) "Con correo" else "Sin cuenta")
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(SEPARACION)) {
            // Mis datos (2 filas) | Recordatorios / Modo noche
            Row(Modifier.fillMaxWidth().height(ALTO_FILA * 2 + SEPARACION), horizontalArrangement = Arrangement.spacedBy(SEPARACION)) {
                TarjetaBento(Modifier.weight(1f).fillMaxHeight(), Icons.Default.AccountCircle, "Mis datos",
                    if (faltan.isEmpty()) "Completos" else "Falta: ${faltan.joinToString(", ")}",
                    colorEstado = if (faltan.isEmpty()) verde() else MaterialTheme.colorScheme.primary,
                    onClick = { onNavigate(Routes.PERFIL_DATOS) }
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { avance / 100f }, modifier = Modifier.size(56.dp), strokeWidth = 6.dp,
                            color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant)
                        Text("$avance%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(SEPARACION)) {
                    TarjetaBento(Modifier.fillMaxWidth().weight(1f), Icons.Default.Notifications, "Recordatorios",
                        if (recordatorios == 0) "Ninguno activo" else "$recordatorios ${if (recordatorios == 1) "activo" else "activos"}",
                        colorEstado = if (recordatorios > 0) verde() else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onNavigate(Routes.RECORDATORIOS) })
                    TarjetaBento(Modifier.fillMaxWidth().weight(1f), Iconos.Luna, "Modo noche", if (oscuro) "Activado" else "Desactivado",
                        onClick = { onNavigate(Routes.APARIENCIA) },
                        esquina = { Switch(checked = oscuro, onCheckedChange = { Apariencia.alternar(context, oscuro) }, modifier = Modifier.height(24.dp)) })
                }
            }
            // Cuenta (ancha)
            TarjetaBento(Modifier.fillMaxWidth().height(ALTO_FILA), Icons.Default.AccountCircle, "Cuenta",
                EstadoPestanas.abreviarCorreo(correo)?.let { "$it · este teléfono" } ?: "Sin cuenta · iniciar sesión o crear una",
                onClick = { onNavigate(Routes.CUENTA) }, esquina = { Flecha() })
            Row(Modifier.fillMaxWidth().height(ALTO_FILA), horizontalArrangement = Arrangement.spacedBy(SEPARACION)) {
                val pin = app.bloqueo.tienePin
                TarjetaBento(Modifier.weight(1f).fillMaxHeight(), Icons.Default.Lock, "Bloqueo", if (pin) "PIN activo" else "Desactivado",
                    colorEstado = if (pin) verde() else MaterialTheme.colorScheme.onSurfaceVariant, onClick = { onNavigate(Routes.BLOQUEO) })
                val cifrada = remember { com.inacap.health.seguridad.CifradoBaseDatos.estaCifrada(context) }
                TarjetaBento(Modifier.weight(1f).fillMaxHeight(), Icons.Default.CheckCircle, "Seguridad", if (cifrada) "Datos cifrados" else "Revisar",
                    colorEstado = if (cifrada) verde() else MaterialTheme.colorScheme.onSurfaceVariant, onClick = { onNavigate(Routes.SEGURIDAD) })
            }
            Row(Modifier.fillMaxWidth().height(ALTO_FILA), horizontalArrangement = Arrangement.spacedBy(SEPARACION)) {
                // «Eliminar mis datos» sigue dentro de Privacidad (no suelto en la grilla).
                TarjetaBento(Modifier.weight(1f).fillMaxHeight(), Icons.Default.Settings, "Privacidad",
                    "Compartir: ${if (perfil?.compartirNube == true) "sí" else "no"}", onClick = { onNavigate(Routes.PRIVACIDAD) })
                TarjetaBento(Modifier.weight(1f).fillMaxHeight(), Icons.AutoMirrored.Filled.List, "Términos",
                    "Versión ${TextosLegales.VERSION}", onClick = { onNavigate(Routes.TERMINOS) })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Etiqueta(texto: String) = Text(
    texto, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1,
    modifier = Modifier.background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
)

/** Misma estructura en todas: ícono arriba a la izquierda; abajo el nombre y su estado en una línea. */
@Composable
private fun TarjetaBento(
    modifier: Modifier,
    icono: ImageVector,
    titulo: String,
    estado: String,
    colorEstado: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
    esquina: (@Composable () -> Unit)? = null,
    centro: (@Composable () -> Unit)? = null
) {
    TarjetaBlanca(modifier, onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.weight(1f))
            esquina?.invoke()
        }
        if (centro != null) { Spacer(Modifier.weight(1f)); centro(); Spacer(Modifier.weight(1f)) } else Spacer(Modifier.weight(1f))
        Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Sub(estado, colorEstado)
    }
}

/** Para la tarjeta de Mis datos: el anillo va como contenido central. */
@Composable
private fun TarjetaBento(
    modifier: Modifier,
    icono: ImageVector,
    titulo: String,
    estado: String,
    colorEstado: Color,
    onClick: () -> Unit,
    centro: @Composable () -> Unit
) = TarjetaBento(modifier, icono, titulo, estado, colorEstado, onClick, esquina = null, centro = centro)
