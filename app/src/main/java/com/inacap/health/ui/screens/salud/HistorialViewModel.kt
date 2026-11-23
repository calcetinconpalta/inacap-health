package com.inacap.health.ui.screens.salud

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inacap.health.HealthApp
import com.inacap.health.data.RegistroBiometrico
import com.inacap.health.data.ResultadoScoff
import com.inacap.health.data.PasosDia
import com.inacap.health.salud.CalculoPasos
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.inacap.health.util.flujoInicioDelDia
import com.inacap.health.util.observar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class HistorialViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HealthApp
    private val registroDao = app.database.registroBiometricoDao()
    private val scoffDao = app.database.resultadoScoffDao()
    private val sharedPrefs = application.getSharedPreferences("health_prefs", Context.MODE_PRIVATE)

    val metaAguaMl: StateFlow<Int> = sharedPrefs.observar { it.getInt("meta_agua_ml", 2000) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sharedPrefs.getInt("meta_agua_ml", 2000))

    val registrosPesoAsc: StateFlow<List<RegistroBiometrico>> = registroDao.getRegistrosConPesoAsc()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Ventana de 7 días que avanza sola al cambiar de día.
    val registrosAgua7Dias: StateFlow<List<RegistroBiometrico>> = flujoInicioDelDia(diasAtras = 6)
        .flatMapLatest { registroDao.getRegistrosAguaFrom(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val metaPasos: Int get() = app.pasos.meta

    val pasos7Dias: StateFlow<List<PasosDia>> = flujoInicioDelDia(diasAtras = 6)
        .flatMapLatest { app.database.pasosDiaDao().desde(CalculoPasos.fecha(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pulsos: StateFlow<List<RegistroBiometrico>> = registroDao.getPulsosDesc()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tarea 18: historial por mes, semana a semana.
    val todosRegistros: StateFlow<List<RegistroBiometrico>> = registroDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val todosPasos: StateFlow<List<PasosDia>> = app.database.pasosDiaDao().observarTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val resultadosScoffDesc: StateFlow<List<ResultadoScoff>> = scoffDao.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
