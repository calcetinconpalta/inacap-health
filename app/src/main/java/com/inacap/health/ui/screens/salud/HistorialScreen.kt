package com.inacap.health.ui.screens.salud

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inacap.health.data.RegistroBiometrico
import com.inacap.health.ui.nav.Routes
import com.inacap.health.ui.theme.BackgroundWhite
import com.inacap.health.ui.theme.BorderGray
import com.inacap.health.ui.theme.RedInacap
import com.inacap.health.ui.theme.SurfaceLight
import com.inacap.health.ui.theme.TextPrimary
import com.inacap.health.ui.theme.TextSecondary
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** La app está solo en español: formato fijo de Chile (coma decimal). */
private val LOCALE_CL: Locale = Locale.Builder().setLanguage("es").setRegion("CL").build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialScreen(
    viewModel: HistorialViewModel = viewModel(),
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Esta información es solo orientativa y no reemplaza a un profesional de salud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SecondaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = RedInacap
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Peso e IMC") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Agua") }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("Actividad") }
                )
                Tab(
                    selected = selectedTabIndex == 3,
                    onClick = { selectedTabIndex = 3 },
                    text = { Text("Tests") }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (selectedTabIndex) {
                    0 -> TabPesoImcContent(viewModel = viewModel, onNavigate = onNavigate)
                    1 -> TabAguaContent(viewModel = viewModel)
                    2 -> TabActividadContent(viewModel = viewModel, onNavigate = onNavigate)
                    3 -> TabTestsContent(viewModel = viewModel, onNavigate = onNavigate)
                }
            }
        }
    }
}

@Composable
private fun TabPesoImcContent(
    viewModel: HistorialViewModel,
    onNavigate: (String) -> Unit
) {
    val registros by viewModel.registrosPesoAsc.collectAsState()
    var opcionSeleccionada by remember { mutableStateOf("Peso") }

    if (registros.size < 2) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Aún no hay suficientes datos. Calcula tu IMC en distintos días para ver tu evolución.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onNavigate(Routes.IMC_AGUA) }) {
                Text("Calcular IMC")
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val primer = registros.first()
            val ultimo = registros.last()
            val conPeso = registros.filter { it.pesoKg != null }
            val diffPeso = if (conPeso.size >= 2) conPeso.last().pesoKg!! - conPeso.first().pesoKg!! else 0.0
            val diffStr = String.format(LOCALE_CL, "%.1f", abs(diffPeso))
            val diffText = if (diffPeso > 0) "Cambio de +$diffStr kg" else if (diffPeso < 0) "Cambio de -$diffStr kg" else "Cambio de 0.0 kg"

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Primer registro vs. último registro",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Primer registro (${formatEpochDate(primer.createdAt)})",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "${primer.pesoKg?.let { "$it kg | " } ?: ""}IMC ${String.format(LOCALE_CL, "%.1f", primer.imc)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Último registro (${formatEpochDate(ultimo.createdAt)})",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "${ultimo.pesoKg?.let { "$it kg | " } ?: ""}IMC ${String.format(LOCALE_CL, "%.1f", ultimo.imc)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = diffText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = opcionSeleccionada == "Peso",
                    onClick = { opcionSeleccionada = "Peso" },
                    label = { Text("Peso") }
                )
                FilterChip(
                    selected = opcionSeleccionada == "IMC",
                    onClick = { opcionSeleccionada = "IMC" },
                    label = { Text("IMC") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LineChart(
                registros = if (opcionSeleccionada == "Peso") registros.filter { it.pesoKg != null } else registros,
                opcionSeleccionada = opcionSeleccionada,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Historial de registros",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            val registrosDesc = remember(registros) { registros.reversed() }
            registrosDesc.forEach { reg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = formatEpochDate(reg.createdAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = if (reg.pesoKg != null) "Peso: ${reg.pesoKg} kg | Estatura: ${reg.estaturaCm?.toInt()} cm" else "Recuperado desde tu cuenta (sin peso)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "IMC: ${String.format(LOCALE_CL, "%.1f", reg.imc)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = RedInacap
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabAguaContent(
    viewModel: HistorialViewModel
) {
    val registrosAgua by viewModel.registrosAgua7Dias.collectAsState()
    val metaAguaMl by viewModel.metaAguaMl.collectAsState()

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    val dias = remember(registrosAgua) {
        (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val etiqueta = when (date.dayOfWeek) {
                DayOfWeek.MONDAY -> "L"
                DayOfWeek.TUESDAY -> "M"
                DayOfWeek.WEDNESDAY -> "M"
                DayOfWeek.THURSDAY -> "J"
                DayOfWeek.FRIDAY -> "V"
                DayOfWeek.SATURDAY -> "S"
                DayOfWeek.SUNDAY -> "D"
                null -> ""
            }
            val totalMl = registrosAgua.filter { reg ->
                Instant.ofEpochMilli(reg.createdAt).atZone(zone).toLocalDate() == date
            }.sumOf { it.aguaMl }
            DiaAguaData(date, etiqueta, totalMl)
        }
    }

    val total7Dias = dias.sumOf { it.totalMl }
    val promedioMl = (total7Dias / 7.0).roundToInt()
    val diasMeta = dias.count { it.totalMl >= metaAguaMl }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Consumo de agua (últimos 7 días)",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        BarChart(
            dias = dias,
            metaAguaMl = metaAguaMl,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Promedio de los últimos 7 días: ${com.inacap.health.ui.screens.salud.SaludLogic.litrosYVasos(promedioMl)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Días en que alcanzaste tu meta: $diasMeta de 7",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
        }
    }
}

/** HU-14 · Pasos de los últimos 7 días y mediciones de pulso. */
@Composable
private fun TabActividadContent(
    viewModel: HistorialViewModel,
    onNavigate: (String) -> Unit
) {
    val pasos by viewModel.pasos7Dias.collectAsState()
    val pulsos by viewModel.pulsos.collectAsState()
    val meta = viewModel.metaPasos
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val letras = mapOf(
        DayOfWeek.MONDAY to "L", DayOfWeek.TUESDAY to "M", DayOfWeek.WEDNESDAY to "M", DayOfWeek.THURSDAY to "J",
        DayOfWeek.FRIDAY to "V", DayOfWeek.SATURDAY to "S", DayOfWeek.SUNDAY to "D"
    )
    val dias = remember(pasos) {
        (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val total = pasos.firstOrNull { it.fecha == date.toString() }?.pasos ?: 0
            DiaAguaData(date, letras[date.dayOfWeek] ?: "", total)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Pasos (últimos 7 días)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        BarChart(dias = dias, metaAguaMl = meta, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceLight), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Promedio diario: ${String.format(LOCALE_CL, "%,d", dias.sumOf { it.totalMl } / 7)} pasos", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Días en que alcanzaste tu meta (${String.format(LOCALE_CL, "%,d", meta)}): ${dias.count { it.totalMl >= meta }} de 7", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Pulso", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        if (pulsos.isEmpty()) {
            Text("Aún no tienes mediciones de pulso.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { onNavigate(Routes.PULSO) }) { Text("Medir con la cámara") }
        } else {
            pulsos.take(10).forEach { reg ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("${reg.frecuenciaCardiaca} lpm", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        Text(
                            when (reg.fuente) { "health_connect" -> "Reloj (Health Connect) · promedio del día"; "reloj" -> "Reloj por Bluetooth"; else -> "Cámara · estimación aproximada" },
                            style = MaterialTheme.typography.bodySmall, color = TextSecondary
                        )
                    }
                    Text(formatEpochDateTime(reg.createdAt), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun TabTestsContent(
    viewModel: HistorialViewModel,
    onNavigate: (String) -> Unit
) {
    val resultados by viewModel.resultadosScoffDesc.collectAsState()

    if (resultados.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Aún no has realizado el test.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Resultados Test SCOFF",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            resultados.forEach { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatEpochDateTime(item.createdAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "Puntaje: ${item.puntaje} de 5",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val textoDerivacion = if (item.requiereDerivacion) {
                            "Se sugirió conocer opciones de apoyo"
                        } else {
                            "Sin necesidad de apoyo adicional por ahora"
                        }
                        Text(
                            text = textoDerivacion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        if (item.requiereDerivacion) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { onNavigate(Routes.DERIVACION) }
                            ) {
                                Text("Conocer opciones de apoyo")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LineChart(
    registros: List<RegistroBiometrico>,
    opcionSeleccionada: String,
    modifier: Modifier = Modifier
) {
    if (registros.isEmpty()) return

    val values = remember(registros, opcionSeleccionada) {
        registros.map { if (opcionSeleccionada == "Peso") (it.pesoKg ?: 0.0) else (it.imc ?: 0.0) }
    }

    val dateFirst = remember(registros) { formatEpochShortDate(registros.first().createdAt) }
    val dateLast = remember(registros) { formatEpochShortDate(registros.last().createdAt) }

    // Colores del tema leídos fuera del Canvas (cambian con el modo noche).
    val RedInacap = com.inacap.health.ui.theme.RedInacap
    val BorderGray = com.inacap.health.ui.theme.BorderGray
    val BackgroundWhite = com.inacap.health.ui.theme.BackgroundWhite
    val SurfaceLight = com.inacap.health.ui.theme.SurfaceLight
    val TextSecondary = com.inacap.health.ui.theme.TextSecondary
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val width = size.width
            val height = size.height

            val paddingLeft = 40.dp.toPx()
            val paddingRight = 20.dp.toPx()
            val paddingTop = 20.dp.toPx()
            val paddingBottom = 30.dp.toPx()

            val chartWidth = width - paddingLeft - paddingRight
            val chartHeight = height - paddingTop - paddingBottom

            val minY = values.minOrNull() ?: 0.0
            val maxY = values.maxOrNull() ?: 100.0
            val rangeY = if (maxY - minY < 0.1) 1.0 else (maxY - minY)
            val chartMinY = minY - (rangeY * 0.1)
            val chartMaxY = maxY + (rangeY * 0.1)

            // Draw Y-axis grid lines
            val numGridLines = 3
            for (i in 0..numGridLines) {
                val yFraction = i.toFloat() / numGridLines
                val yPos = paddingTop + chartHeight * yFraction
                drawLine(
                    color = BorderGray,
                    start = Offset(paddingLeft, yPos),
                    end = Offset(width - paddingRight, yPos),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Draw X-axis line
            drawLine(
                color = BorderGray,
                start = Offset(paddingLeft, height - paddingBottom),
                end = Offset(width - paddingRight, height - paddingBottom),
                strokeWidth = 2.dp.toPx()
            )

            // Calculate points
            val points = values.mapIndexed { index, value ->
                val xFraction = if (values.size > 1) index.toFloat() / (values.size - 1) else 0.5f
                val x = paddingLeft + xFraction * chartWidth
                val yFraction = ((value - chartMinY) / (chartMaxY - chartMinY)).toFloat()
                val y = height - paddingBottom - (yFraction * chartHeight)
                Offset(x, y)
            }

            // Draw line path
            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = RedInacap,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // Draw circles at each point
            points.forEach { point ->
                drawCircle(
                    color = RedInacap,
                    radius = 5.dp.toPx(),
                    center = point
                )
                drawCircle(
                    color = BackgroundWhite,
                    radius = 2.5.dp.toPx(),
                    center = point
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp, end = 20.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = dateFirst, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(text = dateLast, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

private data class DiaAguaData(
    val fecha: LocalDate,
    val etiqueta: String,
    val totalMl: Int
)

@Composable
private fun BarChart(
    dias: List<DiaAguaData>,
    metaAguaMl: Int,
    modifier: Modifier = Modifier
) {
    val maxDaily = dias.maxOfOrNull { it.totalMl } ?: 0
    val maxScaleY = maxOf(metaAguaMl, maxDaily, 1000) * 1.15f

    // Colores del tema leídos fuera del Canvas (cambian con el modo noche).
    val RedInacap = com.inacap.health.ui.theme.RedInacap
    val BorderGray = com.inacap.health.ui.theme.BorderGray
    val BackgroundWhite = com.inacap.health.ui.theme.BackgroundWhite
    val SurfaceLight = com.inacap.health.ui.theme.SurfaceLight
    val TextSecondary = com.inacap.health.ui.theme.TextSecondary
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            val width = size.width
            val height = size.height

            val paddingTop = 20.dp.toPx()
            val paddingBottom = 30.dp.toPx()
            val paddingLeft = 16.dp.toPx()
            val paddingRight = 16.dp.toPx()

            val chartWidth = width - paddingLeft - paddingRight
            val chartHeight = height - paddingTop - paddingBottom

            // Draw goal line (dashed horizontal line)
            val goalYFraction = (metaAguaMl.toFloat() / maxScaleY)
            val goalY = height - paddingBottom - (goalYFraction * chartHeight)

            drawLine(
                color = TextSecondary,
                start = Offset(paddingLeft, goalY),
                end = Offset(width - paddingRight, goalY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            )

            // Draw bars
            val numBars = dias.size
            val barGroupWidth = chartWidth / numBars
            val barWidth = (barGroupWidth * 0.45f).coerceAtMost(36.dp.toPx())

            dias.forEachIndexed { index, dia ->
                val centerX = paddingLeft + (index + 0.5f) * barGroupWidth
                val left = centerX - (barWidth / 2f)
                val topTrack = paddingTop
                val bottomTrack = height - paddingBottom

                // Track (background)
                drawRoundRect(
                    color = SurfaceLight,
                    topLeft = Offset(left, topTrack),
                    size = Size(barWidth, chartHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                // Foreground bar
                val fillFraction = (dia.totalMl.toFloat() / maxScaleY).coerceIn(0f, 1f)
                val barFillHeight = fillFraction * chartHeight
                val barTop = bottomTrack - barFillHeight

                if (barFillHeight > 0f) {
                    drawRoundRect(
                        color = RedInacap,
                        topLeft = Offset(left, barTop),
                        size = Size(barWidth, barFillHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            dias.forEach { dia ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dia.etiqueta,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

private fun formatEpochDate(epochMilli: Long): String {
    return Instant.ofEpochMilli(epochMilli)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE_CL))
}

private fun formatEpochDateTime(epochMilli: Long): String {
    return Instant.ofEpochMilli(epochMilli)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", LOCALE_CL))
}

private fun formatEpochShortDate(epochMilli: Long): String {
    return Instant.ofEpochMilli(epochMilli)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM", LOCALE_CL))
}
