package com.inacap.health

import com.inacap.health.data.PasosDia
import com.inacap.health.data.Perfil
import com.inacap.health.data.RegistroBiometrico
import com.inacap.health.data.ResultadoScoff
import com.inacap.health.data.sync.Anonimizacion
import com.inacap.health.orientacion.SenalesDeAlerta
import com.inacap.health.salud.CalculoPasos
import com.inacap.health.salud.DatosResumen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActividadYResumenTest {

    @Test
    fun pasos_deltaDelContador() {
        assertEquals(0, CalculoPasos.delta(null, 5000))     // primera lectura: referencia
        assertEquals(250, CalculoPasos.delta(5000, 5250))
        assertEquals(120, CalculoPasos.delta(5250, 120))    // el teléfono se reinició
        assertEquals(0, CalculoPasos.delta(5250, 5250))
        assertEquals(8000, CalculoPasos.combinar(sensor = 6000, healthConnect = 8000)) // no se suman (duplicaría)
    }

    @Test
    fun pulsoYPasos_seAnonimizan() {
        val pulso = RegistroBiometrico(pesoKg = null, estaturaCm = null, imc = null, aguaMl = 0, frecuenciaCardiaca = 72, fuente = "camara", createdAt = 0)
        val j = Anonimizacion.registroBiometrico(pulso)!!
        assertEquals("pulso", j.getString("tipo"))
        assertEquals(72, j.getInt("frecuencia_cardiaca"))
        assertNull(Anonimizacion.registroBiometrico(pulso.copy(frecuenciaCardiaca = 400)))  // fuera de rango
        val p = Anonimizacion.pasosDia(PasosDia(fecha = "2026-11-19", pasos = 6543, fuente = "sensor"))
        assertEquals("pasos", p.getString("tipo"))
        assertEquals("2026-11-19T12:00:00.000Z", p.getString("fecha")) // solo el día, sin hora real
    }

    @Test
    fun resumen_respetaPeriodoYOpcionesDePrivacidad() {
        val ahora = 1_800_000_000_000L
        val dia = 86_400_000L
        val perfil = Perfil(nombre = "Ana", edad = 21, sexo = "Femenino", patologias = "asma", alergias = "", medicamentos = "", consentimiento = true)
        val registros = listOf(
            RegistroBiometrico(pesoKg = 70.0, estaturaCm = 175.0, imc = 22.9, aguaMl = 0, frecuenciaCardiaca = null, createdAt = ahora - 2 * dia),
            RegistroBiometrico(pesoKg = 71.0, estaturaCm = 175.0, imc = 23.2, aguaMl = 0, frecuenciaCardiaca = null, createdAt = ahora - 200 * dia), // fuera
            RegistroBiometrico(pesoKg = null, estaturaCm = null, imc = null, aguaMl = 250, frecuenciaCardiaca = null, createdAt = ahora - dia),
            RegistroBiometrico(pesoKg = null, estaturaCm = null, imc = null, aguaMl = 250, frecuenciaCardiaca = null, createdAt = ahora - dia + 1000),
            RegistroBiometrico(pesoKg = null, estaturaCm = null, imc = null, aguaMl = 0, frecuenciaCardiaca = 70, fuente = "health_connect", createdAt = ahora - dia)
        )
        val scoff = listOf(ResultadoScoff(puntaje = 1, respuestas = "1,0,0,0,0", requiereDerivacion = false, createdAt = ahora - dia))

        val d = DatosResumen.construir(perfil, registros, emptyList(), scoff, dias = 90, incluirNombre = false, incluirScoff = false, ahora = ahora)
        assertNull("Nombre solo si se elige", d.nombre)
        assertNull("SCOFF solo si se elige", d.scoff)
        assertEquals(1, d.imc.size)
        assertEquals(1, d.agua.size)
        assertEquals(500, d.agua.single().second)          // agrupado por día
        assertEquals("Reloj", d.pulsos.single().fuente)

        val conTodo = DatosResumen.construir(perfil, registros, emptyList(), scoff, dias = 365, incluirNombre = true, incluirScoff = true, ahora = ahora)
        assertEquals("Ana", conTodo.nombre)
        assertEquals(1, conTodo.scoff!!.size)
        assertEquals(2, conTodo.imc.size)
    }

    @Test
    fun senalesDeAlerta_enElChat() {
        assertTrue(SenalesDeAlerta.hay("a veces me provoco el vómito"))
        assertTrue(SenalesDeAlerta.hay("No quiero vivir así"))
        assertFalse(SenalesDeAlerta.hay("¿qué colación me recomiendan?"))
    }

    @Test
    fun orientacion_recomiendaSinBloquear_soloConScoffReciente() {
        val dia = 24 * 60 * 60_000L
        val ahora = 100 * dia
        fun r(alerta: Boolean, haceDias: Int) = ResultadoScoff(puntaje = if (alerta) 3 else 0, respuestas = "", requiereDerivacion = alerta, createdAt = ahora - haceDias * dia)
        val reciente = com.inacap.health.orientacion.RecomendacionApoyo
        assertTrue(reciente.scoffReciente(listOf(r(true, 5)), ahora))
        assertFalse("Más de 30 días: ya no cuenta", reciente.scoffReciente(listOf(r(true, 31)), ahora))
        assertFalse("Cuenta el ÚLTIMO resultado", reciente.scoffReciente(listOf(r(false, 1), r(true, 3)), ahora))
        assertFalse(reciente.scoffReciente(emptyList(), ahora))
    }
}
