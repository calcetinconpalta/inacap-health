package com.inacap.health.data.remote

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Base NoSQL (Firebase Firestore) vía su API REST.
 * Guarda eventos de uso de la app (alto volumen, esquema flexible).
 * Inicia sesión anónima en Firebase Auth; las reglas de seguridad solo
 * permiten crear/borrar documentos propios y leer al administrador.
 */
class FirestoreCliente(
    private val apiKey: String,
    private val projectId: String,
    private val sesion: SesionNube,
    private val urlFirestore: String = "https://firestore.googleapis.com/v1",
    private val urlIdentity: String = "https://identitytoolkit.googleapis.com/v1",
    private val urlSecureToken: String = "https://securetoken.googleapis.com/v1",
    /** X-Android-Package / X-Android-Cert: necesarios si la API key está restringida a la app. */
    private val encabezadosApp: Map<String, String> = emptyMap()
) {
    private fun enviar(
        metodo: String, url: String, headers: Map<String, String> = emptyMap(),
        cuerpo: String? = null, contentType: String = "application/json"
    ) = Http.enviar(metodo, url, encabezadosApp + headers, cuerpo, contentType)

    private val baseDocs = "$urlFirestore/projects/$projectId/databases/(default)/documents"

    fun asegurarSesion(): String {
        val actual = sesion.fbIdToken
        if (actual != null && System.currentTimeMillis() < sesion.fbExpiraEn - 60_000) return actual

        sesion.fbRefreshToken?.let { refresh ->
            val cuerpo = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refresh, "UTF-8")
            val r = enviar(
                "POST", "$urlSecureToken/token?key=$apiKey",
                cuerpo = cuerpo, contentType = "application/x-www-form-urlencoded"
            )
            if (r.ok) {
                val j = JSONObject(r.cuerpo)
                return guardar(
                    j.getString("id_token"), j.optString("refresh_token"),
                    j.optString("expires_in", "3600"), j.optString("user_id")
                )
            }
            if (r.codigo != 400 && r.codigo != 401 && r.codigo != 403) {
                Http.exigirOk(r, "Firebase refresh", deConfiguracion = true)
            }
        }

        val r = enviar(
            "POST", "$urlIdentity/accounts:signUp?key=$apiKey",
            cuerpo = JSONObject().put("returnSecureToken", true).toString()
        )
        Http.exigirOk(r, "Firebase inicio anónimo", deConfiguracion = true)
        val j = JSONObject(r.cuerpo)
        return guardar(j.getString("idToken"), j.optString("refreshToken"), j.optString("expiresIn", "3600"), j.optString("localId"))
    }

    private fun guardar(id: String, refresh: String, expira: String, uid: String): String {
        sesion.fbIdToken = id
        if (refresh.isNotBlank()) sesion.fbRefreshToken = refresh
        sesion.fbExpiraEn = System.currentTimeMillis() + (expira.toLongOrNull() ?: 3600) * 1000
        if (uid.isNotBlank()) sesion.fbUid = uid
        return id
    }

    private fun auth(token: String) = mapOf("Authorization" to "Bearer $token")

    private fun conReintentoToken(accion: (String) -> HttpRespuesta): HttpRespuesta {
        var r = accion(asegurarSesion())
        if (r.codigo == 401) {
            sesion.fbIdToken = null
            sesion.fbExpiraEn = 0
            r = accion(asegurarSesion())
        }
        return r
    }

    /**
     * Crea el documento coleccion/[docId]. Si ya existe (409) se considera
     * enviado: así un reintento nunca genera duplicados.
     */
    fun crearDocumento(coleccion: String, docId: String, datos: JSONObject) {
        val r = conReintentoToken { token ->
            val doc = JSONObject(datos.toString()).put("uid", sesion.fbUid ?: "")
            enviar(
                "POST", "$baseDocs/$coleccion?documentId=" + URLEncoder.encode(docId, "UTF-8"),
                auth(token), JSONObject().put("fields", FirestoreCodec.aCampos(doc)).toString()
            )
        }
        if (r.codigo == 409) return
        Http.exigirOk(r, "Firestore crear $coleccion")
    }

    /** Borra todos los eventos del usuario y su cuenta anónima de Firebase. */
    fun eliminarMisDatos() {
        if (sesion.fbRefreshToken == null && sesion.fbIdToken == null) return
        val token = asegurarSesion()
        val uid = sesion.fbUid ?: return
        while (true) {
            val consulta = JSONObject().put(
                "structuredQuery", JSONObject()
                    .put("from", JSONArray().put(JSONObject().put("collectionId", "eventos")))
                    .put(
                        "where", JSONObject().put(
                            "fieldFilter", JSONObject()
                                .put("field", JSONObject().put("fieldPath", "uid"))
                                .put("op", "EQUAL")
                                .put("value", JSONObject().put("stringValue", uid))
                        )
                    )
                    .put("limit", 200)
            )
            val r = Http.exigirOk(
                enviar("POST", "$baseDocs:runQuery", auth(token), consulta.toString()),
                "Firestore consulta"
            )
            val resultados = JSONArray(r.cuerpo)
            val nombres = (0 until resultados.length())
                .mapNotNull { resultados.getJSONObject(it).optJSONObject("document")?.optString("name") }
                .filter { it.isNotBlank() }
            if (nombres.isEmpty()) break
            nombres.forEach { nombre ->
                val d = enviar("DELETE", "$urlFirestore/$nombre", auth(token))
                if (d.codigo != 404) Http.exigirOk(d, "Firestore borrar")
            }
        }
        val borrarCuenta = enviar(
            "POST", "$urlIdentity/accounts:delete?key=$apiKey",
            cuerpo = JSONObject().put("idToken", token).toString()
        )
        if (borrarCuenta.codigo != 400) Http.exigirOk(borrarCuenta, "Firebase borrar cuenta")
    }
}

/** Convierte JSON simple al formato tipado de la API REST de Firestore. */
object FirestoreCodec {
    /** Campos con este nombre (epoch ms) se guardan como timestamp de Firestore. */
    const val CAMPO_FECHA = "ts"

    fun aCampos(obj: JSONObject): JSONObject {
        val campos = JSONObject()
        obj.keys().forEach { k ->
            val v = obj.get(k)
            campos.put(k, if (k == CAMPO_FECHA && v is Number) timestamp(v.toLong()) else aValor(v))
        }
        return campos
    }

    fun aValor(v: Any?): JSONObject = when (v) {
        null, JSONObject.NULL -> JSONObject().put("nullValue", JSONObject.NULL)
        is Boolean -> JSONObject().put("booleanValue", v)
        is Int, is Long, is Short, is Byte -> JSONObject().put("integerValue", v.toString())
        is Float, is Double -> JSONObject().put("doubleValue", (v as Number).toDouble())
        is JSONObject -> JSONObject().put("mapValue", JSONObject().put("fields", aCampos(v)))
        is JSONArray -> JSONObject().put(
            "arrayValue",
            JSONObject().put("values", JSONArray().apply { for (i in 0 until v.length()) put(aValor(v.get(i))) })
        )
        else -> JSONObject().put("stringValue", v.toString())
    }

    fun timestamp(epochMs: Long): JSONObject {
        val formato = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return JSONObject().put("timestampValue", formato.format(Date(epochMs)))
    }
}
