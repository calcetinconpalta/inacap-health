package com.inacap.health.ui.screens.inicio

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Person
import kotlinx.coroutines.launch
import com.inacap.health.data.datosCompletos
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inacap.health.ui.nav.Routes
import com.inacap.health.ui.screens.perfil.PerfilViewModel
import com.inacap.health.ui.screens.salud.SaludLogic
import com.inacap.health.ui.screens.salud.SaludViewModel
import com.inacap.health.ui.theme.BorderGray
import com.inacap.health.ui.theme.RedInacap
import java.util.Locale

@Composable
fun InicioScreen(
    perfilViewModel: PerfilViewModel,
    saludViewModel: SaludViewModel,
    onNavigate: (String) -> Unit
) {
    val perfilState by perfilViewModel.perfil.collectAsState()
    val aguaHoyMl by saludViewModel.aguaHoyMl.collectAsState()
    val metaAguaMl by saludViewModel.metaAguaMl.collectAsState()
    val ultimoRegistro by saludViewModel.ultimoRegistro.collectAsState()

    val nombre = perfilState?.nombre?.trim()
    val saludo = if (!nombre.isNullOrBlank()) "Hola, $nombre" else "Hola"

    val pasosHoy by saludViewModel.pasosHoy.collectAsState()
    val metaPasos by saludViewModel.metaPasos.collectAsState()
    val ultimoPulso by saludViewModel.ultimoPulso.collectAsState()
    val reloj by saludViewModel.reloj.collectAsState()
    val animoHoy by saludViewModel.animoHoy.collectAsState()
    val sugerirApoyo by saludViewModel.sugerirApoyoAnimo.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val oscuro = com.inacap.health.ui.theme.temaOscuroActivo()
    val cl = Locale.forLanguageTag("es-CL")
    val hoy = remember {
        java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", cl))
            .replaceFirstChar { it.titlecase(cl) }
    }
    val banda = if (oscuro) com.inacap.health.ui.theme.Diseno.BandaRojaNoche else com.inacap.health.ui.theme.Diseno.BandaRoja
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { DatoHero.entries.size })
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val quieto = sinAnimaciones()
    val pagina = pager.paginaVisible()
    val logica = com.inacap.health.ui.screens.salud.SaludLogic

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Banda roja INACAP: marca, fecha, saludo y el hero (tarea 18e).
        Column(
            Modifier.fillMaxWidth().background(banda, androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("INACAP Health", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                androidx.compose.material3.IconButton(onClick = { com.inacap.health.ui.theme.Apariencia.alternar(context, oscuro) }) {
                    Icon(
                        if (oscuro) com.inacap.health.ui.components.Iconos.Sol else com.inacap.health.ui.components.Iconos.Luna,
                        contentDescription = if (oscuro) "Activar modo claro" else "Activar modo noche", tint = Color.White
                    )
                }
            }
            Text(hoy, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
            Text(saludo, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            HeroInicio(pager, banda) { dato ->
                when (dato) {
                    DatoHero.AGUA -> ContenidoHero(
                        "${logica.vasos(aguaHoyMl)} de ${logica.metaVasos(metaAguaMl)} vasos",
                        "${logica.litros(aguaHoyMl)} de ${logica.litros(metaAguaMl, 1)} L hoy"
                    )
                    DatoHero.PASOS -> ContenidoHero(String.format(cl, "%,d", pasosHoy), "pasos hoy · meta ${String.format(cl, "%,d", metaPasos)}")
                    DatoHero.PULSO -> {
                        val bpm = reloj?.bpm ?: ultimoPulso?.frecuenciaCardiaca
                        ContenidoHero(bpm?.let { "$it lpm" } ?: "—", if (bpm == null) "Mide tu pulso con la cámara" else "Última medición")
                    }
                    DatoHero.IMC -> ContenidoHero(
                        ultimoRegistro?.imc?.let { String.format(cl, "%.1f", it) } ?: "—",
                        ultimoRegistro?.imc?.let { logica.categoriaImc(it) } ?: "Calcula tu IMC"
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IconosHero(
                seleccionado = pagina,
                iconos = listOf(com.inacap.health.ui.components.Iconos.Gota, com.inacap.health.ui.components.Iconos.Pasos,
                    androidx.compose.material.icons.Icons.Default.Favorite, androidx.compose.material.icons.Icons.Default.Person),
                onElegir = { i -> scope.launch { if (quieto) pager.scrollToPage(i) else pager.animateScrollToPage(i) } }
            )
            // El botón cambia según el dato.
            when (DatoHero.entries[pagina]) {
                DatoHero.AGUA -> {
                    BotonPrincipal("+ 1 vaso") { saludViewModel.agregarAgua() }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(logica.textoProgresoAgua(aguaHoyMl, metaAguaMl), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = { saludViewModel.quitarAgua() }) { Text("Quitar 1 vaso") }
                    }
                    if (ultimoRegistro == null) {
                        Text("Meta general de 2 L. Calcula tu IMC y te recomendamos una meta según tu peso.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DatoHero.PASOS -> BotonPrincipal("Ver mis pasos") { onNavigate(Routes.HISTORIAL) }
                DatoHero.PULSO -> BotonPrincipal("Medir pulso") { onNavigate(Routes.PULSO) }
                DatoHero.IMC -> BotonPrincipal("Calcular IMC") { onNavigate(Routes.IMC_AGUA) }
            }

            // Tarea 14: si faltan datos (o se omitieron al crear la cuenta), se invita a completarlos.
            if (perfilState != null && !perfilState.datosCompletos()) {
                AvisoCompletarDatos(onCompletar = { onNavigate(Routes.PERFIL_DATOS) })
            }

            // Tarea 18g: check-in diario de ánimo (el SCOFF va en Bienestar, no en Inicio).
            CheckInAnimo(
                nivelHoy = animoHoy, sugerirApoyo = sugerirApoyo,
                onElegir = { saludViewModel.guardarAnimo(it) },
                onCuestionarios = { onNavigate(Routes.CUESTIONARIOS) },
                onBienestar = { onNavigate(Routes.DERIVACION) }
            )

            // Pasos en vivo (sensor) y meta.
            TarjetaPasos(saludViewModel = saludViewModel, onNavigate = onNavigate)

            // Reloj: se trae solo al abrir y cada 5 min mientras Inicio está abierto.
            TarjetaReloj(saludViewModel = saludViewModel, onNavigate = onNavigate)

            Text(
                text = "INACAP Health es un primer filtro de autocuidado. No entrega diagnósticos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** «Completa tus datos para recomendaciones personalizadas → Completar». Desaparece al completarlos. */
@Composable
internal fun AvisoCompletarDatos(onCompletar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onCompletar),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Completa tus datos para recomendaciones personalizadas",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.TextButton(onClick = onCompletar) { Text("Completar →") }
        }
    }
}

@Composable
private fun TarjetaReloj(saludViewModel: SaludViewModel, onNavigate: (String) -> Unit) {
    val datos by saludViewModel.reloj.collectAsState()
    val ciclo = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) { ciclo.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) { saludViewModel.sincronizarRelojPeriodico() } }
    // Para que "hace N min" avance aunque no lleguen datos nuevos.
    var ahora by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); ahora = System.currentTimeMillis() } }
    val d = datos ?: return
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onNavigate(Routes.HEALTH_CONNECT) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Tu reloj", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                com.inacap.health.salud.RelojResumen.textoPulso(d, ahora) ?: "Aún no llega pulso del reloj en las últimas 24 horas.",
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface
            )
            d.pasosHoy?.let {
                Text(
                    "Pasos del reloj hoy: ${String.format(Locale.forLanguageTag("es-CL"), "%,d", it)}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                "Revisado ${com.inacap.health.salud.RelojResumen.hace(d.actualizadoEn, ahora)}. Los datos llegan desde la app de tu reloj con unos minutos de atraso; no es en tiempo real.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TarjetaPasos(saludViewModel: SaludViewModel, onNavigate: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pasos by saludViewModel.pasosHoy.collectAsState()
    val meta by saludViewModel.metaPasos.collectAsState()
    var permiso by remember { mutableStateOf(saludViewModel.tienePermisoPasos) }
    var elegirMeta by remember { mutableStateOf(false) }
    val lanzador = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { ok -> permiso = ok; if (ok) saludViewModel.actualizarPasos() }

    LaunchedEffect(permiso) { if (permiso) saludViewModel.actualizarPasos() }
    // Pasos en vivo: escucha el sensor solo mientras Inicio está a la vista (se detiene al
    // salir de la pantalla o al dejar la app en segundo plano).
    val ciclo = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(permiso) {
        if (permiso) ciclo.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) { saludViewModel.pasosEnVivo() }
    }
    var avisoBateria by remember { mutableStateOf(com.inacap.health.salud.AhorroBateria.mostrarAviso(context)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(com.inacap.health.ui.components.Iconos.Pasos, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                Text("Pasos de hoy", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            when {
                !saludViewModel.tieneSensorPasos -> {
                    Text(
                        "Tu teléfono no tiene contador de pasos. Puedes traerlos desde tu reloj.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { onNavigate(Routes.HEALTH_CONNECT) }) { Text("Conectar mi reloj") }
                }
                !permiso -> {
                    Text(
                        "Activa el contador para ver tu movimiento diario. Los pasos se guardan en tu teléfono.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            lanzador.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                        } else { permiso = true }
                    }) { Text("Activar contador de pasos") }
                }
                else -> {
                    val progreso = if (meta > 0) (pasos.toFloat() / meta).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(
                        progress = { progreso },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = BorderGray
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${String.format(Locale.forLanguageTag("es-CL"), "%,d", pasos)} / ${String.format(Locale.forLanguageTag("es-CL"), "%,d", meta)}",
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface
                        )
                        androidx.compose.material3.TextButton(onClick = { elegirMeta = !elegirMeta }) { Text("Cambiar meta") }
                    }
                    if (elegirMeta) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.inacap.health.salud.CalculoPasos.METAS.forEach { m ->
                                androidx.compose.material3.FilterChip(
                                    selected = m == meta,
                                    onClick = { saludViewModel.cambiarMetaPasos(m); elegirMeta = false },
                                    label = { Text("${m / 1000}k") }
                                )
                            }
                        }
                    }
                    Text(
                        if (pasos >= meta) "¡Meta cumplida! Cada paso suma a tu bienestar." else "Moverte un poco durante la jornada también cuenta.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (avisoBateria) {
                        Text(
                            "Con la app abierta los pasos se cuentan en vivo. En este teléfono, para que sigan contando con la app cerrada, quita INACAP Health del ahorro de batería.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.TextButton(onClick = { com.inacap.health.salud.AhorroBateria.abrirAjustes(context) }) { Text("Abrir ajustes") }
                            androidx.compose.material3.TextButton(onClick = { avisoBateria = false }) { Text("Ahora no") }
                        }
                    }
                }
            }
        }
    }
}
