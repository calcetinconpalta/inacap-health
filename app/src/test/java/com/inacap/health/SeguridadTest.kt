package com.inacap.health

import com.inacap.health.seguridad.CifradoBaseDatos
import com.inacap.health.seguridad.Pin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SeguridadTest {
    @Test
    fun pin_validacionYTriviales() {
        assertTrue(Pin.esValido("2580"))
        assertFalse(Pin.esValido("123"))       // muy corto
        assertFalse(Pin.esValido("123456789")) // muy largo
        assertFalse(Pin.esValido("12a4"))
        listOf("1111", "1234", "4321", "98765").forEach { assertTrue(it, Pin.esTrivial(it)) }
        listOf("2580", "1357", "9413").forEach { assertFalse(it, Pin.esTrivial(it)) }
    }

    @Test
    fun pin_hashConSal_noReversibleYVerificable() {
        val sal = Pin.nuevaSal()
        val hash = Pin.hash("2580", sal, iteraciones = 1000)
        assertEquals(32, hash.size)
        assertTrue(Pin.coincide("2580", sal, hash, iteraciones = 1000))
        assertFalse(Pin.coincide("2581", sal, hash, iteraciones = 1000))
        // Misma clave con otra sal = otro hash (no sirven tablas precalculadas)
        assertFalse(Pin.hash("2580", Pin.nuevaSal(), 1000).contentEquals(hash))
        assertArrayEquals(hash, Pin.hash("2580", sal, 1000))
    }

    @Test
    fun pin_esperaCreceTrasFallos() {
        assertEquals(0, Pin.esperaTrasFallos(4))
        assertEquals(30_000, Pin.esperaTrasFallos(5))
        assertEquals(60_000, Pin.esperaTrasFallos(6))
        assertEquals(15 * 60_000L, Pin.esperaTrasFallos(50))
    }

    @Test
    fun bloqueo_seActivaSoloTrasElTiempoConfigurado() {
        val salio = 1_000_000L
        assertFalse(Pin.debeBloquear(true, salio, salio + 4 * 60_000, minutos = 5))
        assertTrue(Pin.debeBloquear(true, salio, salio + 5 * 60_000, minutos = 5))
        assertTrue(Pin.debeBloquear(true, salio, salio + 1, minutos = 0))   // "al salir"
        assertFalse(Pin.debeBloquear(false, salio, salio + 99 * 60_000, minutos = 5)) // sin PIN
        assertFalse(Pin.debeBloquear(true, 0, salio, minutos = 0))          // nunca salió
    }

    @Test
    fun detectaBaseSinCifrar() {
        val plana = File.createTempFile("plana", ".db").apply { writeBytes("SQLite format 3\u0000".toByteArray() + ByteArray(100)) }
        val cifrada = File.createTempFile("cifrada", ".db").apply { writeBytes(ByteArray(116) { (it * 37).toByte() }) }
        assertTrue(CifradoBaseDatos.esBasePlana(plana))
        assertFalse(CifradoBaseDatos.esBasePlana(cifrada))
        assertFalse(CifradoBaseDatos.esBasePlana(File("/no/existe.db")))
    }
}
