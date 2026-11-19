package com.inacap.health

import com.inacap.health.data.remote.ExplicacionError
import org.junit.Assert.assertTrue
import org.junit.Test

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
    }
}
