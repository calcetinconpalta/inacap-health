package com.inacap.health

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Servidor HTTP local que imita las APIs REST de Supabase (auth + PostgREST),
 * Firebase Auth y Firestore. Guarda cada petición para revisarla en las pruebas.
 */
class NubeFalsa : AutoCloseable {
    data class Peticion(val metodo: String, val ruta: String, val query: String, val cuerpo: String, val auth: String?)

    val peticiones = CopyOnWriteArrayList<Peticion>()
    /** Documentos de Firestore que "existen" (por id). */
    val documentos: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet())
    /** Si es true, todo responde 503 (servidor caído). */
    @Volatile var caida = false
    /** Simula errores de configuración del proyecto. */
    @Volatile var faltaEsquema = false
    @Volatile var anonimoDesactivado = false
    @Volatile var reglasFirestoreSinPublicar = false

    // --- Cuentas con correo (Supabase Auth) ---
    /** Si es true, agregar un correo exige confirmarlo con un enlace. */
    @Volatile var confirmacionRequerida = false
    /** Correo ya confirmado de la cuenta actual ("" = anónima). */
    @Volatile var emailConfirmado = ""
    /** Cuentas existentes: correo -> contraseña. */
    val cuentas = Collections.synchronizedMap(mutableMapOf<String, String>())
    /** Filas que devuelve la base al restaurar. */
    @Volatile var registrosRemotos = JSONArray()
    @Volatile var scoffRemotos = JSONArray()
    /** Respuestas a medida: "GET /rest/v1/conversaciones" o "POST /rest/v1/rpc/rol_actual" -> cuerpo JSON. */
    val respuestas = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** Ids que en la nube pertenecen a OTRA sesión: un upsert con ellos falla por RLS (como Supabase). */
    val idsDeOtraSesion: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet())

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { manejar(it) }
        start()
    }

    val base: String get() = "http://127.0.0.1:${server.address.port}"

    fun de(metodo: String, prefijoRuta: String) =
        peticiones.filter { it.metodo == metodo && it.ruta.startsWith(prefijoRuta) }

    private fun manejar(ex: HttpExchange) {
        val cuerpo = ex.requestBody.readBytes().toString(Charsets.UTF_8)
        val ruta = ex.requestURI.rawPath
        val query = ex.requestURI.rawQuery ?: ""
        peticiones += Peticion(ex.requestMethod, ruta, query, cuerpo, ex.requestHeaders.getFirst("Authorization"))

        val (codigo, respuesta) = if (caida) 503 to """{"error":"caida"}""" else when {
            anonimoDesactivado && ruta == "/auth/v1/signup" ->
                422 to """{"code":422,"error_code":"anonymous_provider_disabled","msg":"Anonymous sign-ins are disabled"}"""
            faltaEsquema && ruta.startsWith("/rest/v1/") ->
                404 to """{"code":"PGRST205","message":"Could not find the table 'public.usuarios' in the schema cache"}"""
            reglasFirestoreSinPublicar && ruta.contains("/documents/eventos") && ex.requestMethod == "POST" ->
                403 to """{"error":{"code":403,"message":"Missing or insufficient permissions.","status":"PERMISSION_DENIED"}}"""
            ex.requestMethod == "POST" && ruta.startsWith("/rest/v1/") && !ruta.startsWith("/rest/v1/rpc/") &&
                synchronized(idsDeOtraSesion) { idsDeOtraSesion.any { cuerpo.contains("\"$it\"") } } ->
                403 to """{"code":"42501","details":null,"hint":null,"message":"new row violates row-level security policy (USING expression) for table \"${ruta.substringAfterLast('/')}\""}"""
            respuestas.containsKey("${ex.requestMethod} $ruta") -> 200 to respuestas.getValue("${ex.requestMethod} $ruta")
            ruta == "/auth/v1/token" && query.contains("grant_type=password") -> {
                val j = JSONObject(cuerpo)
                if (cuentas[j.optString("email")] == j.optString("password"))
                    200 to """{"access_token":"sb-cuenta","refresh_token":"sb-refresco-cuenta","expires_in":3600,"user":{"id":"$CUENTA_UID","email":"${j.optString("email")}","is_anonymous":false}}"""
                else 400 to """{"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}"""
            }
            ruta == "/auth/v1/signup" || ruta == "/auth/v1/token" ->
                200 to """{"access_token":"sb-acceso","refresh_token":"sb-refresco","expires_in":3600,"user":{"id":"$SB_UID","email":"$emailConfirmado","is_anonymous":${emailConfirmado.isEmpty()}}}"""
            ruta == "/auth/v1/user" && ex.requestMethod == "GET" ->
                200 to """{"id":"$SB_UID","email":"$emailConfirmado","is_anonymous":${emailConfirmado.isEmpty()}}"""
            ruta == "/auth/v1/user" && ex.requestMethod == "PUT" -> {
                val j = JSONObject(cuerpo)
                when {
                    j.has("email") && cuentas.containsKey(j.getString("email")) ->
                        422 to """{"code":422,"error_code":"email_exists","msg":"A user with this email address has already been registered"}"""
                    j.has("email") && confirmacionRequerida ->
                        200 to """{"id":"$SB_UID","email":"","new_email":"${j.getString("email")}","is_anonymous":true}"""
                    j.has("email") -> { emailConfirmado = j.getString("email"); 200 to """{"id":"$SB_UID","email":"$emailConfirmado","is_anonymous":false}""" }
                    j.has("password") && emailConfirmado.isEmpty() ->
                        422 to """{"code":422,"error_code":"validation_failed","msg":"Updating password of an anonymous user is not possible"}"""
                    else -> { cuentas[emailConfirmado] = j.getString("password"); 200 to """{"id":"$SB_UID","email":"$emailConfirmado","is_anonymous":false}""" }
                }
            }
            ruta == "/auth/v1/recover" || ruta == "/auth/v1/logout" -> 200 to "{}"
            ruta == "/rest/v1/registros_biometricos" && ex.requestMethod == "GET" -> 200 to registrosRemotos.toString()
            ruta == "/rest/v1/resultados_scoff" && ex.requestMethod == "GET" -> 200 to scoffRemotos.toString()
            ruta == "/rest/v1/rpc/eliminar_mis_datos" -> 204 to ""
            ruta.startsWith("/rest/v1/") -> (if (ex.requestMethod == "DELETE") 204 else 201) to ""
            ruta.endsWith("/accounts:signUp") ->
                200 to """{"idToken":"fb-id","refreshToken":"fb-refresco","expiresIn":"3600","localId":"$FB_UID"}"""
            ruta.endsWith("/accounts:delete") -> 200 to "{}"
            ruta.endsWith("/documents:runQuery") -> {
                val docs = JSONArray()
                synchronized(documentos) {
                    documentos.forEach { id ->
                        docs.put(JSONObject().put("document", JSONObject().put("name", "projects/p/databases/(default)/documents/eventos/$id")))
                    }
                }
                if (docs.length() == 0) docs.put(JSONObject().put("readTime", "2026-01-01T00:00:00Z"))
                200 to docs.toString()
            }
            ruta.contains("/documents/eventos") && ex.requestMethod == "POST" -> {
                val id = query.split("&").first { it.startsWith("documentId=") }.substringAfter("=").let { URLDecoder.decode(it, "UTF-8") }
                if (documentos.add(id)) 200 to "{}" else 409 to """{"error":{"status":"ALREADY_EXISTS"}}"""
            }
            ruta.contains("/documents/eventos/") && ex.requestMethod == "DELETE" -> {
                documentos.remove(ruta.substringAfterLast('/'))
                200 to "{}"
            }
            else -> 404 to "{}"
        }
        val bytes = respuesta.toByteArray()
        ex.sendResponseHeaders(codigo, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
        ex.close()
    }

    override fun close() = server.stop(0)

    companion object {
        const val SB_UID = "11111111-1111-4111-8111-111111111111"
        const val FB_UID = "firebase-uid-123"
        const val CUENTA_UID = "22222222-2222-4222-8222-222222222222"
    }
}
