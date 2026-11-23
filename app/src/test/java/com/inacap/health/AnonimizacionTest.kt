package com.inacap.health

import com.inacap.health.data.Perfil
import com.inacap.health.data.RegistroBiometrico
import com.inacap.health.data.ResultadoScoff
import com.inacap.health.data.sync.Anonimizacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AnonimizacionTest {

    @Test
    fun rangoEdad_agrupaSinRevelarEdadExacta() {
        assertEquals("sin_dato", Anonimizacion.rangoEdad(null))
        assertEquals("<18", Anonimizacion.rangoEdad(17))
        assertEquals("18-24", Anonimizacion.rangoEdad(18))
        assertEquals("18-24", Anonimizacion.rangoEdad(24))
        assertEquals("25-29", Anonimizacion.rangoEdad(25))
        assertEquals("30-39", Anonimizacion.rangoEdad(39))
        assertEquals("40+", Anonimizacion.rangoEdad(40))
    }

    @Test
    fun sexo_valoresDesconocidosSeNormalizan() {
        assertEquals("Femenino", Anonimizacion.sexo("Femenino"))
        assertEquals("sin_dato", Anonimizacion.sexo(""))
        assertEquals("sin_dato", Anonimizacion.sexo("cualquier cosa"))
    }

    @Test
    fun usuario_noIncluyeNombreNiDatosClinicos() {
        val perfil = Perfil(
            nombre = "Ana Pérez", edad = 21, sexo = "Femenino",
            patologias = "asma", alergias = "maní", medicamentos = "salbutamol",
            consentimiento = true
        )
        val json = Anonimizacion.usuario(perfil, "1.1", 0L).toString()
        listOf("Ana", "asma", "maní", "salbutamol", "21").forEach {
            assertFalse("No debe enviar '$it'", json.contains(it))
        }
        assertEquals("18-24", Anonimizacion.usuario(perfil, "1.1", 0L).getString("rango_edad"))
    }

    @Test
    fun registroImc_noIncluyePesoNiEstatura() {
        val r = RegistroBiometrico(pesoKg = 70.0, estaturaCm = 175.0, imc = 22.9, aguaMl = 0, frecuenciaCardiaca = null, createdAt = 1_700_000_000_000L)
        val json = Anonimizacion.registroBiometrico(r, "Moderado")!!
        assertEquals("imc", json.getString("tipo"))
        assertEquals("en_rango", json.getString("categoria_imc"))
        assertEquals("moderado", json.getString("nivel_actividad"))
        assertFalse(json.has("peso_kg"))
        assertFalse(json.toString().contains("70"))
        assertFalse(json.toString().contains("175"))
    }

    @Test
    fun registroAgua_yRegistroVacio() {
        val agua = RegistroBiometrico(pesoKg = null, estaturaCm = null, imc = null, aguaMl = 250, frecuenciaCardiaca = null, createdAt = 0)
        assertEquals(250, Anonimizacion.registroBiometrico(agua)!!.getInt("agua_ml"))
        val vacio = agua.copy(aguaMl = 0)
        assertNull(Anonimizacion.registroBiometrico(vacio))
    }

    @Test
    fun scoff_noIncluyeRespuestasIndividuales() {
        val r = ResultadoScoff(puntaje = 2, respuestas = "1,1,0,0,0", requiereDerivacion = true, createdAt = 0)
        val json = Anonimizacion.resultadoScoff(r)
        assertFalse(json.has("respuestas"))
        assertEquals(2, json.getInt("puntaje"))
    }

    @Test
    fun categoriaImc_usaRangosOms() {
        assertEquals("bajo_rango", Anonimizacion.categoriaImc(18.4))
        assertEquals("en_rango", Anonimizacion.categoriaImc(18.5))
        assertEquals("en_rango", Anonimizacion.categoriaImc(24.9))
        assertEquals("sobre_rango", Anonimizacion.categoriaImc(25.0))
    }

    @Test
    fun canalDerivacion() {
        assertEquals("cesfam", Anonimizacion.canalDerivacion("CESFAM (orientación hacia la red de salud pública)"))
        assertEquals("bienestar_correo", Anonimizacion.canalDerivacion("Bienestar Estudiantil (contacto por correo)"))
        assertEquals("bienestar_presencial", Anonimizacion.canalDerivacion("Bienestar Estudiantil (atención presencial)"))
    }

    @Test
    fun fechaIso_enUtc() {
        assertEquals("1970-01-01T00:00:00.000Z", Anonimizacion.fechaIso(0))
    }

    @Test
    fun evento_limitaPropiedades() {
        val props = Anonimizacion.PROPS_PERMITIDAS.associateWith { "x".repeat(500) } + ("nombre" to "Ana")
        val e = Anonimizacion.evento("pantalla_vista", props, 0, "1.1", 34)
        val p = e.getJSONObject("props")
        assertEquals("Máximo 10 campos", 10, p.length())
        assertEquals("Un campo desconocido nunca se envía", false, p.has("nombre"))
        p.keys().forEach { assertEquals(Anonimizacion.LARGO_MAX_TEXTO, p.getString(it).length) }
        assertEquals("pantalla_vista", e.getString("tipo"))
    }
}
