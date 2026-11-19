package com.inacap.health

import com.inacap.health.ui.screens.salud.SaludLogic
import org.junit.Assert.assertEquals
import org.junit.Test

class SaludLogicTest {
    @Test
    fun imc_seRedondeaAUnDecimal() {
        assertEquals(22.9, SaludLogic.calcularImc(70.0, 175.0), 0.0)
        assertEquals(0.0, SaludLogic.calcularImc(70.0, 0.0), 0.0)
    }

    @Test
    fun metaAgua_segunPesoYActividad() {
        assertEquals(2800, SaludLogic.metaAguaMl(70.0, "Moderado"))
        assertEquals(2450, SaludLogic.metaAguaMl(70.0, "Bajo"))
        assertEquals(3150, SaludLogic.metaAguaMl(70.0, "Alto"))
        assertEquals(500, SaludLogic.metaAguaMl(5.0, "Bajo"))
    }
}
