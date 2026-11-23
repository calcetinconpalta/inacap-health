package com.inacap.health

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.inacap.health.data.Perfil
import com.inacap.health.data.datosCompletos
import com.inacap.health.legal.TextosLegales
import com.inacap.health.ui.nav.AppNav
import com.inacap.health.ui.screens.perfil.PerfilViewModel
import com.inacap.health.ui.theme.INACAPHealthTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La barra inferior siempre debe llevar a la pestaña tocada (bug: "Inicio" no volvía). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp")
class NavegacionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<HealthApp>()

    @Before fun preparar() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking(Dispatchers.IO) { app.database.clearAllTables() }
    }

    private fun conPerfil() = runBlocking(Dispatchers.IO) {
        app.database.perfilDao().upsertPerfil(
            Perfil(nombre = "Ana", edad = 21, sexo = "Femenino", patologias = "", alergias = "", medicamentos = "",
                consentimiento = true, terminosVersion = TextosLegales.VERSION)
        )
    }

    private fun tab(nombre: String) {
        compose.onNode(hasText(nombre) and isSelectable()).performClick()
        compose.waitForIdle(); Thread.sleep(100); compose.waitForIdle()
    }
    private fun enInicio() = compose.onNodeWithText("Hola, Ana").assertIsDisplayed()

    private fun abrir(esperar: String = "Inicio") {
        val vm = PerfilViewModel(app)
        compose.setContent { INACAPHealthTheme { AppNav(perfilViewModel = vm) } }
        repeat(50) {
            if (compose.onAllNodesWithContentDescription(esperar).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText(esperar).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(100); compose.waitForIdle()
        }
        compose.onRoot().printToLog("ARBOL")
        println(compose.onRoot().fetchSemanticsNode().config)
        error("No apareció '$esperar'")
    }

    @Test fun atrasMuchasVecesNuncaDejaLaPantallaNegra() {
        conPerfil(); abrir()
        tab("Salud")
        compose.onNodeWithText("Historial").performClick(); compose.waitForIdle()
        // Varios toques seguidos en la flecha «Atrás» y en el atrás del teléfono.
        repeat(3) {
            compose.onAllNodesWithContentDescription("Atrás").fetchSemanticsNodes().firstOrNull()?.let {
                compose.onAllNodesWithContentDescription("Atrás")[0].performClick()
            }
            compose.waitForIdle()
        }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        enInicio()
    }

    @Test fun datosCompletosParaRecomendaciones() {
        val base = Perfil(nombre = "", edad = null, sexo = "", patologias = "", alergias = "", medicamentos = "", consentimiento = true)
        org.junit.Assert.assertFalse(base.datosCompletos())
        org.junit.Assert.assertFalse((null as Perfil?).datosCompletos())
        org.junit.Assert.assertTrue(base.copy(edad = 21, sexo = "Femenino").datosCompletos())
    }

    @Test fun inicioPideCompletarDatosSiFaltan() {
        runBlocking(Dispatchers.IO) {
            app.database.perfilDao().upsertPerfil(
                Perfil(nombre = "Ana", edad = null, sexo = "", patologias = "", alergias = "", medicamentos = "",
                    consentimiento = true, terminosVersion = TextosLegales.VERSION)
            )
        }
        abrir()
        compose.onNodeWithText("Completa tus datos para recomendaciones personalizadas").assertIsDisplayed()
    }

    @Test fun inicioVuelveDesdeOtraPestana() {
        conPerfil(); abrir()
        tab("Salud"); compose.onNodeWithText("Calculadoras IMC e hidratación").assertIsDisplayed()
        tab("Inicio"); enInicio()
        tab("Bienestar"); tab("Perfil"); tab("Inicio"); enInicio()
    }

    @Test fun inicioVuelveTrasEntrarAUnaSubpantalla() {
        conPerfil(); abrir()
        tab("Salud")
        compose.onNodeWithText("Historial").performClick(); compose.waitForIdle()
        compose.onNodeWithContentDescription("Atrás").performClick(); compose.waitForIdle()
        tab("Inicio"); enInicio()
        tab("Salud"); tab("Inicio"); enInicio()
    }

    @Test fun casitaVuelveDesdeUnaSubpantalla() {
        conPerfil(); abrir()
        tab("Salud")
        compose.onNodeWithText("Historial").performClick(); compose.waitForIdle()
        compose.onNodeWithText("Pasos (últimos 7 días)", substring = true).assertDoesNotExist()  // sigue en la pestaña Peso/IMC
        tab("Inicio"); enInicio()                                   // la barra se ve y la casita funciona
        tab("Salud"); compose.onNodeWithText("Calculadoras IMC e hidratación").assertIsDisplayed() // el menú, no el historial
        compose.onNodeWithText("Pulso con cámara").performClick(); compose.waitForIdle()
        tab("Perfil"); compose.onNodeWithText("Mis datos").assertIsDisplayed()
        compose.onNodeWithText("Privacidad y mis datos").performClick(); compose.waitForIdle()
        tab("Inicio"); enInicio()
    }

    @Test fun inicioVuelveDespuesDelOnboarding() {
        abrir("Siguiente")  // sin perfil: empieza en la bienvenida
        compose.onNodeWithText("Siguiente").performClick()
        compose.onNodeWithText("He leído", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Tengo 18 años", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Comenzar").performScrollTo().performClick()
        // Con la nube configurada (claves en .env / local.properties) la app ofrece crear una cuenta.
        // Sin claves esa pantalla no aparece: la prueba sirve en los dos casos.
        repeat(20) {
            if (compose.onAllNodesWithText("Seguir sin cuenta").fetchSemanticsNodes().isNotEmpty()) return@repeat
            if (compose.onAllNodes(hasText("Salud") and isSelectable()).fetchSemanticsNodes().isNotEmpty()) return@repeat
            Thread.sleep(100); compose.waitForIdle()
        }
        if (compose.onAllNodesWithText("Seguir sin cuenta").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Seguir sin cuenta").performScrollTo().performClick()
        }
        repeat(50) { if (compose.onAllNodes(hasText("Salud") and isSelectable()).fetchSemanticsNodes().isNotEmpty()) return@repeat; Thread.sleep(100); compose.waitForIdle() }
        tab("Salud"); compose.onNodeWithText("Calculadoras IMC e hidratación").assertIsDisplayed()
        tab("Inicio"); compose.onNodeWithText("Hola").assertIsDisplayed()
        tab("Perfil"); tab("Inicio"); compose.onNodeWithText("Hola").assertIsDisplayed()
    }
}
