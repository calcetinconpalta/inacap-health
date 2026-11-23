package com.inacap.health.seguridad

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Hash del PIN con PBKDF2-HMAC-SHA256 y sal aleatoria (nunca se guarda el PIN). */
object Pin {
    const val ITERACIONES = 120_000
    const val LARGO_MIN = 4
    const val LARGO_MAX = 8

    fun esValido(pin: String) = pin.length in LARGO_MIN..LARGO_MAX && pin.all { it.isDigit() }

    /** PIN demasiado fácil de adivinar (1111, 1234, 4321…). */
    fun esTrivial(pin: String): Boolean {
        if (pin.toSet().size == 1) return true
        val difs = pin.zipWithNext { a, b -> b - a }.toSet()
        return difs == setOf(1) || difs == setOf(-1)
    }

    fun nuevaSal(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, sal: ByteArray, iteraciones: Int = ITERACIONES): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), sal, iteraciones, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Comparación en tiempo constante. */
    fun coincide(pin: String, sal: ByteArray, esperado: ByteArray, iteraciones: Int = ITERACIONES) =
        MessageDigest.isEqual(hash(pin, sal, iteraciones), esperado)

    /** Espera tras intentos fallidos: 0 s hasta 4 fallos, luego 30 s, 60 s, 120 s… (máx. 15 min). */
    fun esperaTrasFallos(fallos: Int): Long {
        if (fallos < 5) return 0
        val exp = (fallos - 5).coerceAtMost(5)
        return (30_000L shl exp).coerceAtMost(15 * 60_000L)
    }

    /**
     * Cuánto falta de la espera, medida con el reloj monótono (tiempo desde que se encendió el
     * teléfono), que no cambia al ajustar la hora. [pendienteMs] es la espera que quedaba en el
     * momento [refMs] del arranque [refArranque].
     *
     * Si el teléfono se reinició (otro número de arranque, o el reloj monótono "retrocedió"),
     * no se sabe cuánto pasó antes de apagarlo: se cuenta solo el tiempo desde el encendido,
     * así reiniciar nunca acorta la espera.
     */
    fun esperaRestante(pendienteMs: Long, refMs: Long, refArranque: Int, ahoraMs: Long, arranqueActual: Int): Long {
        if (pendienteMs <= 0) return 0
        val mismoArranque = arranqueActual == refArranque && ahoraMs >= refMs
        val transcurrido = if (mismoArranque) ahoraMs - refMs else ahoraMs
        return (pendienteMs - transcurrido).coerceIn(0, pendienteMs)
    }

    /** ¿Hay que bloquear al volver a la app? */
    fun debeBloquear(tieneBloqueo: Boolean, enSegundoPlanoDesde: Long, ahora: Long, minutos: Int): Boolean =
        tieneBloqueo && enSegundoPlanoDesde > 0 && ahora - enSegundoPlanoDesde >= minutos * 60_000L
}
