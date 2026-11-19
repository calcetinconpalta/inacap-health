package com.inacap.health.ui.screens.bienestar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inacap.health.ui.theme.BorderGray

private data class NivelPiramide(
    val id: Int,
    val nombre: String,
    val labelCorto: String,
    val descripcion: String
)

private data class Habito(
    val titulo: String,
    val descripcion: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EducativoScreen(
    onBack: () -> Unit
) {
    val niveles = remember {
        listOf(
            NivelPiramide(
                id = 1,
                nombre = "Frutas y verduras",
                labelCorto = "1. Frutas y verduras",
                descripcion = "Aportan fibra, vitaminas y minerales. Intenta incluirlas en la mayoría de tus comidas, de distintos colores."
            ),
            NivelPiramide(
                id = 2,
                nombre = "Cereales, legumbres y tubérculos",
                labelCorto = "2. Cereales y legumbres",
                descripcion = "Son tu fuente principal de energía. Las legumbres y los cereales integrales te mantienen con energía por más tiempo."
            ),
            NivelPiramide(
                id = 3,
                nombre = "Lácteos y proteínas",
                labelCorto = "3. Lácteos y proteínas",
                descripcion = "Lácteos, huevos, pescado, carnes y legumbres ayudan a construir y reparar tu cuerpo."
            ),
            NivelPiramide(
                id = 4,
                nombre = "Grasas saludables",
                labelCorto = "4. Grasas saludables",
                descripcion = "Palta, frutos secos y aceite de oliva, en porciones moderadas."
            ),
            NivelPiramide(
                id = 5,
                nombre = "Dulces y ultraprocesados",
                labelCorto = "5. Dulces y ultraprocesados",
                descripcion = "Disfrútalos de vez en cuando; no hace falta prohibirlos."
            )
        )
    }

    val habitos = remember {
        listOf(
            Habito(
                titulo = "Hidratación",
                descripcion = "Lleva una botella contigo y toma agua a lo largo del día, no solo cuando tengas sed."
            ),
            Habito(
                titulo = "Pausas activas",
                descripcion = "Cada hora de estudio, levántate y muévete unos minutos. Ayuda a tu concentración."
            ),
            Habito(
                titulo = "Descanso",
                descripcion = "Dormir bien mejora tu ánimo, tu memoria y tu apetito. Intenta mantener horarios regulares."
            ),
            Habito(
                titulo = "Comer con calma",
                descripcion = "Comer sin pantallas y con tiempo te ayuda a reconocer cuándo estás satisfecho/a."
            ),
            Habito(
                titulo = "Planifica tus colaciones",
                descripcion = "Tener fruta, frutos secos o yogur a mano evita saltarte comidas durante las clases."
            )
        )
    }

    var selectedNivelId by remember { mutableStateOf(1) } // Por defecto el de la base (1)
    val selectedNivel = niveles.first { it.id == selectedNivelId }

    val primaryColor = MaterialTheme.colorScheme.primary
    val textMeasurer = rememberTextMeasurer()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Aprende",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Encabezado
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Aprende",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Información general para cuidar tu bienestar",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Sección Pirámide
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Pirámide de alimentación",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Canvas Pirámide
                // Tonos calmados que se adaptan al modo claro y noche.
                val calma = MaterialTheme.colorScheme.secondaryContainer
                val fondoPiramide = MaterialTheme.colorScheme.surface
                val grayShades = listOf(1f, 0.8f, 0.6f, 0.45f, 0.3f).map { calma.copy(alpha = it).compositeOver(fondoPiramide) }
                val separador = MaterialTheme.colorScheme.background
                val textoNivel = MaterialTheme.colorScheme.onSecondaryContainer
                val textoSeleccion = MaterialTheme.colorScheme.onPrimary

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val sliceHeight = size.height / 5f
                                val indexFromTop = (offset.y / sliceHeight).toInt().coerceIn(0, 4)
                                val tappedId = 5 - indexFromTop
                                selectedNivelId = tappedId
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val sliceHeight = h / 5f

                    // Top tip width (y=0) and Bottom base width (y=h)
                    val topFactor = 0.18f
                    val bottomFactor = 0.92f

                    for (i in 0 until 5) {
                        val levelId = 5 - i
                        val yStart = i * sliceHeight
                        val yEnd = (i + 1) * sliceHeight

                        val topFrac = topFactor + (i / 5f) * (bottomFactor - topFactor)
                        val bottomFrac = topFactor + ((i + 1) / 5f) * (bottomFactor - topFactor)

                        val halfWTop = (topFrac * w) / 2f
                        val halfWBottom = (bottomFrac * w) / 2f

                        val topLeftX = (w / 2f) - halfWTop
                        val topRightX = (w / 2f) + halfWTop
                        val bottomLeftX = (w / 2f) - halfWBottom
                        val bottomRightX = (w / 2f) + halfWBottom

                        val path = Path().apply {
                            moveTo(topLeftX, yStart)
                            lineTo(topRightX, yStart)
                            lineTo(bottomRightX, yEnd)
                            lineTo(bottomLeftX, yEnd)
                            close()
                        }

                        val isSelected = levelId == selectedNivelId
                        val fillColor = if (isSelected) primaryColor else grayShades[i]

                        // Dibujar relleno
                        drawPath(path = path, color = fillColor)

                        // Dibujar separador blanco
                        drawPath(
                            path = path,
                            color = separador,
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // Texto dentro de la sección
                        val nivelData = niveles.first { it.id == levelId }
                        val textStyle = TextStyle(
                            color = if (isSelected) textoSeleccion else textoNivel,
                            fontSize = if (isSelected) 13.sp else 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )

                        val textLayoutResult = textMeasurer.measure(
                            text = nivelData.labelCorto,
                            style = textStyle
                        )

                        val textX = (w - textLayoutResult.size.width) / 2f
                        val textY = yStart + (sliceHeight - textLayoutResult.size.height) / 2f

                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(textX, textY)
                        )
                    }
                }

                // Tarjeta con detalle del nivel seleccionado
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, BorderGray)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = selectedNivel.nombre,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = selectedNivel.descripcion,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // Sección Hábitos
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Hábitos para tu día a día",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                habitos.forEach { habito ->
                    HabitoExpandableCard(
                        titulo = habito.titulo,
                        descripcion = habito.descripcion
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Nota gris fija al final
            Text(
                text = "Contenido informativo general. No reemplaza la orientación de un nutricionista ni de otro profesional de salud.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun HabitoExpandableCard(
    titulo: String,
    descripcion: String
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, BorderGray)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titulo,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Contraer" else "Expandir",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = descripcion,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
