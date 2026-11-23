package com.inacap.health

import com.inacap.health.data.ResultadoCuestionario
import com.inacap.health.data.sync.Anonimizacion
import com.inacap.health.salud.Cuestionarios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tarea 18f: puntajes y cortes de las versiones verificadas; PHQ-9 pregunta 9 activa la ayuda. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CuestionariosTest {
    @Test fun who5() {
        val bien = Cuestionarios.evaluar(Cuestionarios.WHO5, listOf(4, 4, 3, 3, 4))
        assertEquals(72, bien.puntaje); assertFalse(bien.alerta)
        assertTrue("Suma < 13", Cuestionarios.evaluar(Cuestionarios.WHO5, listOf(2, 2, 2, 3, 3)).alerta)
        assertTrue("Un ítem en 0 o 1", Cuestionarios.evaluar(Cuestionarios.WHO5, listOf(5, 5, 5, 5, 1)).alerta)
    }

    @Test fun phq2OfrecePhq9Desde3() {
        assertFalse(Cuestionarios.evaluar(Cuestionarios.PHQ2, listOf(1, 1)).alerta)
        val r = Cuestionarios.evaluar(Cuestionarios.PHQ2, listOf(2, 1))
        assertTrue(r.alerta); assertEquals("phq9", r.ofrecer)
    }

    @Test fun phq9CortesYPregunta9() {
        val leve = Cuestionarios.evaluar(Cuestionarios.PHQ9, listOf(1, 1, 1, 1, 1, 1, 1, 0, 0))
        assertEquals("Leve", leve.nivel); assertFalse(leve.alerta); assertFalse(leve.ayudaUrgente)
        assertTrue(Cuestionarios.evaluar(Cuestionarios.PHQ9, listOf(2, 2, 2, 2, 1, 1, 0, 0, 0)).alerta)   // 10
        val p9 = Cuestionarios.evaluar(Cuestionarios.PHQ9, listOf(0, 0, 0, 0, 0, 0, 0, 0, 1))
        assertTrue("Pregunta 9 ≠ Nunca: ayuda sin importar el puntaje", p9.ayudaUrgente && p9.alerta)
        assertEquals("Nunca", Cuestionarios.PHQ9.opciones.first().first)
    }

    @Test fun losNoVerificadosNoSePuedenResponder() {
        Cuestionarios.TODOS.filter { it.clave in setOf("gad7", "pss10", "auditc", "ipaq") }.forEach { assertFalse(it.disponible) }
    }

    @Test fun aLaNubeSoloElPuntaje() {
        val j = Anonimizacion.cuestionario(ResultadoCuestionario(tipo = "phq9", puntaje = 7, respuestas = "1,1,1,1,1,1,1,0,0", alerta = false))
        assertEquals(setOf("tipo", "puntaje", "alerta", "fecha"), j.keys().asSequence().toSet())
    }
}
