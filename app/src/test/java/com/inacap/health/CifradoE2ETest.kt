package com.inacap.health

import android.app.Application
import android.util.Base64
import com.inacap.health.seguridad.CifradoE2E
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.KeyPairGenerator
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CifradoE2ETest {

    @Test
    fun soloLaClavePrivadaPuedeDescifrar() {
        val par = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        val spki = Base64.encodeToString(par.public.encoded, Base64.NO_WRAP)
        val datos = """{"nombre":"Valentina","contacto":"+56 9 1234 5678"}"""

        val sobre = CifradoE2E.cifrar(datos, spki)
        assertFalse("El sobre no debe contener el texto", sobre.toString().contains("Valentina"))
        assertEquals(CifradoE2E.huella(spki), sobre.getString("kid"))
        assertNotEquals("IV aleatorio", sobre.getString("iv"), CifradoE2E.cifrar(datos, spki).getString("iv"))

        // Descifrado igual al que hace el dashboard (RSA-OAEP SHA-256 + AES-256-GCM)
        val clave = Cipher.getInstance("RSA/ECB/OAEPPadding").run {
            init(Cipher.DECRYPT_MODE, par.private, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT))
            doFinal(Base64.decode(sobre.getString("ek"), Base64.NO_WRAP))
        }
        val claro = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(clave, "AES"), GCMParameterSpec(128, Base64.decode(sobre.getString("iv"), Base64.NO_WRAP)))
            String(doFinal(Base64.decode(sobre.getString("ct"), Base64.NO_WRAP)))
        }
        assertEquals(datos, claro)
    }

    /** Interoperabilidad con WebCrypto: si INACAP_E2E_DIR tiene publica.txt (generada en el navegador/Node), deja sobre.json. */
    @Test
    fun interoperabilidadConWebCrypto() {
        val dir = System.getenv("INACAP_E2E_DIR")?.let { File(it) } ?: return
        val publica = File(dir, "publica.txt").readText().trim()
        val sobre = CifradoE2E.cifrar(JSONObject().put("nombre", "Prueba Interop").put("contacto", "interop@ejemplo.cl").toString(), publica)
        File(dir, "sobre.json").writeText(sobre.toString())
    }
}
