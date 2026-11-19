package com.inacap.health.seguridad

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Cifra textos pequeños (tokens, claves) antes de guardarlos en el teléfono. */
interface Cifrador {
    val protegido: Boolean
    fun cifrar(texto: String): String
    fun descifrar(cifrado: String): String?
}

/**
 * AES-256-GCM con la clave guardada en el Android Keystore: la clave nunca sale
 * del hardware seguro del teléfono y no se puede copiar a otro dispositivo.
 */
class KeystoreCifrador(private val alias: String = ALIAS) : Cifrador {
    override val protegido = true

    private fun clave(): SecretKey {
        val ks = KeyStore.getInstance(PROVEEDOR).apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEEDOR)
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    override fun cifrar(texto: String): String {
        val c = Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, clave()) }
        val datos = c.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv + datos, Base64.NO_WRAP)
    }

    override fun descifrar(cifrado: String): String? = try {
        val todo = Base64.decode(cifrado, Base64.NO_WRAP)
        val c = Cipher.getInstance(TRANSFORMACION).apply {
            init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(128, todo, 0, IV_BYTES))
        }
        String(c.doFinal(todo, IV_BYTES, todo.size - IV_BYTES), Charsets.UTF_8)
    } catch (e: Exception) {
        Log.w("KeystoreCifrador", "No se pudo descifrar: ${e.javaClass.simpleName}")
        null
    }

    companion object {
        const val ALIAS = "inacap_health_clave_maestra"
        private const val PROVEEDOR = "AndroidKeyStore"
        private const val TRANSFORMACION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12

        fun disponible(): Boolean = try {
            KeyStore.getInstance(PROVEEDOR).apply { load(null) }
            true
        } catch (_: Exception) {
            false
        }
    }
}

/** Solo para entornos sin Keystore (pruebas en la JVM). */
object SinCifrado : Cifrador {
    override val protegido = false
    override fun cifrar(texto: String) = texto
    override fun descifrar(cifrado: String) = cifrado
}

object Cifradores {
    val porDefecto: Cifrador by lazy {
        if (KeystoreCifrador.disponible()) KeystoreCifrador() else SinCifrado
    }
}
