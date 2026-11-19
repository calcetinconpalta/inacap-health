package com.inacap.health

import com.inacap.health.salud.Fotopletismografia
import com.inacap.health.salud.Fotopletismografia.Calidad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class FotopletismografiaTest {

    /** Señal PPG sintética: latido + deriva lenta + ruido, ~30 cuadros/s con jitter. */
    private fun senal(bpm: Double, segundos: Int = 20, ruido: Double = 0.4, semilla: Int = 1): Pair<LongArray, DoubleArray> {
        val r = Random(semilla)
        val tiempos = mutableListOf<Long>()
        var t = 0L
        while (t < segundos * 1000L) { tiempos += t; t += 30 + r.nextInt(8) }
        val f = bpm / 60.0
        val valores = tiempos.map { ms ->
            val s = ms / 1000.0
            val latido = sin(2 * PI * f * s) + 0.35 * sin(4 * PI * f * s + 0.8) // onda con muesca dicrótica
            180 + 1.5 * latido + 6 * sin(2 * PI * 0.1 * s) + ruido * (r.nextDouble() - 0.5)
        }
        return tiempos.toLongArray() to valores.toDoubleArray()
    }

    @Test
    fun estimaPulsoEnReposo() {
        val (t, v) = senal(72.0)
        val r = Fotopletismografia.estimar(t, v)
        assertEquals(72.0, r.bpm!!.toDouble(), 3.0)
        assertEquals(Calidad.BUENA, r.calidad)
    }

    @Test
    fun estimaPulsoRapidoYLento() {
        listOf(50.0, 95.0, 130.0).forEach { bpm ->
            val (t, v) = senal(bpm, semilla = bpm.toInt())
            val r = Fotopletismografia.estimar(t, v)
            assertEquals("bpm=$bpm", bpm, r.bpm!!.toDouble(), 4.0)
        }
    }

    @Test
    fun soloRuido_noInventaUnResultado() {
        val r = Random(7)
        val t = LongArray(600) { it * 33L }
        val v = DoubleArray(600) { 180 + 3 * r.nextDouble() }
        val res = Fotopletismografia.estimar(t, v)
        assertNull(res.bpm)
        assertEquals(Calidad.INSUFICIENTE, res.calidad)
    }

    @Test
    fun medicionDemasiadoCorta() {
        val (t, v) = senal(72.0, segundos = 5)
        assertNull(Fotopletismografia.estimar(t, v).bpm)
    }

    @Test
    fun deteccionDelDedo() {
        assertTrue(Fotopletismografia.dedoDetectado(rojoMedio = 200.0, verdeMedio = 40.0))
        assertFalse(Fotopletismografia.dedoDetectado(rojoMedio = 120.0, verdeMedio = 118.0)) // imagen normal
        assertFalse(Fotopletismografia.dedoDetectado(rojoMedio = 30.0, verdeMedio = 5.0))    // oscuro
        val (r, g) = Fotopletismografia.rojoYVerde(yMedio = 110.0, uMedio = 100.0, vMedio = 190.0)
        assertTrue(Fotopletismografia.dedoDetectado(r, g))
    }
}
