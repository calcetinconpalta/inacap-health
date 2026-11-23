package com.inacap.health

import com.inacap.health.data.remote.ExplicacionError
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

class ExplicacionErrorTest {
    private fun dice(mensaje: String, codigo: Int?, contiene: String) {
        val r = ExplicacionError.explicar(mensaje, codigo)
        assertTrue("«$r» debería mencionar «$contiene»", r.contains(contiene))
    }

    @Test fun traduceErroresComunes() {
        dice("Supabase inicio anónimo: HTTP 422 {\"error_code\":\"anonymous_provider_disabled\",\"msg\":\"Anonymous sign-ins are disabled\"}", 422, "Allow anonymous sign-ins")
        dice("Supabase upsert usuarios: HTTP 404 {\"code\":\"PGRST205\",\"message\":\"Could not find the table\"}", 404, "schema.sql")
        dice("Supabase upsert usuarios: HTTP 401 {\"message\":\"Invalid API key\"}", 401, "clave")
        dice("Firebase inicio anónimo: HTTP 400 {\"error\":{\"message\":\"ADMIN_ONLY_OPERATION\"}}", 400, "Anónimo")
        dice("Firebase inicio anónimo: HTTP 403 {\"error\":{\"message\":\"Requests from this Android client application com.inacap.health are blocked.\",\"status\":\"PERMISSION_DENIED\"}}", 403, "SHA-1")
        dice("Firestore crear eventos: HTTP 403 {\"error\":{\"status\":\"PERMISSION_DENIED\"}}", 403, "reglas")
        dice("Firestore crear eventos: HTTP 404 {\"error\":{\"status\":\"NOT_FOUND\",\"message\":\"The database (default) does not exist\"}}", 404, "Crear base de datos")
        dice("Sin conexión: UnknownHostException", -1, "internet")
        // Ya no todo es "sin internet": cada falla de red se explica según su causa real.
        dice("Sin respuesta (POST /rest/v1/usuarios): SocketTimeoutException: timeout", -1, "tardó demasiado")
        dice("Sin respuesta (POST /rest/v1/usuarios): SSLHandshakeException: Chain validation failed", -1, "certificado")
        dice("Sin respuesta (POST /rest/v1/usuarios): ConnectException: failed to connect", -1, "bloquea")
        dice("Sin respuesta (GET /rest/v1/usuarios): UnknownHostException: Unable to resolve host", -1, "DNS")
    }

    @Test fun laPersonaVeLaCausaYElDetalleSinDireccionesDeServidores() {
        val m = "Sin respuesta (POST /rest/v1/usuarios): SocketTimeoutException: failed to connect to abcdxyz.supabase.co/1.2.3.4 (port 443) after 15000ms"
        assertTrue(ExplicacionError.paraPersona(m, -1).contains("tardó demasiado"))
        val oculto = ExplicacionError.detalle("$m · https://abcdxyz.supabase.co/rest", completo = false)!!
        assertFalse(oculto.contains("supabase"))
        assertTrue(oculto.contains("SocketTimeoutException") && oculto.contains("/rest/v1/usuarios"))
        assertTrue(ExplicacionError.detalle(m, completo = true)!!.contains("abcdxyz.supabase.co"))
        assertTrue(ExplicacionError.paraPersona("Supabase upsert usuarios: HTTP 500 boom", 500).contains("problema"))
    }

    @Test fun laFallaDeRedIncluyeMetodoRutaYCausa() {
        val m = com.inacap.health.data.remote.Http.falla("POST", "https://x.supabase.co/rest/v1/usuarios?on_conflict=id",
            java.io.IOException("envoltura", java.net.SocketTimeoutException("timeout")))
        assertEquals("Sin respuesta (POST /rest/v1/usuarios): IOException: envoltura ← SocketTimeoutException: timeout", m)
    }
}
