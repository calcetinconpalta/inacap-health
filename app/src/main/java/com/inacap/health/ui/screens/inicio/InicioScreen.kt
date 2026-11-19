package com.inacap.health.ui.screens.inicio

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Encabezado: fecha, saludo y modo noche
        val context = androidx.compose.ui.platform.LocalContext.current
        val oscuro = com.inacap.health.ui.theme.temaOscuroActivo()
        val hoy = remember {
            java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-CL")))
                .replaceFirstChar { it.titlecase(Locale.forLanguageTag("es-CL")) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(hoy, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Text(saludo, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text("Un paso a la vez. Así va tu día.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.FilledTonalIconButton(
                onClick = { com.inacap.health.ui.theme.Apariencia.alternar(context, oscuro) },
                colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    if (oscuro) com.inacap.health.ui.components.Iconos.Sol else com.inacap.health.ui.components.Iconos.Luna,
                    contentDescription = if (oscuro) "Activar modo claro" else "Activar modo noche",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Tarjeta Agua
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.align(Alignment.Start), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(com.inacap.health.ui.components.Iconos.Gota, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Hidratación diaria",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(160.dp)
                ) {
                    val progress = if (metaAguaMl > 0) (aguaHoyMl.toFloat() / metaAguaMl.toFloat()).coerceIn(0f, 1f) else 0f
                    val strokeWidth = with(LocalDensity.current) { 14.dp.toPx() }
                    val pista = BorderGray
                    val relleno = MaterialTheme.colorScheme.secondary

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    ) {
                        drawArc(
                            color = pista,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        if (progress > 0f) {
                            drawArc(
                                color = relleno,
                                startAngle = -90f,
                                sweepAngle = progress * 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$aguaHoyMl / $metaAguaMl ml",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Agua registrada",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { saludViewModel.agregarAgua() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ 250 ml")
                    }
                    OutlinedButton(
                        onClick = { saludViewModel.quitarAgua() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Quitar 250 ml")
                    }
                }
            }
        }

        // Tarjeta Pasos (HU-10: metas de movimiento, sin calorías)
        TarjetaPasos(saludViewModel = saludViewModel, onNavigate = onNavigate)

        // Tarjeta IMC
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Índice de Masa Corporal (IMC)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (ultimoRegistro != null && ultimoRegistro?.imc != null) {
                    val imcVal = ultimoRegistro!!.imc!!
                    val categoria = SaludLogic.categoriaImc(imcVal)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "IMC: ${String.format(Locale.US, "%.1f", imcVal)}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = categoria,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = { onNavigate(Routes.IMC_AGUA) }) {
                            Text("Calcular")
                        }
                    }
                } else {
                    Text(
                        text = "Aún no hay datos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { onNavigate(Routes.IMC_AGUA) },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text("Calcular")
                    }
                }
            }
        }

        // Dos accesos rápidos
        Text(
            text = "Accesos rápidos",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Acceso Test SCOFF
            Card(
                onClick = { onNavigate(Routes.SCOFF) },
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Test SCOFF",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Autocuidado alimentario",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Acceso Medir pulso (HU-05)
            Card(
                onClick = { onNavigate(Routes.PULSO) },
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Medir pulso",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Con la cámara · estimación",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Texto pequeño gris al final
        Text(
            text = "INACAP Health es un primer filtro de autocuidado. No entrega diagnósticos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                }
            }
        }
    }
}
