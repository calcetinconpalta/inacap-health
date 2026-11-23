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
            codigo == -1 -> ExplicacionError.fallaDeRed(e.message) ?: "No hubo respuesta del servidor. Revisa tu internet e intenta de nuevo."
            "invalid_credentials" in t || "invalid login" in t -> "Correo o contraseña incorrectos."
            "email_not_confirmed" in t || "not confirmed" in t -> "Primero confirma tu correo con el enlace que te enviamos."
            "email_exists" in t || "already been registered" in t || "already registered" in t || "user_already_exists" in t ->
                "Ese correo ya tiene una cuenta. Usa «Iniciar sesión»."
            "weak_password" in t -> "La contraseña es muy débil: usa al menos 8 caracteres con letras y números."
            "email_address_invalid" in t || "invalid format" in t -> "Revisa el correo: el formato no es válido."
            codigo == 429 || "rate limit" in t -> "Demasiados intentos. Espera unos minutos e intenta de nuevo."
            "anonymous" in t && "disabled" in t -> "Las cuentas no están activadas en el servidor (inicio anónimo desactivado)."
            else -> "No se pudo completar (error ${codigo ?: e.javaClass.simpleName}). Intenta de nuevo más tarde."
        }
    }

    fun fechaMs(iso: String): Long = OffsetDateTime.parse(iso).toInstant().toEpochMilli()
}

/**
 * Cuenta opcional con correo (Supabase Auth).
 * - Crear cuenta: la cuenta anónima se convierte en cuenta con correo (mismo id, mismos datos).
 * - Iniciar sesión: en otro teléfono o tras reinstalar, recupera desde la nube lo que se guardó.
 */
class CuentaRepositorio(
    private val context: Context,
    private val sync: SyncManager = (context.applicationContext as HealthApp).sync,
    private val db: AppDatabase = (context.applicationContext as HealthApp).database
) {
    private val sesion = sync.sesion

    fun estado(): EstadoCuenta {
        if (sync.clienteSql == null) return EstadoCuenta.NoDisponible
        sesion.cuentaEmail?.let { return EstadoCuenta.Registrada(it) }
        sesion.emailPendiente?.let { return EstadoCuenta.Pendiente(it) }
        return EstadoCuenta.Anonima
    }

    private fun sql() = sync.clienteSql ?: throw ErrorNube(0, "Cuenta no disponible")

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
            val u = sb.actualizarUsuario(JSONObject().put("email", email.trim()))
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

    /** Inicia sesión y recupera los datos de la cuenta. */
    suspend fun iniciarSesion(email: String, contrasena: String): ResultadoCuenta = ejecutar {
        val sb = sql()
        // Si este teléfono tenía una cuenta anónima, su copia en la nube se borra
        // (lo que hay en el teléfono se vuelve a subir, ahora a tu cuenta).
        val tokenAnonimo = if (sesion.cuentaEmail == null && sesion.sbRefreshToken != null) runCatching { sb.asegurarSesion() }.getOrNull() else null
        val uidAnterior = sesion.sbUid
        sb.iniciarSesion(email.trim(), contrasena)
        if (tokenAnonimo != null && uidAnterior != sesion.sbUid) sb.eliminarDatosConToken(tokenAnonimo)
        sesion.emailPendiente = null
        restaurar()
        activarRespaldo()
        sync.registrarEvento("sesion_iniciada")
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
