package com.inacap.health

import com.inacap.health.data.PasosDia
import com.inacap.health.data.RegistroBiometrico
import com.inacap.health.ui.screens.salud.HistorialMensual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Tarea 18: el mes se ve por semanas, el mes nuevo parte en la semana 1 y se navega entre meses. */
class HistorialMensualTest {
    private val zona = ZoneId.of("America/Santiago")
    private fun ms(d: LocalDate, h: Int = 12) = d.atTime(h, 0).atZone(zona).toInstant().toEpochMilli()
    private fun agua(d: LocalDate) = RegistroBiometrico(pesoKg = null, estaturaCm = null, imc = null, aguaMl = 250, frecuenciaCardiaca = null, createdAt = ms(d))
    private fun pulso(d: LocalDate, bpm: Int) = RegistroBiometrico(pesoKg = null, estaturaCm = null, imc = null, aguaMl = 0, frecuenciaCardiaca = bpm, createdAt = ms(d))
    private fun imc(d: LocalDate, v: Double, h: Int) = RegistroBiometrico(pesoKg = 70.0, estaturaCm = 175.0, imc = v, aguaMl = 0, frecuenciaCardiaca = null, createdAt = ms(d, h))

    @Test fun semanasDelMesActualHastaHoy() {
        val hoy = LocalDate.of(2026, 11, 23)
        val s = HistorialMensual.semanas(YearMonth.of(2026, 11), hoy)
        assertEquals(listOf(1, 2, 3, 4), s.map { it.first })
        assertEquals(LocalDate.of(2026, 11, 22), s.last().second.start)
        // Mes completo pasado: 5 semanas (29–30 es la quinta).
        assertEquals(5, HistorialMensual.semanas(YearMonth.of(2026, 10), hoy).size)
        // Al empezar el mes siguiente se parte en la semana 1.
        assertEquals(listOf(1), HistorialMensual.semanas(YearMonth.of(2026, 12), LocalDate.of(2026, 12, 2)).map { it.first })
        assertTrue(HistorialMensual.semanas(YearMonth.of(2026, 12), hoy).isEmpty())
        assertTrue(HistorialMensual.puedeAvanzar(YearMonth.of(2026, 10), hoy))
        assertFalse(HistorialMensual.puedeAvanzar(YearMonth.of(2026, 11), hoy))
        assertEquals("Noviembre 2026", HistorialMensual.titulo(YearMonth.of(2026, 11)))
    }

    @Test fun resumenPorSemana() {
        val hoy = LocalDate.of(2026, 11, 23)
        val d3 = LocalDate.of(2026, 11, 3); val d4 = LocalDate.of(2026, 11, 4); val d10 = LocalDate.of(2026, 11, 10)
        val registros = List(8) { agua(d3) } + List(4) { agua(d4) } + listOf(pulso(d3, 70), pulso(d4, 80)) +
            listOf(imc(d10, 22.0, 9), imc(d10, 22.5, 18)) + listOf(agua(LocalDate.of(2026, 10, 31)))
        val pasos = listOf(PasosDia(fecha = "2026-11-03", pasos = 6000, fuente = "sensor"), PasosDia(fecha = "2026-11-05", pasos = 8000, fuente = "sensor"))
        val r = HistorialMensual.resumir(YearMonth.of(2026, 11), hoy, registros, pasos, zona)
        assertEquals(1500, r[0].aguaMlPorDia)          // (2000 + 1000) / 2 días con registro
        assertEquals(7000, r[0].pasosPorDia)
        assertEquals(75, r[0].pulsoPromedio)
        assertNull(r[0].imc)
        assertEquals(22.5, r[1].imc!!, 0.0)            // el último de la semana
        assertTrue(r[2].vacia)
    }
}
