package com.inacap.health.data.remote

/**
 * Traduce los errores del servicio en la nube.
 * - [paraPersona]: mensaje sencillo para estudiantes, sin nombres de servicios ni detalles técnicos.
 * - [explicar]: qué hay que corregir en la configuración (solo equipo y compilaciones de prueba).
 */
object ExplicacionError {
    fun paraPersona(mensaje: String?, codigo: Int? = null): String {
        val m = (mensaje ?: "").lowercase()
        return when {
            codigo == -1 || "sin conexión" in m -> "Sin conexión a internet. Se enviará cuando vuelvas a tener conexión."
            codigo == 429 -> "Hiciste muchas acciones seguidas. Espera unos minutos y vuelve a intentarlo."
            else -> "No pudimos enviar tus datos todavía. Se volverá a intentar automáticamente."
        }
    }

    fun explicar(mensaje: String?, codigo: Int? = null): String {
        val m = (mensaje ?: "").lowercase()
        val esSupabase = m.startsWith("supabase")
        return when {
            codigo == -1 || "sin conexión" in m -> "Sin conexión a internet (o la red bloquea el servidor)."
            // ----- Supabase -----
            esSupabase && "anonymous" in m && ("disabled" in m || "provider" in m) ->
                "Supabase: activa «Allow anonymous sign-ins» en Authentication → Sign In / Providers."
            esSupabase && ("invalid api key" in m || "no api key" in m || "unregistered api key" in m) ->
                "Supabase: la clave no es válida. Copia la publishable (o anon) key desde Project Settings → API Keys."
            esSupabase && ("pgrst205" in m || "could not find the table" in m || "does not exist" in m || "pgrst202" in m) ->
                "Supabase: faltan las tablas. Ejecuta backend/supabase/schema.sql completo en el SQL Editor."
            esSupabase && ("row-level security" in m || "42501" in m || "permission denied" in m) ->
                "Supabase: permisos rechazados. Vuelve a ejecutar schema.sql completo (crea las políticas de seguridad)."
            esSupabase && codigo == 401 -> "Supabase: sesión o clave rechazada (401). Revisa SUPABASE_URL y la clave en local.properties."
            esSupabase && codigo == 404 -> "Supabase: dirección no encontrada (404). Revisa SUPABASE_URL o ejecuta schema.sql."
            // ----- Firebase -----
            "admin_only_operation" in m || "operation_not_allowed" in m || "configuration_not_found" in m ->
                "Firebase: activa el método «Anónimo» en Authentication → Método de acceso."
            "api_key_android_app_blocked" in m || "android client application" in m || "api_key_service_blocked" in m || "requests from this android" in m ->
                "Firebase: la API key está restringida y no acepta esta app. En Google Cloud → Credenciales, agrega el SHA-1 de esta app o quita la restricción."
            "api key not valid" in m || "api_key_invalid" in m -> "Firebase: la API key no es válida. Copia current_key de google-services.json."
            "permission_denied" in m || ("firestore" in m && codigo == 403) ->
                "Firestore: las reglas rechazan el envío. Publica backend/firebase/firestore.rules en Firestore → Reglas."
            "firestore" in m && (codigo == 404 || "not_found" in m || "does not exist" in m) ->
                "Firestore: la base no existe. Créala en Firestore Database → Crear base de datos."
            codigo == 429 -> "Demasiados envíos seguidos: se reintentará en unos minutos."
            codigo != null && codigo >= 500 -> "El servidor no responde ahora: se reintentará solo."
            else -> "Error: ${mensaje?.take(160) ?: "desconocido"}"
        }
    }
}
