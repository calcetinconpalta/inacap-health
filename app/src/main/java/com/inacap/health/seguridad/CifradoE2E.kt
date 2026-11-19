package com.inacap.health.seguridad

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Cifrado de extremo a extremo para las solicitudes de derivación.
 *
 * - El teléfono cifra los datos con AES-256-GCM (clave aleatoria de un solo uso).
 * - Esa clave se cifra con la clave PÚBLICA RSA-OAEP (SHA-256) de Bienestar Estudiantil,
 *   incluida en la app. La clave privada solo la tiene Bienestar, en su computador.
 * - El servidor guarda un texto ilegible: ni la nube ni los administradores pueden leerlo.
 *
 * Compatible con WebCrypto (el dashboard descifra con crypto.subtle).
 */
object CifradoE2E {
    const val ALGORITMO = "RSA-OAEP-256+A256GCM"

    private val OAEP_SHA256 = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)

    fun clavePublica(spkiBase64: String): PublicKey =
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(spkiBase64.trim(), Base64.DEFAULT)))

    /** Huella corta de la clave pública (para saber con qué clave se cifró). */
    fun huella(spkiBase64: String): String =
        MessageDigest.getInstance("SHA-256").digest(Base64.decode(spkiBase64.trim(), Base64.DEFAULT))
            .take(8).joinToString("") { "%02x".format(it) }

    fun cifrar(texto: String, spkiBase64: String): JSONObject {
        val clave = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cifrado = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, clave, GCMParameterSpec(128, iv))
            doFinal(texto.toByteArray(Charsets.UTF_8))
        }
        val claveCifrada = Cipher.getInstance("RSA/ECB/OAEPPadding").run {
            init(Cipher.ENCRYPT_MODE, clavePublica(spkiBase64), OAEP_SHA256)
            doFinal(clave.encoded)
        }
        fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
        return JSONObject()
            .put("v", 1)
            .put("alg", ALGORITMO)
            .put("kid", huella(spkiBase64))
            .put("ek", b64(claveCifrada))
            .put("iv", b64(iv))
            .put("ct", b64(cifrado))
    }
}
