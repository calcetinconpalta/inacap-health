package com.inacap.health.ui.screens.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PerfilDatosScreen(
    viewModel: PerfilViewModel,
    onBack: () -> Unit
) {
    val perfil by viewModel.perfil.collectAsState()

    var nombre by rememberSaveable { mutableStateOf("") }
    var edadStr by rememberSaveable { mutableStateOf("") }
    var sexo by rememberSaveable { mutableStateOf("") }
    var patologias by rememberSaveable { mutableStateOf("") }
    var alergias by rememberSaveable { mutableStateOf("") }
    var medicamentos by rememberSaveable { mutableStateOf("") }
    var isLoaded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(perfil) {
        if (!isLoaded && perfil != null) {
            nombre = perfil?.nombre ?: ""
            edadStr = perfil?.edad?.toString() ?: ""
            sexo = perfil?.sexo ?: ""
            patologias = perfil?.patologias ?: ""
            alergias = perfil?.alergias ?: ""
            medicamentos = perfil?.medicamentos ?: ""
            isLoaded = true
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val opcionesSexo = listOf("Femenino", "Masculino", "Otro", "Prefiero no decir")

    // Validaciones
    val nombreError = if (nombre.length > 60) "Máximo 60 caracteres" else null
    val edadInt = edadStr.filter { it.isDigit() }.toIntOrNull()
    val edadError = if (edadStr.isNotEmpty() && (edadInt == null || edadInt !in 14..100)) {
        "La edad debe estar entre 14 y 100 años"
    } else null
    val patologiasError = if (patologias.length > 300) "Máximo 300 caracteres" else null
    val alergiasError = if (alergias.length > 300) "Máximo 300 caracteres" else null
    val medicamentosError = if (medicamentos.length > 300) "Máximo 300 caracteres" else null

    val isFormValid = nombreError == null && edadError == null &&
            patologiasError == null && alergiasError == null && medicamentosError == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mis datos",
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Nombre
            Column {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { input ->
                        nombre = input.replace("\n", "").replace("\r", "")
                    },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                if (nombreError != null) {
                    Text(
                        text = nombreError,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
            }

            // Edad
            Column {
                OutlinedTextField(
                    value = edadStr,
                    onValueChange = { input ->
                        edadStr = input.filter { it.isDigit() }
                    },
                    label = { Text("Edad") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                if (edadError != null) {
                    Text(
                        text = edadError,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
            }

            // Sexo
            Column {
                Text(
                    text = "Sexo",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    opcionesSexo.forEach { opcion ->
                        FilterChip(
                            selected = sexo == opcion,
                            onClick = { sexo = if (sexo == opcion) "" else opcion },
                            label = { Text(opcion) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            // Patologías
            Column {
                OutlinedTextField(
                    value = patologias,
                    onValueChange = { patologias = it },
                    label = { Text("Patologías") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                if (patologiasError != null) {
                    Text(
                        text = patologiasError,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
            }

            // Alergias
            Column {
                OutlinedTextField(
                    value = alergias,
                    onValueChange = { alergias = it },
                    label = { Text("Alergias") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                if (alergiasError != null) {
                    Text(
                        text = alergiasError,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
            }

            // Medicamentos
            Column {
                OutlinedTextField(
                    value = medicamentos,
                    onValueChange = { medicamentos = it },
                    label = { Text("Medicamentos") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                if (medicamentosError != null) {
                    Text(
                        text = medicamentosError,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (isFormValid) {
                        val sanitizedNombre = nombre.replace("\n", "").replace("\r", "").trim().take(60)
                        val sanitizedEdad = edadStr.filter { it.isDigit() }.toIntOrNull()
                        val sanitizedPatologias = patologias.trim().take(300)
                        val sanitizedAlergias = alergias.trim().take(300)
                        val sanitizedMedicamentos = medicamentos.trim().take(300)

                        viewModel.guardarPerfil(
                            nombre = sanitizedNombre,
                            edad = sanitizedEdad,
                            sexo = sexo,
                            patologias = sanitizedPatologias,
                            alergias = sanitizedAlergias,
                            medicamentos = sanitizedMedicamentos,
                            onComplete = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Datos guardados")
                                }
                            }
                        )
                    }
                },
                enabled = isFormValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Guardar",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Esta información solo se usa para personalizar tus recomendaciones.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
