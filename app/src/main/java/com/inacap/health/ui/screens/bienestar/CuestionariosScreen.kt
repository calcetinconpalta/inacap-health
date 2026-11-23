package com.inacap.health.ui.screens.bienestar

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inacap.health.HealthApp
import com.inacap.health.data.ResultadoCuestionario
import com.inacap.health.salud.Cuestionario
import com.inacap.health.salud.Cuestionarios
import com.inacap.health.salud.ResultadoEvaluacion
import com.inacap.health.ui.components.BarraSuperior
import com.inacap.health.ui.components.PasoCuestionario
import com.inacap.health.ui.components.Tarjeta
import com.inacap.health.ui.components.TextoSecundario
import kotlinx.coroutines.launch

/** Tarea 18f · Bienestar → Cuestionarios: lista, una pregunta por pantalla y resultado sin diagnóstico. */
@Composable
fun CuestionariosScreen(onBack: () -> Unit, onPedirAtencion: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HealthApp
    val scope = rememberCoroutineScope()
    var actual by remember { mutableStateOf<Cuestionario?>(null) }
    var paso by remember { mutableStateOf(0) }
    val respuestas = remember { mutableStateListOf<Int?>() }
    var resultado by remember { mutableStateOf<ResultadoEvaluacion?>(null) }
    var ayudaVista by remember { mutableStateOf(false) }

    fun empezar(c: Cuestionario) {
        actual = c; paso = 0; resultado = null; ayudaVista = false
        respuestas.clear(); repeat(c.preguntas.size) { respuestas.add(null) }
    }
    fun salir() { actual = null; resultado = null }

    fun terminar(c: Cuestionario) {
        val puntos = respuestas.map { it ?: 0 }
        val r = Cuestionarios.evaluar(c, puntos)
        resultado = r
        scope.launch {
            val fila = ResultadoCuestionario(tipo = c.clave, puntaje = r.puntaje, respuestas = puntos.joinToString(","), alerta = r.alerta)
            app.database.resultadoCuestionarioDao().insert(fila)
            app.sync.encolarCuestionario(fila)                        // a la nube solo el puntaje, si comparte datos
            app.sync.registrarEvento("cuestionario_completado", mapOf("tipo" to c.clave))
        }
    }

    BackHandler(enabled = actual != null) {
        if (resultado == null && paso > 0) paso-- else salir()
    }

    val c = actual
    val r = resultado
    Scaffold(topBar = { BarraSuperior(c?.nombre ?: "Cuestionarios", onBack = { if (c != null) salir() else onBack() }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when {
                // PHQ-9 pregunta 9 ≠ «Nunca»: ayuda inmediata, sin importar el puntaje. No se salta sin «Entiendo».
                r != null && r.ayudaUrgente && !ayudaVista -> AyudaInmediata(onPedirAtencion) { ayudaVista = true }
                c != null && r != null -> {
                    Tarjeta(titulo = r.nivel) {
                        Text(if (c.clave == "who5") "Puntaje: ${r.puntaje} de 100" else "Puntaje: ${r.puntaje}", fontWeight = FontWeight.Bold)
                        TextoSecundario(r.texto)
                        TextoSecundario(Cuestionarios.NO_DIAGNOSTICO)
                    }
                    r.ofrecer?.let { siguiente ->
                        Button(onClick = { empezar(Cuestionarios.por(siguiente)) }, modifier = Modifier.fillMaxWidth()) { Text("Responder el ${Cuestionarios.por(siguiente).nombre.substringBefore(" ·")}") }
                    }
                    if (r.alerta) Button(
                        onClick = onPedirAtencion, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Pedir atención a Bienestar") }
                    OutlinedButton(onClick = { salir() }, modifier = Modifier.fillMaxWidth()) { Text("Volver a los cuestionarios", color = MaterialTheme.colorScheme.onSurface) }
                    TextoSecundario("Tus respuestas se guardan solo en tu teléfono. Si compartes datos anonimizados, solo se envía el puntaje.")
                }
                c != null -> PasoCuestionario(
                    numero = paso + 1, total = c.preguntas.size,
                    pregunta = c.preguntas[paso],
                    opciones = c.opciones.map { it.first },
                    elegida = respuestas[paso]?.let { p -> c.opciones.indexOfFirst { it.second == p } },
                    onElegir = { i -> respuestas[paso] = c.opciones[i].second },
                    onSiguiente = { if (paso < c.preguntas.size - 1) paso++ else terminar(c) },
                    onAnterior = if (paso > 0) ({ paso-- }) else null,
                    encabezado = c.encabezado
                )
                else -> {
                    TextoSecundario("Cuestionarios breves para conocerte mejor. Una pregunta por pantalla. " + Cuestionarios.NO_DIAGNOSTICO)
                    Cuestionarios.TODOS.forEach { q ->
                        Tarjeta(modifier = if (q.disponible) Modifier.clickable { empezar(q) } else Modifier, titulo = q.nombre) {
                            TextoSecundario(q.descripcion)
                            if (q.disponible) TextoSecundario("${q.preguntas.size} preguntas · Toca para empezar")
                        }
                    }
                }
            }
        }
    }
}

/** Protocolo mínimo (PHQ-9 pregunta 9): líneas de ayuda con botón para llamar y pedir atención. */
@Composable
private fun AyudaInmediata(onPedirAtencion: () -> Unit, onEntiendo: () -> Unit) {
    val context = LocalContext.current
    fun llamar(numero: String) = runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(numero)))) }
    Tarjeta(titulo = "No estás solo/a") {
        TextoSecundario("Nos contaste que has tenido pensamientos de que estarías mejor muerto/a o de hacerte daño. Hablar con alguien ahora puede ayudar. Estas líneas son gratuitas y confidenciales:")
    }
    Button(onClick = { llamar("*4141") }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Text("Llamar a *4141 · Prevención del suicidio (24 h)", fontWeight = FontWeight.Bold)
    }
    OutlinedButton(onClick = { llamar("6003607777") }, modifier = Modifier.fillMaxWidth()) { Text("Salud Responde · 600 360 7777", color = MaterialTheme.colorScheme.onSurface) }
    OutlinedButton(onClick = { llamar("131") }, modifier = Modifier.fillMaxWidth()) { Text("Emergencias · 131", color = MaterialTheme.colorScheme.onSurface) }
    OutlinedButton(onClick = onPedirAtencion, modifier = Modifier.fillMaxWidth()) { Text("Pedir atención a Bienestar", color = MaterialTheme.colorScheme.onSurface) }
    Button(onClick = onEntiendo, modifier = Modifier.fillMaxWidth()) { Text("Entiendo") }
}
