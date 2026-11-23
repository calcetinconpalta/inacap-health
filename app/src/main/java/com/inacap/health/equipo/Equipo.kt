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
    PROFESIONAL("bienestar", "Bienestar"),
    ADMIN("admin", "Administrador");

    companion object {
        fun de(clave: String?) = entries.firstOrNull { it.clave == clave }

        /** Lee la respuesta de rpc("rol_actual"): "\"tutor\"", "tutor" o "null". */
        fun deRespuesta(cuerpo: String): RolEquipo? = de(cuerpo.trim().trim('"').takeIf { it.isNotBlank() && it != "null" })
    }
}

data class ConversacionEquipo(
    val id: String, val tema: String, val estado: String, val tipo: String,
    val tutorId: String?, val profesionalId: String?, val actualizadaEn: String
)
data class MensajeEquipo(val id: String, val rol: String, val texto: String, val creadoEn: String)
data class DerivacionEquipo(val id: String, val canal: String, val codigo: String, val fecha: String, val estado: String, val conContacto: Boolean)
data class Supervision(val accion: String, val nota: String?, val fecha: String, val conversacionId: String? = null)

/** Cifras sin nombres para el monitoreo del tutor. */
data class Monitoreo(val abiertas: Int, val sinTutor: Int, val esperanRespuesta: Int, val porTema: Map<String, Int>)

data class MiembroEquipo(val userId: String, val email: String, val rol: String, val nombre: String, val activo: Boolean, val ultimoAcceso: String?)
data class IpBloqueada(val ip: String, val hasta: String, val motivo: String?)
data class EventoAuditoria(val fecha: String, val accion: String, val actor: String)
data class Seguridad(val rechazos24h: Int, val ips: List<IpBloqueada>, val auditoria: List<EventoAuditoria>)

/** Resumen para el administrador: solo cifras agregadas (las entrega dashboard_resumen). */
data class ResumenAdmin(
    val dias: Int, val usuarios: Int, val nuevos: Int, val activos: Int,
    val consultas: Int, val abiertas: Int, val sinTutor: Int, val derivaciones: Int,
    val tests: Int, val sugierenApoyo: Int, val pulsoPromedio: Int?, val pasosPromedio: Int
)

object MonitoreoReglas {
    /**
     * [ultimoAutor]: rol de quien escribió el último mensaje de cada conversación.
     * Una consulta abierta "espera respuesta" si no tiene mensajes o si lo último lo escribió el estudiante.
     */
    fun calcular(convs: List<ConversacionEquipo>, ultimoAutor: Map<String, String>): Monitoreo {
        val abiertas = convs.filter { it.tipo == "orientacion" && it.estado == "abierta" }
        return Monitoreo(
            abiertas = abiertas.size,
            sinTutor = abiertas.count { it.tutorId == null },
            esperanRespuesta = abiertas.count { (ultimoAutor[it.id] ?: "estudiante") == "estudiante" },
            porTema = abiertas.groupingBy { it.tema }.eachCount().toSortedMap()
        )
    }

    /** Lee la respuesta de dashboard_resumen. */
    fun resumenDe(j: JSONObject): ResumenAdmin {
        val u = j.optJSONObject("usuarios") ?: JSONObject()
        val o = j.optJSONObject("orientacion") ?: JSONObject()
        val s = j.optJSONObject("scoff") ?: JSONObject()
        val p = j.optJSONObject("pulso") ?: JSONObject()
        return ResumenAdmin(
            dias = j.optInt("periodo_dias", 30), usuarios = u.optInt("total"), nuevos = u.optInt("nuevos"), activos = u.optInt("activos"),
            consultas = o.optInt("conversaciones"), abiertas = o.optInt("abiertas"), sinTutor = o.optInt("sin_tutor"),
            derivaciones = j.optJSONObject("derivaciones")?.optInt("total") ?: 0,
            tests = s.optInt("tests"), sugierenApoyo = s.optInt("sugiere_derivacion"),
            pulsoPromedio = if (p.isNull("promedio")) null else p.optInt("promedio"),
            pasosPromedio = j.optJSONObject("pasos")?.optInt("promedio_diario") ?: 0
        )
    }

    fun seguridadDe(j: JSONObject): Seguridad {
        val ips = j.optJSONArray("ips_bloqueadas") ?: JSONArray()
        val aud = j.optJSONArray("auditoria") ?: JSONArray()
        return Seguridad(
            j.optInt("rechazos_24h"),
            (0 until ips.length()).map { i -> ips.getJSONObject(i).let { IpBloqueada(it.optString("ip"), it.optString("hasta"), it.optString("motivo").takeIf { m -> m.isNotBlank() && m != "null" }) } },
            (0 until aud.length()).map { i -> aud.getJSONObject(i).let { EventoAuditoria(it.optString("fecha"), it.optString("accion"), it.optString("actor", "Sistema")) } }
        )
    }
}

/** Reglas de la sesión del equipo (lógica pura). */
object SesionEquipoReglas {
    const val INACTIVIDAD_MS = 30 * 60_000L          // 30 minutos sin uso
    const val MAXIMA_MS = 12 * 60 * 60_000L          // 12 horas desde que inició sesión

    fun vencida(iniciadaEn: Long, ultimaActividad: Long, ahora: Long): Boolean =
        iniciadaEn <= 0 || ahora - ultimaActividad > INACTIVIDAD_MS || ahora - iniciadaEn > MAXIMA_MS

    /**
     * Pantalla «Iniciar sesión» al cambiar el modo equipo. Devuelve null si no se muestra, "" si se
     * muestra sin mensaje, o el mensaje que ya había (por ejemplo, el de sesión expirada).
     * Al salir del modo equipo SIEMPRE se vuelve a iniciar sesión, nunca a la app de estudiante directo.
     */
    fun accesoTrasCambio(estabaEnEquipo: Boolean, equipoActivo: Boolean, actual: String?): String? = when {
        equipoActivo -> null
        estabaEnEquipo -> actual ?: ""
        else -> actual
    }
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

    init {
        // Un inicio de sesión que quedó a medias (la app se cerró antes de confirmar el modo equipo)
        // deja la sesión marcada como del equipo pero sin rol: se limpia para no quedar en un estado raro.
        if (rol == null && sesion.modoEquipo) {
            sesion.limpiarSesiones()
            sesion.modoEquipo = false
        }
    }

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

    // El inicio de sesión (estudiantes y equipo) está en CuentaRepositorio.acceder: autentica,
    // consulta el rol en el servidor y, si es del equipo, llama a activar().

    /** Nombre visible del personal con la sesión actual (o el correo si no tiene). */
    internal fun nombreDelServidor(email: String): String = runCatching {
        sql().seleccionar("personal", "select=nombre_visible&user_id=eq.${enc(sesion.sbUid ?: "")}")
            .optJSONObject(0)?.optString("nombre_visible")?.takeIf { it.isNotBlank() && it != "null" }
    }.getOrNull() ?: email.trim()

    /** Abre el modo equipo con la sesión ya autenticada (el rol lo entregó el servidor). */
    fun activar(rol: RolEquipo, nombre: String, email: String) {
        val ahora = System.currentTimeMillis()
        sesion.modoEquipo = true
        aceptoConfidencialidad = true
        prefs.edit(commit = true) {
            putString("rol", rol.clave)
            putString("nombre", nombre)
            putString("email", email.trim())
            putLong("iniciada", ahora); putLong("actividad", ahora)
        }
        _activo.value = true
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

    /** Rol del último mensaje de cada conversación visible (la RLS limita a las que le tocan). */
    suspend fun ultimosAutores(): Map<String, String> = withContext(Dispatchers.IO) {
        val a = sql().seleccionar("mensajes", "select=conversacion_id,rol_autor&order=creado_en.desc&limit=1000")
        buildMap { for (i in 0 until a.length()) a.getJSONObject(i).let { putIfAbsent(it.getString("conversacion_id"), it.getString("rol_autor")) } }
    }

    /** Bitácora completa de supervisión (solo docentes y administradores, lo decide la RLS). */
    suspend fun bitacoraGeneral(): List<Supervision> = withContext(Dispatchers.IO) {
        val a = sql().seleccionar("supervision", "select=conversacion_id,accion,nota,creada_en&order=creada_en.desc&limit=100")
        (0 until a.length()).map { i -> a.getJSONObject(i).let {
            Supervision(it.getString("accion"), it.optString("nota").takeIf { n -> n.isNotBlank() && n != "null" }, it.getString("creada_en"),
                it.optString("conversacion_id").takeIf { c -> c.isNotBlank() && c != "null" })
        } }
    }

    suspend fun resumenAdmin(dias: Int = 30): ResumenAdmin = withContext(Dispatchers.IO) {
        MonitoreoReglas.resumenDe(JSONObject(sql().rpc("dashboard_resumen", JSONObject().put("dias", dias))))
    }

    suspend fun equipo(): List<MiembroEquipo> = withContext(Dispatchers.IO) {
        val a = JSONArray(sql().rpc("listar_equipo"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let {
            MiembroEquipo(it.getString("user_id"), it.optString("email"), it.optString("rol"), it.optString("nombre_visible"),
                it.optBoolean("activo"), it.optString("ultimo_acceso").takeIf { f -> f.isNotBlank() && f != "null" })
        } }
    }

    suspend fun seguridad(): Seguridad = withContext(Dispatchers.IO) {
        MonitoreoReglas.seguridadDe(JSONObject(sql().rpc("resumen_seguridad")))
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
