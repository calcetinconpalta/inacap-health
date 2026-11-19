package com.inacap.health.ui.screens.salud

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inacap.health.HealthApp
import com.inacap.health.data.RegistroBiometrico
import com.inacap.health.salud.Fotopletismografia
import com.inacap.health.salud.Fotopletismografia.Calidad
import com.inacap.health.ui.components.BarraSuperior
import com.inacap.health.ui.components.Tarjeta
import com.inacap.health.ui.components.TextoSecundario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

sealed interface EstadoPulso {
    object Inicio : EstadoPulso
    data class Midiendo(val progreso: Float, val dedo: Boolean) : EstadoPulso
    data class Listo(val resultado: Fotopletismografia.Resultado, val guardado: Boolean = false) : EstadoPulso
    data class Problema(val mensaje: String) : EstadoPulso
}

class PulsoViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HealthApp
    private val _estado = MutableStateFlow<EstadoPulso>(EstadoPulso.Inicio)
    val estado: StateFlow<EstadoPulso> = _estado.asStateFlow()
    private val _onda = MutableStateFlow<List<Double>>(emptyList())
    val onda: StateFlow<List<Double>> = _onda.asStateFlow()

    private val tiempos = ArrayList<Long>()
    private val valores = ArrayList<Double>()
    private var inicio = 0L
    private var sinDedoDesde = 0L

    fun comenzar() {
        synchronized(this) { tiempos.clear(); valores.clear(); inicio = 0L; sinDedoDesde = 0L }
        _onda.value = emptyList()
        _estado.value = EstadoPulso.Midiendo(0f, false)
    }

    fun problema(mensaje: String) { _estado.value = EstadoPulso.Problema(mensaje) }

    /** Llamado desde el analizador de la cámara (hilo de fondo). */
    fun muestra(tMs: Long, rojo: Double, verde: Double) = synchronized(this) {
        if (_estado.value !is EstadoPulso.Midiendo) return
        val dedo = Fotopletismografia.dedoDetectado(rojo, verde)
        if (!dedo) {
            if (sinDedoDesde == 0L) sinDedoDesde = tMs
            // Si el dedo se sale más de 1 s, la medición vuelve a empezar.
            if (tMs - sinDedoDesde > 1000) { tiempos.clear(); valores.clear(); inicio = 0L }
            _estado.value = EstadoPulso.Midiendo(0f, false)
            return
        }
        sinDedoDesde = 0L
        if (inicio == 0L) inicio = tMs
        tiempos += tMs
        valores += rojo
        _onda.value = valores.takeLast(150)
        val progreso = ((tMs - inicio).toFloat() / Fotopletismografia.DURACION_MS).coerceAtMost(1f)
        if (progreso >= 1f) {
            _estado.value = EstadoPulso.Listo(Fotopletismografia.estimar(tiempos.toLongArray(), valores.toDoubleArray()))
        } else {
            _estado.value = EstadoPulso.Midiendo(progreso, true)
        }
    }

    fun guardar() {
        val listo = _estado.value as? EstadoPulso.Listo ?: return
        val bpm = listo.resultado.bpm ?: return
        viewModelScope.launch {
            val registro = RegistroBiometrico(
                pesoKg = null, estaturaCm = null, imc = null, aguaMl = 0,
                frecuenciaCardiaca = bpm, fuente = "camara"
            )
            app.database.registroBiometricoDao().insert(registro)
            app.sync.encolarRegistro(registro)
            app.sync.registrarEvento("pulso_medido", mapOf("calidad" to listo.resultado.calidad.name.lowercase()))
            _estado.value = listo.copy(guardado = true)
        }
    }
}

/** Promedios Y/U/V del centro del cuadro (submuestreado cada 8 píxeles). */
private fun mediasYuv(img: ImageProxy): Triple<Double, Double, Double> {
    val (py, pu, pv) = img.planes
    val w = img.width
    val h = img.height
    var sy = 0L; var su = 0L; var sv = 0L; var c = 0
    var fila = h / 4
    while (fila < 3 * h / 4) {
        var col = w / 4
        while (col < 3 * w / 4) {
            sy += py.buffer.get(fila * py.rowStride + col * py.pixelStride).toInt() and 0xFF
            su += pu.buffer.get((fila / 2) * pu.rowStride + (col / 2) * pu.pixelStride).toInt() and 0xFF
            sv += pv.buffer.get((fila / 2) * pv.rowStride + (col / 2) * pv.pixelStride).toInt() and 0xFF
            c++
            col += 8
        }
        fila += 8
    }
    return Triple(sy.toDouble() / c, su.toDouble() / c, sv.toDouble() / c)
}

/** HU-05 · Pulso con la cámara y la linterna. */
@Composable
fun PulsoScreen(onBack: () -> Unit, vm: PulsoViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val estado by vm.estado.collectAsState()
    val onda by vm.onda.collectAsState()
    var permisoDenegado by remember { mutableStateOf(false) }

    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permisoDenegado = !ok
        if (ok) vm.comenzar()
    }

    val midiendo = estado is EstadoPulso.Midiendo
    // La cámara solo está encendida mientras se mide.
    DisposableEffect(midiendo) {
        if (!midiendo) return@DisposableEffect onDispose { }
        val ejecutor = Executors.newSingleThreadExecutor()
        val futuro = ProcessCameraProvider.getInstance(context)
        var camara: Camera? = null
        futuro.addListener({
            try {
                val proveedor = futuro.get()
                val analisis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { a ->
                        a.setAnalyzer(ejecutor) { img ->
                            try {
                                val (y, u, v) = mediasYuv(img)
                                val (r, g) = Fotopletismografia.rojoYVerde(y, u, v)
                                vm.muestra(img.imageInfo.timestamp / 1_000_000, r, g)
                            } finally {
                                img.close()
                            }
                        }
                    }
                proveedor.unbindAll()
                camara = proveedor.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, analisis)
                if (camara?.cameraInfo?.hasFlashUnit() == true) camara?.cameraControl?.enableTorch(true)
                else vm.problema("Tu teléfono no tiene linterna en la cámara trasera, que es necesaria para esta medición.")
            } catch (e: Exception) {
                vm.problema("No se pudo usar la cámara (${e.javaClass.simpleName}).")
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            camara?.cameraControl?.enableTorch(false)
            if (futuro.isDone) runCatching { futuro.get().unbindAll() }
            ejecutor.shutdown()
        }
    }

    fun iniciar() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) vm.comenzar()
        else pedirPermiso.launch(Manifest.permission.CAMERA)
    }

    Scaffold(topBar = { BarraSuperior("Pulso con cámara", onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val e = estado) {
                EstadoPulso.Inicio -> {
                    Tarjeta(titulo = "Cómo medir") {
                        TextoSecundario("1. Siéntate y descansa un minuto.")
                        TextoSecundario("2. Cubre por completo la cámara trasera y la linterna con la yema del dedo índice, sin apretar.")
                        TextoSecundario("3. Quédate quieto durante 20 segundos.")
                    }
                    TextoSecundario("La imagen se procesa en tu teléfono y nunca se guarda ni se envía. El resultado es una estimación aproximada, no un diagnóstico.")
                    if (permisoDenegado) TextoSecundario("Sin permiso de cámara no podemos medir. Puedes activarlo en los ajustes del teléfono.")
                    BotonPrincipal("Comenzar medición") { iniciar() }
                }
                is EstadoPulso.Midiendo -> {
                    Text(
                        if (e.dedo) "Midiendo… no muevas el dedo" else "Cubre la cámara y la linterna con el dedo",
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                    )
                    LinearProgressIndicator(
                        progress = { e.progreso },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                    Onda(onda)
                    OutlinedButton(onClick = { vm.problema("Medición cancelada.") }) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                is EstadoPulso.Listo -> {
                    val r = e.resultado
                    if (r.bpm != null) {
                        Tarjeta {
                            Text("≈ ${r.bpm} lpm", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            TextoSecundario(
                                "Estimación aproximada · calidad de la señal: " +
                                    if (r.calidad == Calidad.BUENA) "buena" else "regular"
                            )
                            TextoSecundario("En reposo, entre 60 y 100 latidos por minuto es lo habitual en adultos. Si tienes dudas sobre tu ritmo cardíaco, conversa con un profesional de salud.")
                        }
                        if (e.guardado) TextoSecundario("Guardado en tu historial.")
                        else BotonPrincipal("Guardar en mi historial") { vm.guardar() }
                    } else {
                        Tarjeta(titulo = "No pudimos obtener una lectura clara") {
                            TextoSecundario("Prueba a cubrir bien la lente sin presionar fuerte, en un lugar con luz y quedándote muy quieto.")
                        }
                    }
                    OutlinedButton(onClick = { iniciar() }) { Text("Medir de nuevo", color = MaterialTheme.colorScheme.onSurface) }
                }
                is EstadoPulso.Problema -> {
                    Tarjeta { TextoSecundario(e.mensaje) }
                    BotonPrincipal("Intentar de nuevo") { iniciar() }
                }
            }
        }
    }
}

@Composable
private fun BotonPrincipal(texto: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) { Text(texto, fontWeight = FontWeight.Bold) }
}

/** Señal en vivo (para que la persona vea que la medición funciona). */
@Composable
private fun Onda(valores: List<Double>) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        if (valores.size < 2) return@Canvas
        val min = valores.min()
        val rango = (valores.max() - min).takeIf { it > 1e-6 } ?: 1.0
        val path = Path()
        valores.forEachIndexed { i, v ->
            val p = Offset(size.width * i / (valores.size - 1), size.height * (1 - ((v - min) / rango).toFloat()))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}
