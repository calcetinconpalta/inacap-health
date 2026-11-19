package com.inacap.health

import com.inacap.health.salud.PerfilFrecuenciaCardiaca
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Decodificación de la característica Heart Rate Measurement (0x2A37) según la especificación. */
class RelojBluetoothTest {
    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun bpmDe8Bits() {
        val l = PerfilFrecuenciaCardiaca.decodificar(b(0x00, 72))!!
        assertEquals(72, l.bpm); assertNull(l.contacto); assertEquals(emptyList<Int>(), l.rrMs)
    }

    @Test fun bpmDe16BitsConContacto() {
        // flags: 16 bits (0x01) + contacto detectado (0x06) ; 300 = 0x012C (little endian)
        val l = PerfilFrecuenciaCardiaca.decodificar(b(0x07, 0x2C, 0x01))!!
        assertEquals(300, l.bpm); assertEquals(true, l.contacto)
    }

    @Test fun sinContactoConLaPiel() {
        assertEquals(false, PerfilFrecuenciaCardiaca.decodificar(b(0x04, 0))!!.contacto)
    }

    @Test fun energiaEIntervalosRR() {
        // flags: energía (0x08) + RR (0x10); bpm 60; energía 0x0100; RR = 1024 (=1000 ms) y 512 (=500 ms)
        val l = PerfilFrecuenciaCardiaca.decodificar(b(0x18, 60, 0x00, 0x01, 0x00, 0x04, 0x00, 0x02))!!
        assertEquals(60, l.bpm); assertEquals(listOf(1000, 500), l.rrMs)
    }

    @Test fun datosIncompletos() {
        assertNull(PerfilFrecuenciaCardiaca.decodificar(ByteArray(0)))
        assertNull(PerfilFrecuenciaCardiaca.decodificar(b(0x01, 0x2C)))
    }

    @Test fun promedioDescartaValoresImposibles() {
        assertEquals(71, PerfilFrecuenciaCardiaca.promedio(listOf(70, 72, 0, 255, 71)))
        assertNull(PerfilFrecuenciaCardiaca.promedio(listOf(0, 0)))
    }
}
