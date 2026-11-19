package com.inacap.health.ui.screens.bienestar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inacap.health.HealthApp
import com.inacap.health.data.ResultadoScoff
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScoffUiState(
    val preguntaActual: Int = 0,
    val respuestas: List<Boolean?> = List(5) { null },
    val mostrandoResultado: Boolean = false,
    val puntaje: Int = 0,
    val requiereDerivacion: Boolean = false
)

class ScoffViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HealthApp
    private val dao = app.database.resultadoScoffDao()

    private val _uiState = MutableStateFlow(ScoffUiState())
    val uiState: StateFlow<ScoffUiState> = _uiState.asStateFlow()

    private var guardadoEnBase: Boolean = false

    fun responder(opcion: Boolean) {
        val actual = _uiState.value
        val nuevasRespuestas = actual.respuestas.toMutableList()
        nuevasRespuestas[actual.preguntaActual] = opcion
        _uiState.value = actual.copy(respuestas = nuevasRespuestas)
    }

    fun siguiente() {
        val actual = _uiState.value
        if (actual.preguntaActual < 4) {
            _uiState.value = actual.copy(preguntaActual = actual.preguntaActual + 1)
        } else {
            finalizarTest()
        }
    }

    fun anterior() {
        val actual = _uiState.value
        if (actual.preguntaActual > 0) {
            _uiState.value = actual.copy(preguntaActual = actual.preguntaActual - 1)
        }
    }

    private fun finalizarTest() {
        val actual = _uiState.value
        val puntaje = actual.respuestas.count { it == true }
        val respuestasStr = actual.respuestas.joinToString(",") { if (it == true) "1" else "0" }
        val requiereDerivacion = puntaje >= 2

        _uiState.value = actual.copy(
            mostrandoResultado = true,
            puntaje = puntaje,
            requiereDerivacion = requiereDerivacion
        )

        if (!guardadoEnBase) {
            guardadoEnBase = true
            viewModelScope.launch {
                val resultado = ResultadoScoff(
                    puntaje = puntaje,
                    respuestas = respuestasStr,
                    requiereDerivacion = requiereDerivacion,
                    createdAt = System.currentTimeMillis()
                )
                dao.insert(resultado)
                // Nube: solo puntaje y si sugiere derivación (nunca las respuestas).
                app.sync.encolarScoff(resultado)
                app.sync.registrarEvento(
                    "scoff_completado",
                    mapOf("requiere_derivacion" to requiereDerivacion)
                )
            }
        }
    }
}
