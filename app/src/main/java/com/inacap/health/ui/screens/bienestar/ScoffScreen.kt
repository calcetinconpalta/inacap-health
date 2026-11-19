package com.inacap.health.ui.screens.bienestar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inacap.health.ui.theme.BorderGray

val PREGUNTAS_SCOFF = listOf(
    "¿Te provocas el vómito porque te sientes incómodamente lleno/a?",
    "¿Te preocupa haber perdido el control sobre la cantidad de comida que ingieres?",
    "¿Has perdido recientemente más de 6 kg en un período de 3 meses?",
    "¿Crees que tienes sobrepeso aunque otras personas digan que estás muy delgado/a?",
    "¿Dirías que la comida domina tu vida?"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoffScreen(
    viewModel: ScoffViewModel = viewModel(),
    onNavigateToDerivacion: () -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Test SCOFF") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.mostrandoResultado || uiState.preguntaActual == 0) {
                                onBack()
                            } else {
                                viewModel.anterior()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!uiState.mostrandoResultado) {
                // Header breve
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = "Este cuestionario es privado: tus respuestas se guardan solo en tu teléfono. No es un diagnóstico, es una guía para tu autocuidado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                // Indicador y Barra de progreso
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pregunta ${uiState.preguntaActual + 1} de 5",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (uiState.preguntaActual + 1) / 5f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = BorderGray
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Texto de la pregunta
                Text(
                    text = PREGUNTAS_SCOFF[uiState.preguntaActual],
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Botones grandes Sí / No
                val respuestaActual = uiState.respuestas[uiState.preguntaActual]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Botón Sí
                    val siSeleccionado = respuestaActual == true
                    if (siSeleccionado) {
                        Button(
                            onClick = { viewModel.responder(true) },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Sí", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.responder(true) },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            border = BorderStroke(1.dp, BorderGray)
                        ) {
                            Text("Sí", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }

                    // Botón No
                    val noSeleccionado = respuestaActual == false
                    if (noSeleccionado) {
                        Button(
                            onClick = { viewModel.responder(false) },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("No", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.responder(false) },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            border = BorderStroke(1.dp, BorderGray)
                        ) {
                            Text("No", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f, fill = false))

                // Controles Atrás y Siguiente / Ver resultado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (uiState.preguntaActual > 0) {
                                viewModel.anterior()
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        border = BorderStroke(1.dp, BorderGray)
                    ) {
                        Text("Atrás", color = MaterialTheme.colorScheme.onBackground)
                    }

                    Button(
                        onClick = { viewModel.siguiente() },
                        enabled = respuestaActual != null,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(if (uiState.preguntaActual == 4) "Ver resultado" else "Siguiente")
                    }
                }
            } else {
                // Pantalla de Resultado
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (uiState.puntaje < 2) {
                            Text(
                                text = "Gracias por responder",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tus respuestas no sugieren necesidad de apoyo adicional por ahora. Si algo te preocupa, siempre puedes conversar con un profesional de salud.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Tu bienestar es prioridad",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Algunas de tus respuestas indican que podría ser útil conversar con un profesional. Esto no es un diagnóstico, solo una orientación. Contar con apoyo es un paso valiente y puedes hacerlo de forma privada.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onNavigateToDerivacion,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Conocer opciones de apoyo")
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    border = BorderStroke(1.dp, BorderGray)
                ) {
                    Text("Volver", color = MaterialTheme.colorScheme.onBackground)
                }

                Spacer(modifier = Modifier.weight(1f, fill = false))

                Text(
                    text = "INACAP Health es un primer filtro informativo y no reemplaza la atención profesional.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
