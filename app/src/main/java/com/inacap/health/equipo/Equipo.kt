package com.inacap.health.equipo

import android.content.Context
import androidx.core.content.edit
import com.inacap.health.HealthApp
import com.inacap.health.cuenta.ReglasCuenta
import com.inacap.health.data.remote.ErrorNube
import com.inacap.health.data.remote.SupabaseCliente
import com.inacap.health.util.Saneamiento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

enum class RolEquipo(val clave: String, val nombre: String) {
    TUTOR("tutor", "Tutor par"),
    DOCENTE("docente", "Docente supervisor"),
    PROFESIONAL("bienestar", "Profesional de salud");

    companion object {
        fun de(clave: String?) = entries.firstOrNull { it.clave == clave }
    }
}

data class ConversacionEquipo(
    val id: String, val tema: String, val estado: String, val tipo: String,
    val tutorId: String?, val profesionalId: String?, val actualizadaEn: String
)
data class MensajeEquipo(val id: String, val rol: String, val texto: String, val creadoEn: String)
data class DerivacionEquipo(val id: String, val canal: String, val codigo: String, val fecha: String, val estado: String, val conContacto: Boolean)
data class Supervision(val accion: String, val nota: String?, val fecha: String)

/** Reglas de la sesión del equipo (lógica pura). */
object SesionEquipoReglas {
    const val INACTIVIDAD_MS = 30 * 60_000L          // 30 minutos sin uso
    const val MAXIMA_MS = 12 * 60 * 60_000L          // 12 horas desde que inició sesión

    fun vencida(iniciadaEn: Long, ultimaActividad: Long, ahora: Long): Boolean =
        iniciadaEn <= 0 || ahora - ultimaActividad > INACTIVIDAD_MS || ahora - iniciadaEn > MAXIMA_MS
}

/**
 * Modo equipo: tutores, docentes y profesionales usan la misma app con su cuenta.
 * Todos los permisos los decide el servidor según el rol (RLS y funciones).
 */
class EquipoRepositorio(context: Context, syncInyectado: com.inacap.health.data.sync.SyncManager? = null) {
    private val sync by lazy { syncInyectado ?: (context.applicationContext as HealthApp).sync }
    private val prefs = context.getSharedPreferences("equipo_prefs", Context.MODE_PRIVATE)
    private val sesion get() = sync.sesion

    private val _activo = MutableStateFlow(rol != null)
    val activo: StateFlow<Boolean> = _activo.asStateFlow()

    val rol: RolEquipo? get() = RolEquipo.de(prefs.getString("rol", null))
    val nombre: String get() = prefs.getString("nombre", "") ?: ""
    val email: String get() = prefs.getString("email", "") ?: ""
    val miId: String? get() = sesion.sbUid
    val disponible: Boolean get() = sync.clienteSql != null
    var aceptoConfidencialidad: Boolean
        get() = prefs.getBoolean("confidencialidad", false)
        set(v) = prefs.edit { putBoolean("confidencialidad", v) }

    private fun sql(): SupabaseCliente = sync.clienteSql ?: throw ErrorNube(0, "Sin servidor configurado")
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ---------- Sesión ----------

    /** @return null si entró; si no, el mensaje de error. */
    suspend fun iniciarSesion(email: String, contrasena: String): String? = withContext(Dispatchers.IO) {
        try {
            val sb = sql()
            sesion.modoEquipo = true      // desde ya: nada de estudiante se envía con esta sesión
            sb.iniciarSesion(email.trim(), contrasena)
            val rol = RolEquipo.de(sb.rpc("rol_actual").trim().trim('"').takeIf { it != "null" })
            if (rol == null) {
                sb.cerrarSesion(); sesion.limpiarSesiones(); sesion.modoEquipo = false
                return@withContext "Tu cuenta aún no tiene un rol del equipo. Pide a un administrador que te lo asigne."
            }
            val fila = sb.seleccionar("personal", "select=nombre_visible&user_id=eq.${enc(sesion.sbUid ?: "")}")
            val ahora = System.currentTimeMillis()
            sesion.modoEquipo = true
            prefs.edit(commit = true) {
                putString("rol", rol.clave)
                putString("nombre", fila.optJSONObject(0)?.optString("nombre_visible") ?: email)
                putString("email", email.trim())
                putLong("iniciada", ahora); putLong("actividad", ahora)
            }
            _activo.value = true
            null
        } catch (e: Exception) {
            if (rol == null) sesion.modoEquipo = false
            ReglasCuenta.mensaje(e)
        }
    }

    /** Crea una cuenta con correo (sin rol: luego el administrador se lo asigna). */
    suspend fun crearCuentaEquipo(email: String, contrasena: String): String = withContext(Dispatchers.IO) {
        try {
            val sb = sql()
            sb.registrarConCorreo(email.trim(), contrasena)
            "Cuenta creada. Confirma tu correo si te llega un enlace y pide a un administrador que te asigne tu rol."
        } catch (e: Exception) { ReglasCuenta.mensaje(e) }
    }

    fun registrarActividad() = prefs.edit { putLong("actividad", System.currentTimeMillis()) }

    /** Para los toques en pantalla: guarda como máximo una vez cada 30 s. */
    fun registrarToque(ahora: Long = System.currentTimeMillis()) {
        if (ahora - prefs.getLong("actividad", 0) > 30_000) prefs.edit { putLong("actividad", ahora) }
    }

    fun sesionVencida(ahora: Long = System.currentTimeMillis()) =
        rol != null && SesionEquipoReglas.vencida(prefs.getLong("iniciada", 0), prefs.getLong("actividad", 0), ahora)

    fun minutosRestantes(ahora: Long = System.currentTimeMillis()): Long {
        val porInactividad = prefs.getLong("actividad", 0) + SesionEquipoReglas.INACTIVIDAD_MS - ahora
        val porMaximo = prefs.getLong("iniciada", 0) + SesionEquipoReglas.MAXIMA_MS - ahora
        return (minOf(porInactividad, porMaximo) / 60_000).coerceAtLeast(0)
    }

    suspend fun cerrarSesion() {
        withContext(Dispatchers.IO) { runCatching { sql().cerrarSesion() } }
        sesion.limpiarSesiones()
        sesion.modoEquipo = false
        val confid = aceptoConfidencialidad
        prefs.edit(commit = true) { clear(); putBoolean("confidencialidad", confid) }
        _activo.value = false
    }

    // ---------- Solicitudes y chats ----------

    private fun conversacionesDe(a: JSONArray) = (0 until a.length()).map { i ->
        a.getJSONObject(i).let {
            ConversacionEquipo(
                it.getString("id"), it.getString("tema"), it.getString("estado"), it.optString("tipo", "orientacion"),
                it.optString("tutor_id").takeIf { s -> s.isNotBlank() && s != "null" },
                it.optString("profesional_id").takeIf { s -> s.isNotBlank() && s != "null" },
                it.getString("actualizada_en")
            )
        }
    }

    suspend fun conversaciones(): List<ConversacionEquipo> = withContext(Dispatchers.IO) {
        conversacionesDe(sql().seleccionar("conversaciones", "select=id,tema,estado,tipo,tutor_id,profesional_id,actualizada_en&order=actualizada_en.desc&limit=200"))
    }

    suspend fun mensajes(id: String): List<MensajeEquipo> = withContext(Dispatchers.IO) {
        val a = sql().seleccionar("mensajes", "select=id,rol_autor,texto,creado_en&conversacion_id=eq.${enc(id)}&order=creado_en.asc")
        (0 until a.length()).map { i -> a.getJSONObject(i).let { MensajeEquipo(it.getString("id"), it.getString("rol_autor"), it.getString("texto"), it.getString("creado_en")) } }
    }

    suspend fun enviar(id: String, texto: String) = withContext(Dispatchers.IO) {
        registrarActividad()
        val limpio = Saneamiento.texto(texto, 1000)
        if (limpio.isEmpty()) return@withContext
        val rolAutor = when (rol) { RolEquipo.PROFESIONAL -> "profesional"; RolEquipo.DOCENTE -> "docente"; else -> "tutor" }
        sql().insertar("mensajes", JSONObject().put("conversacion_id", id).put("rol_autor", rolAutor).put("texto", limpio))
    }

    suspend fun tomar(id: String) = withContext(Dispatchers.IO) { sql().rpc("tomar_conversacion", JSONObject().put("p_id", id)) }

    suspend fun cambiarEstado(id: String, estado: String) = withContext(Dispatchers.IO) {
        registrarActividad()
        sql().rpc("cambiar_estado_conversacion", JSONObject().put("p_id", id).put("p_estado", estado))
    }

    suspend fun supervisar(id: String, accion: String, nota: String?) = withContext(Dispatchers.IO) {
        registrarActividad()
        sql().rpc("supervisar", JSONObject().put("p_id", id).put("p_accion", accion).put("p_nota", nota?.let { Saneamiento.texto(it, 1000) } ?: JSONObject.NULL))
    }

    suspend fun bitacora(id: String): List<Supervision> = withContext(Dispatchers.IO) {
        val a = sql().seleccionar("supervision", "select=accion,nota,creada_en&conversacion_id=eq.${enc(id)}&order=creada_en.asc")
        (0 until a.length()).map { i -> a.getJSONObject(i).let { Supervision(it.getString("accion"), it.optString("nota").takeIf { n -> n.isNotBlank() && n != "null" }, it.getString("creada_en")) } }
    }

    suspend fun derivaciones(): List<DerivacionEquipo> = withContext(Dispatchers.IO) {
        val a = sql().seleccionar("derivaciones", "select=id,canal,codigo,fecha,estado,contacto_cifrado&order=fecha.desc&limit=200")
        (0 until a.length()).map { i -> a.getJSONObject(i).let {
            DerivacionEquipo(it.getString("id"), it.getString("canal"), it.getString("codigo"), it.getString("fecha"), it.getString("estado"), !it.isNull("contacto_cifrado"))
        } }
    }

    suspend fun gestionarDerivacion(id: String, estado: String) = withContext(Dispatchers.IO) {
        sql().rpc("gestionar_derivacion", JSONObject().put("p_id", id).put("p_estado", estado))
    }

    /** Abre (o retoma) el chat de atención de una derivación. Devuelve el id de la conversación. */
    suspend fun iniciarAtencion(derivacionId: String): String = withContext(Dispatchers.IO) {
        sql().rpc("iniciar_atencion", JSONObject().put("p_derivacion", derivacionId)).trim().trim('"')
    }
}
