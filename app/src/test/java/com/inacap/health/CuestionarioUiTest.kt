package com.inacap.health

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.inacap.health.ui.components.PasoCuestionario
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tarea 16: Sí y No grandes; «Siguiente» solo tras elegir; «Volver a la pregunta anterior» aparte. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp", application = android.app.Application::class)
class CuestionarioUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun soloSiYNoYSiguienteAlElegir() {
        var elegida by mutableStateOf<Int?>(null)
        var siguientes = 0
        var anteriores = 0
        compose.setContent {
            PasoCuestionario(
                numero = 2, total = 5, pregunta = "¿Te provocas el vómito porque te sientes muy lleno/a?",
                opciones = listOf("Sí", "No"), elegida = elegida, onElegir = { elegida = it },
                onSiguiente = { siguientes++ }, onAnterior = { anteriores++ }
            )
        }
        compose.onNodeWithText("Sí").assertIsDisplayed()
        compose.onNodeWithText("No").assertIsDisplayed()
        compose.onAllNodesWithText("Siguiente").assertCountEquals(0)
        compose.onAllNodesWithText("Atrás").assertCountEquals(0)
        compose.onNodeWithText("No").performClick()
        compose.onNodeWithText("Siguiente").performClick()
        compose.onNodeWithText("Volver a la pregunta anterior").performClick()
        assertEquals(1, elegida); assertEquals(1, siguientes); assertEquals(1, anteriores)
    }

    @Test fun ultimaDiceVerResultadoYPrimeraNoTieneVolver() {
        compose.setContent {
            PasoCuestionario(numero = 1, total = 1, pregunta = "P", opciones = listOf("Sí", "No"), elegida = 0,
                onElegir = {}, onSiguiente = {}, onAnterior = null)
        }
        compose.onNodeWithText("Ver resultado").assertIsDisplayed()
        compose.onAllNodesWithText("Volver a la pregunta anterior").assertCountEquals(0)
    }
}
