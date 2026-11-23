package com.inacap.health.ui.screens.bienestar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inacap.health.salud.Antecedentes
import com.inacap.health.ui.components.BarraSuperior
import com.inacap.health.ui.components.Tarjeta
import com.inacap.health.ui.components.TextoSecundario
import com.inacap.health.ui.screens.perfil.PerfilViewModel

/**
 * Tarea 18d · Antes de la primera consulta (orientación o apoyo): «Necesitamos saber más de ti…».
 * Sin aceptar compartir los antecedentes no se puede consultar, pero se explica por qué.
 * Si ya aceptó, muestra [contenido] directo.
 */
@Composable
fun PuertaAntecedentes(
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
    contenido: @Composable () -> Unit
) {
    val context = LocalContext.current
    var aceptado by remember { mutableStateOf(Antecedentes.aceptado(context)) }
    if (aceptado) { contenido(); return }

    val vm: PerfilViewModel = viewModel()
    val perfil by vm.perfil.collectAsState()
    var patologias by rememberSaveable { mutableStateOf("") }
    var alergias by rememberSaveable { mutableStateOf("") }
    var medicamentos by rememberSaveable { mutableStateOf("") }
    var cargado by rememberSaveable { mutableStateOf(false) }
    var enviando by remember { mutableStateOf(false) }
    LaunchedEffect(perfil) {
        val p = perfil
        if (!cargado && p != null) {
            patologias = p.patologias; alergias = p.alergias; medicamentos = p.medicamentos; cargado = true
        }
    }

    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Tarjeta(titulo = Antecedentes.TITULO) { TextoSecundario(Antecedentes.POR_QUE) }
        TextoSecundario("Revisa lo que tienes en Mis datos. Si no tienes, escribe «Ninguna».")
        OutlinedTextField(patologias, { patologias = it.take(300) }, label = { Text("Patologías (ej.: hipertensión, asma)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(alergias, { alergias = it.take(300) }, label = { Text("Alergias (ej.: paracetamol)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(medicamentos, { medicamentos = it.take(300) }, label = { Text("Medicamentos que tomas") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        val completo = listOf(patologias, alergias, medicamentos).all { it.isNotBlank() }
        Button(
            onClick = {
                enviando = true
                vm.aceptarAntecedentes(patologias, alergias, medicamentos) { enviando = false; aceptado = true }
            },
            enabled = completo && !enviando,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (enviando) "Guardando…" else "Aceptar y compartir con Bienestar", fontWeight = FontWeight.Bold) }
        if (!completo) TextoSecundario("Completa los tres campos (puede ser «Ninguna»).")
        TextoSecundario(Antecedentes.SIN_ACEPTAR)
        extra()
    }
}

/** Versión de pantalla completa (con barra superior), para Apoyo de Bienestar. */
@Composable
fun PuertaAntecedentesPantalla(titulo: String, onBack: () -> Unit, onVerMensajes: (() -> Unit)? = null, contenido: @Composable () -> Unit) {
    val context = LocalContext.current
    if (Antecedentes.aceptado(context)) { contenido(); return }
    var listo by remember { mutableStateOf(false) }
    if (listo) { contenido(); return }
    Scaffold(topBar = { BarraSuperior(titulo, onBack) }) { padding ->
        PuertaAntecedentes(
            modifier = Modifier.padding(padding),
            extra = { onVerMensajes?.let { TextButton(onClick = it) { Text("Ver mensajes del equipo de Bienestar") } } }
        ) {
            LaunchedEffect(Unit) { listo = true }
        }
    }
}
