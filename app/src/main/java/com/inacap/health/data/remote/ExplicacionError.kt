package com.inacap.health.data.remote

/**
 * Traduce los errores del servicio en la nube.
 * - [paraPersona]: mensaje sencillo para estudiantes, sin nombres de servicios ni detalles técnicos.
 * - [explicar]: qué hay que corregir en la configuración (solo equipo y compilaciones de prueba).
 */
object ExplicacionError {
    /** Tipo de falla cuando el servidor no respondió (código -1), según la excepción real. */
    fun fallaDeRed(mensaje: String?): String? {
        val m = mensaje ?: return null
        if (!m.startsWith("Sin respuesta") && !m.startsWith("Sin conexión")) return null
        return when {
            "UnknownHostException" in m -> "No se encontró el servidor: el teléfono no tiene internet o la red no resuelve la dirección (DNS)."
            "SocketTimeoutException" in m || "timeout" in m.lowercase() -> "El servidor tardó demasiado en responder (más de 15 s). Puede estar lento o la conexión es débil."
            "SSLHandshakeException" in m || "CertPathValidatorException" in m || "SSLPeerUnverified" in m || "SSLException" in m ->
                "La conexión segura fue rechazada (certificado). Revisa la fecha y hora del teléfono, o si la red Wi-Fi pide iniciar sesión."
            "ConnectException" in m || "ECONNREFUSED" in m || "NoRouteToHost" in m -> "No se pudo conectar con el servidor: la red lo bloquea o el servidor no está disponible."
            "ProtocolException" in m -> "El servidor respondió algo que la app no esperaba (error de protocolo)."
            "SocketException" in m || "EOFException" in m || "reset" in m.lowercase() -> "La conexión se cortó a la mitad. Intenta de nuevo."
            else -> "No hubo respuesta del servidor."
        }
    }

    /** La misma causa de [fallaDeRed], en palabras simples (sin "servidor" ni siglas). */
    fun redSimple(mensaje: String?): String? {
        val m = mensaje ?: return null
        if (!m.startsWith("Sin respuesta") && !m.startsWith("Sin conexión")) return null
        return when {
            "UnknownHostException" in m -> "El teléfono no tiene internet o la red no encuentra la dirección."
            "SocketTimeoutException" in m || "timeout" in m.lowercase() -> "La conexión tardó demasiado en responder. Puede estar lenta o débil."
            "SSLHandshakeException" in m || "CertPathValidatorException" in m || "SSLPeerUnverified" in m || "SSLException" in m ->
                "La conexión segura fue rechazada. Revisa la fecha y hora del teléfono, o si la red Wi-Fi pide iniciar sesión."
            "ConnectException" in m || "ECONNREFUSED" in m || "NoRouteToHost" in m -> "No se pudo conectar: la red lo bloquea o el servicio no está disponible ahora."
            "ProtocolException" in m -> "Llegó una respuesta inesperada."
            "SocketException" in m || "EOFException" in m || "reset" in m.lowercase() -> "La conexión se cortó a la mitad. Intenta de nuevo."
            else -> "No hubo respuesta."
        }
    }

    /** Mensaje simple para acciones en línea (modo equipo, chats): sin nombres de servicios. */
    fun simple(mensaje: String?, codigo: Int? = null): String {
        redSimple(mensaje)?.let { return it }
        val m = (mensaje ?: "").lowercase()
        return when {
            codigo == -1 -> "No hubo respuesta. Revisa tu conexión e intenta de nuevo."
            codigo == 429 -> "Hiciste muchas acciones seguidas. Espera unos minutos y vuelve a intentarlo."
            codigo == 401 -> "Tu sesión venció. Vuelve a iniciar sesión."
            codigo == 403 || "42501" in m || "row-level security" in m || "permission denied" in m ->
                "No tienes permiso para esta acción con tu rol actual."
            codigo != null && codigo >= 500 -> "Hay un problema en este momento. Intenta de nuevo en unos minutos."
            else -> "Algo no funcionó. Intenta de nuevo."
        }
    }

    fun paraPersona(mensaje: String?, codigo: Int? = null): String {
        redSimple(mensaje)?.let { return "$it Tus datos quedan guardados y se enviarán después." }
        return when {
            codigo == -1 -> "No hubo respuesta. Tus datos quedan guardados y se enviarán después."
            codigo == 429 -> "Hiciste muchas acciones seguidas. Espera unos minutos y vuelve a intentarlo."
            codigo == 401 -> "Tu sesión venció. Se renovará sola al volver a sincronizar."
            codigo == 403 -> "El envío fue rechazado por permisos. Tus datos quedan guardados."
            codigo != null && codigo >= 500 -> "Hay un problema en este momento. Se volverá a intentar automáticamente."
            else -> "No pudimos enviar tus datos todavía. Se volverá a intentar automáticamente."
        }
    }

    /**
     * Detalle técnico para mostrar bajo «Ver detalle del error». En la versión publicada se ocultan las
     * direcciones de los servidores; en las compilaciones de prueba se muestra completo.
     */
    fun detalle(mensaje: String?, completo: Boolean): String? {
        val m = mensaje?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (completo) return m
        return m.replace(Regex("""https?://[^\s/"]+"""), "https://servidor")
            .replace(Regex("""[a-z0-9-]+\.(supabase\.co|googleapis\.com|firebaseio\.com|firebaseapp\.com)"""), "servidor")
    }

    fun explicar(mensaje: String?, codigo: Int? = null): String {
        val m = (mensaje ?: "").lowercase()
        val esSupabase = m.startsWith("supabase")
        return when {
            fallaDeRed(mensaje) != null -> fallaDeRed(mensaje)!! + " Detalle: " + mensaje!!.take(220)
            codigo == -1 -> "Sin respuesta del servidor. Detalle: ${mensaje?.take(220) ?: "desconocido"}"
            // ----- Supabase -----
            esSupabase && "anonymous" in m && ("disabled" in m || "provider" in m) ->
                "Supabase: activa «Allow anonymous sign-ins» en Authentication → Sign In / Providers."
            esSupabase && ("invalid api key" in m || "no api key" in m || "unregistered api key" in m) ->
                "Supabase: la clave no es válida. Copia la publishable (o anon) key desde Project Settings → API Keys."
            esSupabase && ("pgrst205" in m || "could not find the table" in m || "does not exist" in m || "pgrst202" in m) ->
                "Supabase: faltan las tablas. Ejecuta backend/supabase/schema.sql completo en el SQL Editor."
            esSupabase && "row-level security" in m ->
                "Supabase: un registro ya se había subido con otra sesión de este teléfono. Vuelve a sincronizar: la app le asigna un id nuevo."
            esSupabase && ("42501" in m || "permission denied" in m) ->
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
            else -> "Error: ${mensaje?.take(300) ?: "desconocido"}"
        }
    }
}
