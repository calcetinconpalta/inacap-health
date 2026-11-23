package com.inacap.health.cuenta

import android.content.Context
import com.inacap.health.HealthApp
import com.inacap.health.data.AppDatabase
import com.inacap.health.data.sync.SyncManager
import com.inacap.health.data.Consentimiento
import com.inacap.health.data.PasosDia
import com.inacap.health.data.RegistroBiometrico
import com.inacap.health.data.ResultadoScoff
import com.inacap.health.data.TipoConsentimiento
import com.inacap.health.data.remote.ErrorNube
import com.inacap.health.data.remote.ExplicacionError
import com.inacap.health.data.remote.SesionNube
import com.inacap.health.equipo.EquipoRepositorio
import com.inacap.health.equipo.RolEquipo
import com.inacap.health.legal.TextosLegales
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.OffsetDateTime

sealed interface EstadoCuenta {
    /** La base SQL no está configurada en esta versión. */
    object NoDisponible : EstadoCuenta
    object Anonima : EstadoCuenta
    data class Pendiente(val email: String) : EstadoCuenta
    data class Registrada(val email: String) : EstadoCuenta
}

sealed interface ResultadoCuenta {
    object Listo : ResultadoCuenta
    data class ConfirmaCorreo(val email: String) : ResultadoCuenta
    data class Error(val mensaje: String) : ResultadoCuenta
}

/** Resultado del inicio de sesión único: el servidor decide si la cuenta es de estudiante o del equipo. */
sealed interface ResultadoAcceso {
    /** Cuenta sin rol: quedó como sesión de estudiante (con sus datos restaurados). */
    object Estudiante : ResultadoAcceso

    /**
     * Cuenta del equipo: ya autenticada, pero el modo equipo se abre recién cuando la persona
     * acepta el compromiso de confidencialidad ([CuentaRepositorio.confirmarEquipo]) o se
     * cancela ([CuentaRepositorio.cancelarEquipo], que vuelve a la sesión anterior).
     */
    data class Equipo(
        val rol: RolEquipo, val nombre: String, val email: String,
        internal val anterior: SesionNube.InstantaneaSql
    ) : ResultadoAcceso

    data class Error(val mensaje: String) : ResultadoAcceso
}

/** Validaciones y mensajes (lógica pura, probada en la JVM). */
object ReglasCuenta {
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
    fun emailValido(email: String) = EMAIL.matches(email.trim())
    fun contrasenaValida(p: String) = p.length >= 8 && p.any { it.isLetter() } && p.any { it.isDigit() }

    /** Traduce los errores de Supabase Auth a mensajes para la persona. */
    fun mensaje(e: Throwable): String {
        val t = (e.message ?: "").lowercase()
        val codigo = (e as? ErrorNube)?.codigo
        return when {
            codigo == -1 -> ExplicacionError.redSimple(e.message) ?: "No hubo respuesta. Revisa tu internet e intenta de nuevo."
            "invalid_credentials" in t || "invalid login" in t -> "Correo o contraseña incorrectos."
            "email_not_confirmed" in t || "not confirmed" in t -> "Primero confirma tu correo con el enlace que te enviamos."
            "email_exists" in t || "already been registered" in t || "already registered" in t || "user_already_exists" in t ->
                "Ese correo ya tiene una cuenta. Usa «Iniciar sesión»."
            "weak_password" in t -> "La contraseña es muy débil: usa al menos 8 caracteres con letras y números."
            "email_address_invalid" in t || "invalid format" in t -> "Revisa el correo: el formato no es válido."
            codigo == 429 || "rate limit" in t -> "Demasiados intentos. Espera unos minutos e intenta de nuevo."
            "anonymous" in t && "disabled" in t -> "Las cuentas no están disponibles en este momento. Intenta más tarde."
            codigo == 403 -> "La solicitud fue rechazada por seguridad. Revisa que el correo y la contraseña no tengan caracteres poco comunes e intenta de nuevo."
            else -> "No se pudo completar (error ${codigo ?: e.javaClass.simpleName}). Intenta de nuevo más tarde."
        }
    }

    fun fechaMs(iso: String): Long = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

    /** Cuenta sin rol que entró por «Soy del equipo» antes de aceptar los términos de estudiante. */
    const val CUENTA_SIN_ROL =
        "Esta cuenta no tiene un rol del equipo. Si eres estudiante, primero acepta los términos en la bienvenida y luego inicia sesión desde Perfil → Cuenta. Si eres del equipo, pide a un administrador que te asigne tu rol."

    /** Se abrió una sesión de estudiante con una cuenta que en realidad es del equipo. */
    const val CUENTA_ES_DEL_EQUIPO =
        "Esta cuenta es del equipo, no de estudiante. Vuelve a iniciar sesión para entrar al modo equipo."
}

/**
 * Cuenta opcional con correo (Supabase Auth).
 * - Crear cuenta: la cuenta anónima se convierte en cuenta con correo (mismo id, mismos datos).
 * - Iniciar sesión: en otro teléfono o tras reinstalar, recupera desde la nube lo que se guardó.
 */
class CuentaRepositorio(
    private val context: Context,
    private val sync: SyncManager = (context.applicationContext as HealthApp).sync,
    private val db: AppDatabase = (context.applicationContext as HealthApp).database,
    equipoInyectado: EquipoRepositorio? = null
) {
    private val equipo by lazy { equipoInyectado ?: (context.applicationContext as HealthApp).equipo }
    private val sesion = sync.sesion

    fun estado(): EstadoCuenta {
        if (sync.clienteSql == null) return EstadoCuenta.NoDisponible
        sesion.cuentaEmail?.let { return EstadoCuenta.Registrada(it) }
        sesion.emailPendiente?.let { return EstadoCuenta.Pendiente(it) }
        return EstadoCuenta.Anonima
    }

    private fun sql() = sync.clienteSql ?: throw ErrorNube(0, "Cuenta no disponible")

    /** Página «Correo confirmado · Vuelve a la app» (no el panel del equipo). */
    private fun urlConfirmado(): String? =
        context.getString(com.inacap.health.R.string.url_confirmado).takeIf { it.startsWith("https://") }

    private suspend fun <T> ejecutar(bloque: suspend () -> T): ResultadoCuenta = try {
        withContext(Dispatchers.IO) { bloque() }
        ResultadoCuenta.Listo
    } catch (e: Exception) {
        ResultadoCuenta.Error(ReglasCuenta.mensaje(e))
    }

    /** Paso 1: agrega el correo a la cuenta anónima. Si el servidor pide confirmar, se envía un enlace. */
    suspend fun crearCuenta(email: String, contrasena: String): ResultadoCuenta {
        var res: ResultadoCuenta = ResultadoCuenta.Listo
        val r = ejecutar {
            val sb = sql()
            sb.asegurarSesion()
            sync.asegurarUsuarioRemoto()
            val u = sb.actualizarUsuario(JSONObject().put("email", email.trim()), redirigirA = urlConfirmado())
            if (u.optString("email").equals(email.trim(), true) && !u.optBoolean("is_anonymous", true)) {
                sb.actualizarUsuario(JSONObject().put("password", contrasena))
                sesion.cuentaEmail = email.trim(); sesion.emailPendiente = null
                activarRespaldo()
            } else {
                sesion.emailPendiente = email.trim()
                res = ResultadoCuenta.ConfirmaCorreo(email.trim())
            }
        }
        return if (r is ResultadoCuenta.Error) r else res
    }

    /** Paso 2 (si hubo que confirmar el correo): pone la contraseña. */
    suspend fun completarRegistro(contrasena: String): ResultadoCuenta {
        var res: ResultadoCuenta = ResultadoCuenta.Listo
        val r = ejecutar {
            val sb = sql()
            sesion.sbAccessToken = null; sesion.sbExpiraEn = 0
            sb.asegurarSesion()                      // renueva el token: ahora trae el correo
            val u = sb.usuarioActual()
            val email = u.optString("email")
            if (email.isBlank() || u.optBoolean("is_anonymous", true)) {
                res = ResultadoCuenta.ConfirmaCorreo(sesion.emailPendiente ?: "")
            } else {
                sb.actualizarUsuario(JSONObject().put("password", contrasena))
                sesion.cuentaEmail = email; sesion.emailPendiente = null
                activarRespaldo()
            }
        }
        return if (r is ResultadoCuenta.Error) r else res
    }

    /**
     * Inicio de sesión ÚNICO (estudiantes y equipo). Autentica y pregunta el rol al servidor:
     * - rol del equipo → [ResultadoAcceso.Equipo] (no se toca nada del estudiante).
     * - sin rol → sesión de estudiante: restaura sus datos y activa el respaldo.
     *   Si [permitirEstudiante] es false (entró por «Soy del equipo» sin aceptar los términos),
     *   se rechaza y se vuelve a la sesión anterior.
     * Mientras se consulta el rol, la sesión queda en modo equipo: nada de estudiante se envía con ella.
     */
    suspend fun acceder(email: String, contrasena: String, permitirEstudiante: Boolean = true): ResultadoAcceso =
        withContext(Dispatchers.IO) {
            val sb = try { sql() } catch (e: Exception) { return@withContext ResultadoAcceso.Error(ReglasCuenta.mensaje(e)) }
            val anterior = sesion.instantaneaSql()
            // Si este teléfono tenía una cuenta anónima de estudiante, se guarda su token: si la cuenta
            // nueva también es de estudiante, esa copia anónima en la nube se borra (lo local se vuelve a subir).
            val tokenAnonimo = if (!anterior.modoEquipo && anterior.email == null && anterior.refresh != null)
                runCatching { sb.asegurarSesion() }.getOrNull() else null
            val anteriorConToken = sesion.instantaneaSql()
            sesion.modoEquipo = true
            try {
                sb.iniciarSesion(email.trim(), contrasena)
                val rol = RolEquipo.deRespuesta(sb.rpc("rol_actual"))
                when {
                    rol != null -> ResultadoAcceso.Equipo(rol, equipo.nombreDelServidor(email), email.trim(), anteriorConToken)
                    !permitirEstudiante -> {
                        sb.cerrarSesion()
                        sesion.restaurarSql(anteriorConToken)
                        ResultadoAcceso.Error(ReglasCuenta.CUENTA_SIN_ROL)
                    }
                    else -> {
                        sesion.modoEquipo = false
                        if (tokenAnonimo != null && anterior.uid != sesion.sbUid) sb.eliminarDatosConToken(tokenAnonimo)
                        sesion.emailPendiente = null
                        restaurar()
                        activarRespaldo()
                        sync.registrarEvento("sesion_iniciada")
                        ResultadoAcceso.Estudiante
                    }
                }
            } catch (e: Exception) {
                // Contraseña incorrecta, sin red, etc.: se vuelve exactamente a la sesión que había.
                if (sesion.sbAccessToken != anteriorConToken.access) runCatching { sb.cerrarSesion() }
                sesion.restaurarSql(anteriorConToken)
                ResultadoAcceso.Error(ReglasCuenta.mensaje(e))
            }
        }

    /** La persona del equipo aceptó el compromiso: se abre el modo equipo. */
    fun confirmarEquipo(r: ResultadoAcceso.Equipo) = equipo.activar(r.rol, r.nombre, r.email)

    /** La persona del equipo no aceptó: se cierra esa sesión y se vuelve a la anterior (por ejemplo, la anónima). */
    suspend fun cancelarEquipo(r: ResultadoAcceso.Equipo) = withContext(Dispatchers.IO) {
        runCatching { sql().cerrarSesion() }
        sesion.restaurarSql(r.anterior)
    }

    /**
     * Revisa la sesión de estudiante con correo que ya existía (de versiones anteriores podía quedar una
     * cuenta del equipo como si fuera alumno). Si el servidor dice que es del equipo, se cierra esa sesión
     * de estudiante y devuelve true para pedir que vuelva a entrar (ahora al modo equipo).
     * Sin red o sin cuenta, no hace nada.
     */
    suspend fun revisarCuentaActual(): Boolean = withContext(Dispatchers.IO) {
        val sb = sync.clienteSql ?: return@withContext false
        if (sesion.modoEquipo || sesion.cuentaEmail == null) return@withContext false
        runCatching { RolEquipo.deRespuesta(sb.rpc("rol_actual")) }.getOrNull() ?: return@withContext false
        // Primero se corta el envío de datos de estudiante con esta cuenta y se vacía la bandeja.
        sesion.compartirNube = false
        db.syncPendienteDao().deleteAll()
        runCatching { sb.cerrarSesion() }
        sesion.limpiarSesiones()
        true
    }

    suspend fun recuperarContrasena(email: String): ResultadoCuenta = ejecutar {
        sql().recuperarContrasena(email.trim(), context.getString(com.inacap.health.R.string.url_restablecer).takeIf { it.startsWith("http") })
    }

    /** Cierra la sesión en el servidor. El borrado de datos locales lo hace PerfilViewModel. */
    suspend fun cerrarSesion() = withContext(Dispatchers.IO) { runCatching { sql().cerrarSesion() } }

    /** Con cuenta, el respaldo en la nube queda activado (consentimiento registrado). */
    private suspend fun activarRespaldo() {
                val perfil = db.perfilDao().getPerfilUnaVez()
        if (perfil != null && !perfil.compartirNube) db.perfilDao().upsertPerfil(perfil.copy(compartirNube = true))
        if (perfil == null || !perfil.compartirNube) {
            db.consentimientoDao().insert(Consentimiento(tipo = TipoConsentimiento.NUBE, version = TextosLegales.VERSION, aceptado = true))
        }
        sync.activarNube()
    }

    /** Trae a este teléfono lo que la cuenta tiene en la nube (sin duplicar). */
    suspend fun restaurar(): Int {
        val sb = sql()
                var n = 0
        val regs = sb.seleccionar("registros_biometricos", "select=id,tipo,imc,agua_ml,frecuencia_cardiaca,pasos,fuente,fecha&order=fecha.asc&limit=10000")
        for (i in 0 until regs.length()) {
            val r = regs.getJSONObject(i)
            val id = r.getString("id")
            val fecha = r.getString("fecha")
            when (r.getString("tipo")) {
                "pasos" -> {
                    val dia = fecha.take(10)
                    val pasos = r.optInt("pasos")
                    val local = db.pasosDiaDao().get(dia)
                    if (local == null || local.pasos < pasos) {
                        db.pasosDiaDao().upsert(PasosDia(fecha = dia, pasos = pasos, fuente = r.optString("fuente", "sensor"), uuid = local?.uuid ?: id)); n++
                    }
                }
                else -> if (db.registroBiometricoDao().getPorUuid(id) == null) {
                    db.registroBiometricoDao().insert(
                        RegistroBiometrico(
                            pesoKg = null, estaturaCm = null,
                            imc = if (r.isNull("imc")) null else r.getDouble("imc"),
                            aguaMl = if (r.isNull("agua_ml")) 0 else r.getInt("agua_ml"),
                            frecuenciaCardiaca = if (r.isNull("frecuencia_cardiaca")) null else r.getInt("frecuencia_cardiaca"),
                            createdAt = ReglasCuenta.fechaMs(fecha), uuid = id,
                            fuente = if (r.isNull("fuente")) null else r.getString("fuente")
                        )
                    ); n++
                }
            }
        }
        val scoff = sb.seleccionar("resultados_scoff", "select=id,puntaje,requiere_derivacion,fecha&order=fecha.asc&limit=1000")
        for (i in 0 until scoff.length()) {
            val r = scoff.getJSONObject(i)
            if (db.resultadoScoffDao().getPorUuid(r.getString("id")) == null) {
                db.resultadoScoffDao().insert(
                    ResultadoScoff(
                        puntaje = r.getInt("puntaje"), respuestas = "", requiereDerivacion = r.getBoolean("requiere_derivacion"),
                        createdAt = ReglasCuenta.fechaMs(r.getString("fecha")), uuid = r.getString("id")
                    )
                ); n++
            }
        }
        return n
    }
}
