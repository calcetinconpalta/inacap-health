package com.inacap.health.ui.screens.salud

import androidx.core.content.edit
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inacap.health.HealthApp
import com.inacap.health.data.RegistroBiometrico
import com.inacap.health.data.sync.Anonimizacion
import com.inacap.health.salud.CalculoPasos
import com.inacap.health.util.flujoInicioDelDia
import com.inacap.health.util.inicioDelDia
import com.inacap.health.util.observar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SaludViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HealthApp
    private val dao = app.database.registroBiometricoDao()
    private val sync = app.sync
    private val sharedPrefs = application.getSharedPreferences("health_prefs", Context.MODE_PRIVATE)

    // Se observa el archivo de preferencias: si se borran los datos, la meta vuelve a 2000
    // (antes quedaba la meta anterior en memoria).
    val metaAguaMl: StateFlow<Int> = sharedPrefs.observar { it.getInt("meta_agua_ml", 2000) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, sharedPrefs.getInt("meta_agua_ml", 2000))

    // El inicio del día se recalcula al pasar la medianoche (antes quedaba fijo).
    val aguaHoyMl: StateFlow<Int> = flujoInicioDelDia()
        .flatMapLatest { dao.getAguaSumFrom(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val ultimoRegistro: StateFlow<RegistroBiometrico?> = dao.getUltimoRegistroConPeso()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // HU-10 · Pasos del día (se actualiza solo al cambiar de día) y meta, sin calorías.
    val pasosHoy: StateFlow<Int> = flujoInicioDelDia()
        .flatMapLatest { inicio -> app.database.pasosDiaDao().observar(CalculoPasos.fecha(inicio)) }
        .map { it?.pasos ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _metaPasos = MutableStateFlow(app.pasos.meta)
    val metaPasos: StateFlow<Int> = _metaPasos.asStateFlow()

    val tieneSensorPasos: Boolean get() = app.pasos.tieneSensor
    val tienePermisoPasos: Boolean get() = app.pasos.tienePermiso

    fun cambiarMetaPasos(meta: Int) {
        app.pasos.meta = meta
        _metaPasos.value = meta
    }

    /** Lee el sensor ahora y deja programada la lectura cada 15 minutos. */
    fun actualizarPasos() {
        viewModelScope.launch {
            app.pasos.muestrear()
            app.pasos.programarMuestreo()
            // Si el reloj está vinculado, trae los últimos 2 días (solo con la app abierta).
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                com.inacap.health.salud.HealthConnectDisponibilidad.vinculado(app)
            ) {
                runCatching { com.inacap.health.salud.HealthConnectRepositorio(app).importar(dias = 2) }
            }
        }
    }

    // ---------- Tarea 18g: ánimo del día (solo en el teléfono) ----------
    private val animoDao = app.database.estadoAnimoDao()

    val animoHoy: StateFlow<Int?> = flujoInicioDelDia()
        .flatMapLatest { inicio -> animoDao.observar(CalculoPasos.fecha(inicio)) }
        .map { it?.nivel }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** «Mal» o «Muy mal» 3 días seguidos (hasta hoy) → sugerir apoyo con suavidad. */
    val sugerirApoyoAnimo: StateFlow<Boolean> = animoDao.observarTodos()
        .map { com.inacap.health.salud.Animo.sugerirApoyo(it, java.time.LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun guardarAnimo(nivel: Int) {
        viewModelScope.launch {
            animoDao.guardar(com.inacap.health.data.EstadoAnimo(fecha = CalculoPasos.fecha(), nivel = nivel.coerceIn(1, 5)))
        }
    }

    /** Escucha el sensor mientras quien llama siga activo (Inicio a la vista). */
    suspend fun pasosEnVivo() = app.pasos.escucharEnVivo()

    private val hcPrefs = application.getSharedPreferences("hc_prefs", Context.MODE_PRIVATE)

    /** Último pulso y pasos de hoy que llegaron del reloj (null si no hay reloj vinculado). */
    val reloj: StateFlow<com.inacap.health.salud.DatosReloj?> = flujoInicioDelDia()
        .flatMapLatest { inicio -> hcPrefs.observar { com.inacap.health.salud.RelojResumen.leer(it, CalculoPasos.fecha(inicio)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Trae el reloj solo, sin tocar "Sincronizar ahora": cada [RelojResumen.CADA_MS] mientras
     * quien llama siga activo (Inicio a la vista). La primera vez ya la hace [actualizarPasos].
     */
    suspend fun sincronizarRelojPeriodico() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        while (true) {
            kotlinx.coroutines.delay(com.inacap.health.salud.RelojResumen.CADA_MS)
            if (com.inacap.health.salud.HealthConnectDisponibilidad.vinculado(app)) {
                runCatching { com.inacap.health.salud.HealthConnectRepositorio(app).importar(dias = 2) }
            }
        }
    }

    val nivelActividadGuardado: String
        get() = sharedPrefs.getString("nivel_actividad", "Moderado") ?: "Moderado"

    fun agregarAgua() {
        viewModelScope.launch {
            val registro = RegistroBiometrico(
                pesoKg = null,
                estaturaCm = null,
                imc = null,
                aguaMl = 250,
                frecuenciaCardiaca = null,
                createdAt = System.currentTimeMillis()
            )
            dao.insert(registro)
            sync.encolarRegistro(registro)
            sync.registrarEvento("agua_registrada", mapOf("ml" to 250))
        }
    }

    fun quitarAgua() {
        viewModelScope.launch {
            val ultimo = dao.getUltimoAguaDelDia(inicioDelDia()) ?: return@launch
            dao.deleteById(ultimo.id)
            sync.encolarBorradoRegistro(ultimo.uuid)
            sync.registrarEvento("agua_quitada", mapOf("ml" to ultimo.aguaMl))
        }
    }

    fun guardarCalculo(peso: Double, estatura: Double, nivel: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val imc = SaludLogic.calcularImc(peso, estatura)
            val nuevaMeta = SaludLogic.metaAguaMl(peso, nivel)

            val registro = RegistroBiometrico(
                pesoKg = peso,
                estaturaCm = estatura,
                imc = imc,
                aguaMl = 0,
                frecuenciaCardiaca = null,
                createdAt = System.currentTimeMillis()
            )
            dao.insert(registro)

            sharedPrefs.edit {
                putString("nivel_actividad", nivel)
                putInt("meta_agua_ml", nuevaMeta)
            }

            // Nube: solo IMC y su rango (nunca peso ni estatura).
            sync.encolarRegistro(registro, nivel)
            sync.registrarEvento(
                "imc_calculado",
                mapOf("categoria" to Anonimizacion.categoriaImc(imc), "nivel_actividad" to nivel.lowercase())
            )
            onComplete()
        }
    }
}
