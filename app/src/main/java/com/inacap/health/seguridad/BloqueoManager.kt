package com.inacap.health.seguridad

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * HU-08 · Bloqueo de la app con PIN y/o biometría.
 * - Se bloquea al abrir la app y al volver después de [minutosExpiracion] en segundo plano.
 * - Mientras está bloqueada, la interfaz no muestra datos.
 * - Tras 5 intentos fallidos, espera creciente antes de volver a intentar.
 */
class BloqueoManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("bloqueo_prefs", Context.MODE_PRIVATE)
    private var enSegundoPlanoDesde = 0L

    private val _bloqueada = MutableStateFlow(tienePin)
    val bloqueada: StateFlow<Boolean> = _bloqueada.asStateFlow()

    val tienePin: Boolean get() = prefs.contains(K_HASH)

    var biometriaActiva: Boolean
        get() = prefs.getBoolean(K_BIO, false)
        set(v) = prefs.edit { putBoolean(K_BIO, v) }

    var minutosExpiracion: Int
        get() = prefs.getInt(K_MIN, 5)
        set(v) = prefs.edit { putInt(K_MIN, v) }

    val fallos: Int get() = prefs.getInt(K_FALLOS, 0)

    /** Milisegundos que faltan para poder volver a intentar (0 = ya se puede). */
    fun esperaRestante(ahora: Long = System.currentTimeMillis()): Long =
        (prefs.getLong(K_ULTIMO_FALLO, 0) + Pin.esperaTrasFallos(fallos) - ahora).coerceAtLeast(0)

    fun observarCicloDeVida() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                enSegundoPlanoDesde = System.currentTimeMillis()
            }

            override fun onStart(owner: LifecycleOwner) {
                if (Pin.debeBloquear(tienePin, enSegundoPlanoDesde, System.currentTimeMillis(), minutosExpiracion)) {
                    _bloqueada.value = true
                }
                enSegundoPlanoDesde = 0
            }
        })
    }

    fun configurarPin(pin: String) {
        require(Pin.esValido(pin)) { "PIN inválido" }
        val sal = Pin.nuevaSal()
        prefs.edit(commit = true) {
            putString(K_SAL, Base64.encodeToString(sal, Base64.NO_WRAP))
            putString(K_HASH, Base64.encodeToString(Pin.hash(pin, sal), Base64.NO_WRAP))
            putInt(K_FALLOS, 0)
        }
    }

    /** true si el PIN es correcto. Respeta la espera tras fallos. */
    fun verificarPin(pin: String): Boolean {
        if (esperaRestante() > 0) return false
        val sal = prefs.getString(K_SAL, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val hash = prefs.getString(K_HASH, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val ok = Pin.coincide(pin, sal, hash)
        prefs.edit {
            if (ok) putInt(K_FALLOS, 0)
            else {
                putInt(K_FALLOS, fallos + 1)
                putLong(K_ULTIMO_FALLO, System.currentTimeMillis())
            }
        }
        return ok
    }

    fun desbloquear() {
        prefs.edit { putInt(K_FALLOS, 0) }
        _bloqueada.value = false
    }

    fun bloquearAhora() {
        if (tienePin) _bloqueada.value = true
    }

    fun quitarBloqueo() {
        prefs.edit(commit = true) { clear() }
        _bloqueada.value = false
    }

    private companion object {
        const val K_HASH = "pin_hash"
        const val K_SAL = "pin_sal"
        const val K_BIO = "biometria"
        const val K_MIN = "minutos_expiracion"
        const val K_FALLOS = "fallos"
        const val K_ULTIMO_FALLO = "ultimo_fallo"
    }
}
