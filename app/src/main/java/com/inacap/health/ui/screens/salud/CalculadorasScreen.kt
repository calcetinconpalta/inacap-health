package com.inacap.health.ui.screens.salud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculadorasScreen(
    saludViewModel: SaludViewModel,
    onBack: () -> Unit
) {
    val ultimoRegistro by saludViewModel.ultimoRegistro.collectAsState()

    var pesoText by remember { mutableStateOf("") }
    var estaturaText by remember { mutableStateOf("") }
    var nivelActividad by remember { mutableStateOf(saludViewModel.nivelActividadGuardado) }
    var errorPeso by remember { mutableStateOf<String?>(null) }
    var errorEstatura by remember { mutableStateOf<String?>(null) }
    var precargado by remember { mutableStateOf(false) }

    LaunchedEffect(ultimoRegistro) {
        if (!precargado) {
            ultimoRegistro?.let { reg ->
                if (reg.pesoKg != null && pesoText.isEmpty()) {
                    pesoText = if (reg.pesoKg % 1.0 == 0.0) {
                        reg.pesoKg.toInt().toString()
                    } else {
                        reg.pesoKg.toString().replace('.', ',')
                    }
                }
                if (reg.estaturaCm != null && estaturaText.isEmpty()) {
                    estaturaText = if (reg.estaturaCm % 1.0 == 0.0) {
                        reg.estaturaCm.toInt().toString()
                    } else {
                        reg.estaturaCm.toString().replace('.', ',')
                    }
                }
            }
            nivelActividad = saludViewModel.nivelActividadGuardado
            precargado = true
        }
    }

    val niveles = listOf("Bajo", "Moderado", "Alto")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calculadoras IMC e hidratación") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            Text(
                text = "Ingresa tus datos para calcular tu IMC y estimar tu meta de agua diaria sugerida.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Campo Peso
            OutlinedTextField(
                value = pesoText,
                onValueChange = {
                    pesoText = it
                    errorPeso = null
                },
                label = { Text("Peso (kg)") },
                placeholder = { Text("Ej. 70.5") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = errorPeso != null
            )
            errorPeso?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Campo Estatura
            OutlinedTextField(
                value = estaturaText,
                onValueChange = {
                    estaturaText = it
                    errorEstatura = null
                },
                label = { Text("Estatura (cm)") },
                placeholder = { Text("Ej. 170") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = errorEstatura != null
            )
            errorEstatura?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Selector Nivel de Actividad
            Text(
                text = "Nivel de actividad física",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                niveles.forEach { nivel ->
                    FilterChip(
                        selected = nivelActividad == nivel,
                        onClick = { nivelActividad = nivel },
                        label = { Text(nivel) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            // Botón Calcular y guardar
            Button(
                onClick = {
                    val pesoClean = pesoText.replace(',', '.').trim()
                    val estaturaClean = estaturaText.replace(',', '.').trim()

                    val peso = pesoClean.toDoubleOrNull()
                    val estatura = estaturaClean.toDoubleOrNull()

                    var valid = true
                    if (peso == null || peso < 20.0 || peso > 300.0) {
                        errorPeso = "El peso debe estar entre 20 y 300 kg."
                        valid = false
                    }
                    if (estatura == null || estatura < 100.0 || estatura > 230.0) {
                        errorEstatura = "La estatura debe estar entre 100 y 230 cm."
                        valid = false
                    }

                    if (valid && peso != null && estatura != null) {
                        saludViewModel.guardarCalculo(peso, estatura, nivelActividad)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Calcular y guardar")
            }

            // Tarjeta de resultado
            ultimoRegistro?.let { reg ->
                if (reg.pesoKg != null && reg.estaturaCm != null && reg.imc != null) {
                    val imcVal = reg.imc
                    val categoria = SaludLogic.categoriaImc(imcVal)
                    val mensaje = SaludLogic.mensajeImc(imcVal)
                    val metaAgua = SaludLogic.metaAguaMl(reg.pesoKg, nivelActividad)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Resultado",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "IMC: ${String.format(Locale.US, "%.1f", imcVal)}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = categoria,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = mensaje,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Meta de agua sugerida: $metaAgua ml al día",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Intenta levantarte y moverte unos minutos cada hora de estudio",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))

            // Nota gris fija al pie
            Text(
                text = "Este indicador es un primer filtro informativo. No es un diagnóstico ni reemplaza a un profesional de salud.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
