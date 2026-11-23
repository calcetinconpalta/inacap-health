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

    @Test
    fun agua_enVasosYLitros_sinMl() {
        assertEquals("Llevas 3 de 8 vasos (0,75 de 2 L)", SaludLogic.textoProgresoAgua(750, 2000))
        assertEquals("Con tu peso te recomendamos unos 2,3 L al día (9 vasos)", SaludLogic.recomendacionAgua(2300))
        assertEquals("Llevas 0 de 8 vasos (0 de 2 L)", SaludLogic.textoProgresoAgua(0, SaludLogic.META_GENERAL_ML))
        assertEquals("1,8 L (7 vasos)", SaludLogic.litrosYVasos(1750))
        assertEquals("2,45", SaludLogic.litros(2450))
        assertEquals(1, SaludLogic.metaVasos(100))
        // La meta se calcula con el peso: 35 ml/kg + extra por actividad.
        assertEquals("Con tu peso te recomendamos unos 2,8 L al día (11 vasos)", SaludLogic.recomendacionAgua(SaludLogic.metaAguaMl(70.0, "moderado")))
        listOf(SaludLogic.textoProgresoAgua(750, 2000), SaludLogic.recomendacionAgua(2300)).forEach { assertEquals(false, it.contains("ml")) }
    }
}
