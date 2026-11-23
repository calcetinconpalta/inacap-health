package com.inacap.health.data.remote

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Base SQL (Supabase / PostgreSQL) vía su API REST.
 *
 * Cada instalación inicia sesión de forma ANÓNIMA (sin correo ni nombre):
 * Supabase le asigna un UUID aleatorio y las políticas RLS hacen que cada
 * usuario solo pueda escribir y borrar sus propias filas.
 */
class SupabaseCliente(
    private val url: String,
    private val anonKey: String,
    private val sesion: SesionNube
) {
    // Sin sesión solo se envía "apikey": las claves nuevas de Supabase (sb_publishable_...)
    // no son JWT y no se aceptan en Authorization. Con sesión va el token del usuario.
    private fun headersBase(token: String? = null): Map<String, String> = buildMap {
        put("apikey", anonKey)
        if (token != null) put("Authorization", "Bearer $token")
        else if (anonKey.startsWith("eyJ")) put("Authorization", "Bearer $anonKey")
    }

    /** Devuelve un access token válido (renueva o crea la sesión anónima si hace falta). */
    fun asegurarSesion(): String {
        val actual = sesion.sbAccessToken
        if (actual != null && System.currentTimeMillis() < sesion.sbExpiraEn - 60_000) return actual

        sesion.sbRefreshToken?.let { refresh ->
            val r = Http.enviar(
                "POST", "$url/auth/v1/token?grant_type=refresh_token",
                headersBase(), JSONObject().put("refresh_token", refresh).toString()
            )
            if (r.ok) return guardarSesion(JSONObject(r.cuerpo))
            if (r.codigo != 400 && r.codigo != 401 && r.codigo != 403) {
                Http.exigirOk(r, "Supabase refresh", deConfiguracion = true)
            }
            // Una cuenta con correo (o del equipo) NUNCA pasa en silencio a una sesión anónima:
            // su sesión fue revocada (otro teléfono, contraseña cambiada…) y debe volver a ingresar.
            if (sesion.cuentaEmail != null || sesion.modoEquipo) {
                sesionCerrada(ReglasSesionUnica.MOTIVO_REVOCADA, "refresh HTTP ${r.codigo} ${r.cuerpo.take(200)}")
            }
            // Refresh inválido de una sesión anónima (usuario borrado o sesión expirada): se crea una nueva.
        }

        val r = Http.enviar(
            "POST", "$url/auth/v1/signup", headersBase(), JSONObject().put("data", JSONObject()).toString()
        )
        Http.exigirOk(r, "Supabase inicio anónimo", deConfiguracion = true)
        return guardarSesion(JSONObject(r.cuerpo))
    }

    internal fun guardarSesion(json: JSONObject): String {
        val token = json.getString("access_token")
        sesion.sbAccessToken = token
        sesion.sbRefreshToken = json.optString("refresh_token").ifBlank { sesion.sbRefreshToken }
        sesion.sbExpiraEn = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000
        json.optJSONObject("user")?.let { u ->
            u.optString("id").takeIf { it.isNotBlank() }?.let { sesion.sbUid = it }
            sesion.cuentaEmail = u.optString("email").takeIf { it.isNotBlank() && !u.optBoolean("is_anonymous", false) }
        }
        return token
    }

    /**
     * Se cerró la sesión de la cuenta desde el servidor. Se limpian los tokens y se apaga el envío de
     * datos (para no subir nada con una sesión anónima nueva); la interfaz vuelve a «Iniciar sesión».
     */
    private fun sesionCerrada(motivo: String, detalle: String): Nothing {
        sesion.limpiarSesiones()
        sesion.compartirNube = false
        sesion.marcarSesionCerrada(motivo)
        throw ErrorNube(401, "${ReglasSesionUnica.MARCA}: $detalle")
    }

    private fun conReintentoToken(accion: (String) -> HttpRespuesta): HttpRespuesta {
        var r = accion(asegurarSesion())
        if (r.codigo == 401 && ReglasSesionUnica.MARCA in r.cuerpo) {
            sesionCerrada(ReglasSesionUnica.MOTIVO_OTRO_TELEFONO, "HTTP 401 ${r.cuerpo.take(200)}")
        }
        if (r.codigo == 401) {
            sesion.sbAccessToken = null
            sesion.sbExpiraEn = 0
            r = accion(asegurarSesion())
        }
        return r
    }

    /**
     * Una sola sesión activa por cuenta: deja vigente la de este teléfono y el servidor cierra
     * las demás. Si el servidor aún no tiene esta función (404), no pasa nada.
     */
    fun registrarSesion() {
        runCatching { rpc("registrar_sesion") }
            .onFailure { if (it is ErrorNube && it.message?.contains(ReglasSesionUnica.MARCA) == true) throw it }
    }

    /** Inserta o actualiza (por id) una o varias filas. Idempotente: reenviar no duplica. */
    fun upsert(tabla: String, filas: JSONArray) {
        val r = conReintentoToken { token ->
            Http.enviar(
                "POST", "$url/rest/v1/$tabla?on_conflict=id",
                headersBase(token) + ("Prefer" to "resolution=merge-duplicates,return=minimal"),
                filas.toString()
            )
        }
        Http.exigirOk(r, "Supabase upsert $tabla")
    }

    fun eliminar(tabla: String, id: String) {
        val idSeguro = URLEncoder.encode(id, "UTF-8")
        val r = conReintentoToken { token ->
            Http.enviar(
                "DELETE", "$url/rest/v1/$tabla?id=eq.$idSeguro",
                headersBase(token) + ("Prefer" to "return=minimal")
            )
        }
        Http.exigirOk(r, "Supabase delete $tabla")
    }

    // ------------------------------------------------------------------
    // Cuenta con correo (opcional). La cuenta anónima se CONVIERTE en una cuenta con
    // correo: mantiene el mismo id, así que los datos ya enviados siguen siendo suyos.
    // ------------------------------------------------------------------

    /** Datos del usuario de la sesión actual (email, is_anonymous, new_email…). */
    fun usuarioActual(): JSONObject {
        val r = conReintentoToken { token -> Http.enviar("GET", "$url/auth/v1/user", headersBase(token)) }
        return JSONObject(Http.exigirOk(r, "Supabase usuario").cuerpo)
    }

    private fun redirigir(a: String?) = a?.takeIf { it.isNotBlank() }?.let { "?redirect_to=" + URLEncoder.encode(it, "UTF-8") } ?: ""

    /**
     * PUT /auth/v1/user: agrega correo o contraseña a la cuenta actual. [redirigirA] es la página
     * que abre el enlace de confirmación del correo (si no, abre la Site URL: el panel del equipo).
     */
    fun actualizarUsuario(datos: JSONObject, redirigirA: String? = null): JSONObject {
        val r = conReintentoToken { token -> Http.enviar("PUT", "$url/auth/v1/user${redirigir(redirigirA)}", headersBase(token), datos.toString()) }
        return JSONObject(Http.exigirOk(r, "Supabase actualizar usuario").cuerpo)
    }

    /** Inicia sesión con correo y contraseña (reemplaza la sesión anónima). */
    fun iniciarSesion(email: String, password: String) {
        val r = Http.enviar(
            "POST", "$url/auth/v1/token?grant_type=password", headersBase(),
            JSONObject().put("email", email).put("password", password).toString()
        )
        Http.exigirOk(r, "Supabase iniciar sesión")
        guardarSesion(JSONObject(r.cuerpo))
    }

    /** Crea una cuenta nueva con correo (personal del equipo). */
    fun registrarConCorreo(email: String, password: String, redirigirA: String? = null) {
        val r = Http.enviar("POST", "$url/auth/v1/signup${redirigir(redirigirA)}", headersBase(), JSONObject().put("email", email).put("password", password).toString())
        Http.exigirOk(r, "Supabase registro")
    }

    /** Envía el correo para crear una contraseña nueva. */
    fun recuperarContrasena(email: String, redirigirA: String?) {
        val destino = redirigirA?.takeIf { it.isNotBlank() }?.let { "?redirect_to=" + URLEncoder.encode(it, "UTF-8") } ?: ""
        val r = Http.enviar("POST", "$url/auth/v1/recover$destino", headersBase(), JSONObject().put("email", email).toString())
        Http.exigirOk(r, "Supabase recuperar contraseña")
    }

    /** Cierra la sesión en el servidor (el token deja de servir). */
    fun cerrarSesion() {
        val token = sesion.sbAccessToken ?: return
        runCatching { Http.enviar("POST", "$url/auth/v1/logout", headersBase(token)) }
    }

    /** Elimina los datos de una sesión anterior usando su token (al cambiar de cuenta anónima a correo). */
    fun eliminarDatosConToken(token: String) {
        runCatching {
            Http.enviar("POST", "$url/rest/v1/rpc/eliminar_mis_datos", headersBase(token), JSONObject().put("motivo", "eliminacion").toString())
        }
    }

    /** Consulta filas visibles para este usuario (RLS). [consulta] es la query de PostgREST. */
    fun seleccionar(tabla: String, consulta: String): JSONArray {
        val r = conReintentoToken { token -> Http.enviar("GET", "$url/rest/v1/$tabla?$consulta", headersBase(token)) }
        return JSONArray(Http.exigirOk(r, "Supabase select $tabla").cuerpo.ifBlank { "[]" })
    }

    /** Inserta una fila y devuelve la fila creada. */
    fun insertar(tabla: String, fila: JSONObject): JSONObject {
        val r = conReintentoToken { token ->
            Http.enviar("POST", "$url/rest/v1/$tabla", headersBase(token) + ("Prefer" to "return=representation"), fila.toString())
        }
        return JSONArray(Http.exigirOk(r, "Supabase insert $tabla").cuerpo).getJSONObject(0)
    }

    /** Llama a una función del servidor. */
    fun rpc(funcion: String, argumentos: JSONObject = JSONObject()): String {
        val r = conReintentoToken { token -> Http.enviar("POST", "$url/rest/v1/rpc/$funcion", headersBase(token), argumentos.toString()) }
        return Http.exigirOk(r, "Supabase rpc $funcion").cuerpo
    }

    /**
     * Borra todos los datos del usuario en la base SQL (y su cuenta anónima).
     * Lo hace la función eliminar_mis_datos() del servidor.
     */
    fun eliminarMisDatos(motivo: String) {
        if (sesion.sbRefreshToken == null && sesion.sbAccessToken == null) return // nunca envió nada
        val r = conReintentoToken { token ->
            Http.enviar(
                "POST", "$url/rest/v1/rpc/eliminar_mis_datos",
                headersBase(token), JSONObject().put("motivo", motivo).toString()
            )
        }
        Http.exigirOk(r, "Supabase eliminar_mis_datos")
    }
}
