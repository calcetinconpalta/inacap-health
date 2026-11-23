package com.inacap.health.ui.screens.salud

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.inacap.health.HealthApp
import com.inacap.health.salud.DatosResumen
import com.inacap.health.salud.EnvioBienestar
import com.inacap.health.salud.ResumenPdf
import com.inacap.health.ui.components.BarraSuperior
import com.inacap.health.ui.components.Tarjeta
import com.inacap.health.ui.components.TextoSecundario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** HU-12 · Exportar un resumen en PDF para llevar a una consulta. */
@Composable
fun ResumenPdfScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HealthApp
    val scope = rememberCoroutineScope()
    var dias by remember { mutableIntStateOf(90) }
    var incluirNombre by remember { mutableStateOf(false) }
    var incluirScoff by remember { mutableStateOf(false) }
    var archivo by remember { mutableStateOf<File?>(null) }
    var generando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun uri(f: File) = FileProvider.getUriForFile(context, "${context.packageName}.archivos", f)

    fun generar() {
        generando = true; error = null
        scope.launch {
            try {
                archivo = withContext(Dispatchers.IO) {
                    val db = app.database
                    val datos = DatosResumen.construir(
                        perfil = db.perfilDao().getPerfilUnaVez(),
                        registros = db.registroBiometricoDao().getAllUnaVez(),
                        pasos = db.pasosDiaDao().getAllUnaVez(),
                        scoff = db.resultadoScoffDao().getAllUnaVez(),
                        dias = dias, incluirNombre = incluirNombre, incluirScoff = incluirScoff
                    )
                    ResumenPdf.generar(context, datos)
                }
                app.sync.registrarEvento("pdf_generado", mapOf("dias" to dias))
            } catch (e: Exception) {
                error = "No se pudo generar el PDF (${e.javaClass.simpleName})."
            } finally {
                generando = false
            }
        }
    }

    Scaffold(topBar = { BarraSuperior("Resumen para tu profesional", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextoSecundario("Genera un PDF con tu IMC, hidratación, pasos y pulso por fecha para mostrarlo en una consulta. Se crea solo cuando lo pides y queda en tu teléfono hasta que decidas compartirlo.")
            Tarjeta(titulo = "Período") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30 to "30 días", 90 to "3 meses", 365 to "1 año").forEach { (d, t) ->
                        FilterChip(
                            selected = dias == d, onClick = { dias = d; archivo = null }, label = { Text(t) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
            Tarjeta(titulo = "Qué incluir") {
                Opcion("Mi nombre", incluirNombre) { incluirNombre = it; archivo = null }
                Opcion("Resultados del cuestionario SCOFF", incluirScoff) { incluirScoff = it; archivo = null }
                TextoSecundario("Patologías, alergias y medicamentos nunca se incluyen.")
            }
            Button(
                onClick = { generar() }, enabled = !generando,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text(if (generando) "Generando…" else "Generar PDF", fontWeight = FontWeight.Bold) }
            error?.let { TextoSecundario(it) }
            archivo?.let { f ->
                Tarjeta(titulo = "PDF listo") {
                    TextoSecundario(f.name)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val ver = Intent(Intent.ACTION_VIEW).setDataAndType(uri(f), "application/pdf")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            try { context.startActivity(ver) } catch (_: ActivityNotFoundException) { error = "No hay una app para abrir PDF." }
                        }) { Text("Abrir", color = MaterialTheme.colorScheme.onSurface) }
                        Button(
                            onClick = {
                                val enviar = Intent(Intent.ACTION_SEND).setType("application/pdf")
                                    .putExtra(Intent.EXTRA_STREAM, uri(f))
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(enviar, "Compartir resumen"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("Compartir") }
                    }
                }
                EnviarABienestar(f, uri = { uri(it) }, onError = { error = it })
            }
        }
    }
}

/** Botón "Enviar a Bienestar": destinatario fijo, app de correo del propio estudiante y límite de envíos. */
@Composable
private fun EnviarABienestar(f: File, uri: (File) -> android.net.Uri, onError: (String) -> Unit) {
    val context = LocalContext.current
    var restantes by remember { mutableIntStateOf(EnvioBienestar.restantes(EnvioBienestar.historial(context), System.currentTimeMillis())) }
    Tarjeta(titulo = "Enviar a Bienestar") {
        if (!EnvioBienestar.configurado) {
            TextoSecundario("El envío a Bienestar aún no está disponible. Mientras tanto, usa «Compartir».")
            return@Tarjeta
        }
        TextoSecundario("Se abrirá tu app de correo con el PDF adjunto y la dirección de Bienestar ya puesta. El correo sale desde tu cuenta; revísalo antes de enviarlo.")
        Button(
            onClick = {
                try {
                    context.startActivity(EnvioBienestar.intent(uri(f)))
                    EnvioBienestar.registrar(context)
                    restantes = EnvioBienestar.restantes(EnvioBienestar.historial(context), System.currentTimeMillis())
                } catch (_: ActivityNotFoundException) { onError("No hay una app de correo en este teléfono. Usa «Compartir».") }
            },
            enabled = restantes > 0,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Enviar a Bienestar") }
        TextoSecundario(
            if (restantes > 0) "Puedes enviar $restantes más por ahora (máximo ${EnvioBienestar.MAX_POR_DIA} al día y ${EnvioBienestar.MAX_POR_MES} al mes)."
            else "Llegaste al máximo de envíos por ahora. Si es urgente, pide atención en Apoyo de Bienestar."
        )
    }
}

@Composable
private fun Opcion(texto: String, valor: Boolean, onCambio: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onCambio(!valor) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(valor, onCambio, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
        Text(texto)
    }
}
