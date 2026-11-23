package com.inacap.health.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.inacap.health.seguridad.Cifrador
import com.inacap.health.seguridad.Cifradores

/**
 * Guarda las sesiones anónimas de Supabase y Firebase, y el estado de la
 * sincronización. Archivo separado de "health_prefs" para que borrar los datos
 * locales no pierda la orden de borrar también los datos en la nube.
 *
 * Los tokens se guardan cifrados con AES-256-GCM (clave en el Android Keystore).
 */
class SesionNube(context: Context, private val cifrador: Cifrador = Cifradores.porDefecto) {
    val prefs: SharedPreferences =
        context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    private fun leerSecreto(clave: String): String? =
        prefs.getString(clave, null)?.let { cifrador.descifrar(it) }

    private fun guardarSecreto(clave: String, valor: String?) =
        prefs.edit { if (valor == null) remove(clave) else putString(clave, cifrador.cifrar(valor)) }

    // --- Consentimiento (copia rápida del valor guardado en Perfil) ---
    var compartirNube: Boolean
        get() = prefs.getBoolean(K_COMPARTIR, false)
        set(v) = prefs.edit { putBoolean(K_COMPARTIR, v) }

    /** En modo equipo la sesión es del personal: no se envían datos de estudiante. */
    var modoEquipo: Boolean
        get() = prefs.getBoolean("modo_equipo", false)
        set(v) = prefs.edit(commit = true) { putBoolean("modo_equipo", v) }

    // --- Supabase (SQL) ---
    var sbAccessToken: String?
        get() = leerSecreto("sb_access")
        set(v) = guardarSecreto("sb_access", v)
    var sbRefreshToken: String?
        get() = leerSecreto("sb_refresh")
        set(v) = guardarSecreto("sb_refresh", v)
    var sbExpiraEn: Long
        get() = prefs.getLong("sb_expira", 0)
        set(v) = prefs.edit { putLong("sb_expira", v) }
    var sbUid: String?
        get() = prefs.getString(K_SB_UID, null)
        set(v) = prefs.edit { putString(K_SB_UID, v) }

    /** Correo de la cuenta (null = cuenta anónima). */
    var cuentaEmail: String?
        get() = prefs.getString(K_EMAIL, null)
        set(v) = prefs.edit { if (v == null) remove(K_EMAIL) else putString(K_EMAIL, v) }

    /** Correo esperando confirmación (se le envió un enlace). */
    var emailPendiente: String?
        get() = prefs.getString("email_pendiente", null)
        set(v) = prefs.edit { if (v == null) remove("email_pendiente") else putString("email_pendiente", v) }

    // --- Firebase (NoSQL) ---
    var fbIdToken: String?
        get() = leerSecreto("fb_id")
        set(v) = guardarSecreto("fb_id", v)
    var fbRefreshToken: String?
        get() = leerSecreto("fb_refresh")
        set(v) = guardarSecreto("fb_refresh", v)
    var fbExpiraEn: Long
        get() = prefs.getLong("fb_expira", 0)
        set(v) = prefs.edit { putLong("fb_expira", v) }
    var fbUid: String?
        get() = prefs.getString("fb_uid", null)
        set(v) = prefs.edit { putString("fb_uid", v) }

    // --- Estado de sincronización ---
    var ultimoEnvio: Long
        get() = prefs.getLong(K_ULTIMO_ENVIO, 0)
        set(v) = prefs.edit { putLong(K_ULTIMO_ENVIO, v) }

    var ultimoErrorSync: String?
        get() = prefs.getString(K_ULTIMO_ERROR, null)
        set(v) = prefs.edit { putString(K_ULTIMO_ERROR, v) }

    /** null = no hay borrado pendiente; "eliminacion" o "revocacion" si lo hay. */
    var borradoRemotoPendiente: String?
        get() = prefs.getString(K_BORRADO, null)
        set(v) = prefs.edit(commit = true) { putString(K_BORRADO, v) }

    /** Error de la app guardado al cerrarse inesperadamente (sin datos personales). */
    var errorAppPendiente: String?
        get() = prefs.getString("error_app", null)
        set(v) = prefs.edit(commit = true) { putString("error_app", v) }

    val tieneSesion: Boolean
        get() = sbRefreshToken != null || fbRefreshToken != null

    /** Copia de la sesión SQL actual, para volver a ella si un inicio de sesión se cancela o se rechaza. */
    data class InstantaneaSql(
        val access: String?, val refresh: String?, val expira: Long,
        val uid: String?, val email: String?, val modoEquipo: Boolean
    )

    fun instantaneaSql() = InstantaneaSql(sbAccessToken, sbRefreshToken, sbExpiraEn, sbUid, cuentaEmail, modoEquipo)

    fun restaurarSql(i: InstantaneaSql) {
        sbAccessToken = i.access; sbRefreshToken = i.refresh; sbExpiraEn = i.expira
        prefs.edit(commit = true) { if (i.uid == null) remove(K_SB_UID) else putString(K_SB_UID, i.uid) }
        cuentaEmail = i.email
        modoEquipo = i.modoEquipo
    }

    fun limpiarSesionFirebase() {
        prefs.edit { listOf("fb_id", "fb_refresh", "fb_expira", "fb_uid").forEach { remove(it) } }
    }

    fun limpiarSesiones() {
        prefs.edit {
            listOf(
                "sb_access", "sb_refresh", "sb_expira", K_SB_UID, K_EMAIL, "email_pendiente",
                "fb_id", "fb_refresh", "fb_expira", "fb_uid"
            ).forEach { remove(it) }
        }
    }

    companion object {
        const val ARCHIVO = "sync_prefs"
        const val K_COMPARTIR = "compartir_nube"
        const val K_ULTIMO_ENVIO = "ultimo_envio"
        const val K_ULTIMO_ERROR = "ultimo_error_sync"
        const val K_BORRADO = "borrado_remoto_pendiente"
        const val K_SB_UID = "sb_uid"
        const val K_EMAIL = "cuenta_email"
    }
}
