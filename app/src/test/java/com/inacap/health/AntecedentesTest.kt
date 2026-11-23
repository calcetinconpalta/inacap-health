package com.inacap.health

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.inacap.health.data.AppDatabase
import com.inacap.health.data.Perfil
import com.inacap.health.data.remote.ConfigNube
import com.inacap.health.data.sync.SyncManager
import com.inacap.health.salud.Antecedentes
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/** Tarea 18d: antecedentes cifrados de extremo a extremo para Bienestar, sin estadísticas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AntecedentesTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var nube: NubeFalsa
    private lateinit var sync: SyncManager
    private lateinit var par: KeyPair

    private val perfil = Perfil(nombre = "Ana Pérez", edad = 21, sexo = "Femenino", patologias = "Hipertensión",
        alergias = "Paracetamol", medicamentos = "Losartán", consentimiento = true)

    @Before fun preparar() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        nube = NubeFalsa()
        par = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val spki = Base64.encodeToString(par.public.encoded, Base64.NO_WRAP)
        sync = SyncManager(context, db, ConfigNube(supabaseUrl = nube.base, supabaseAnonKey = "sb_publishable_prueba",
            firebaseApiKey = "", firebaseProjectId = "", bienestarClavePublica = spki))
    }

    @After fun cerrar() { nube.close(); db.close() }

    private fun descifrar(sobre: JSONObject): JSONObject {
        val clave = Cipher.getInstance("RSA/ECB/OAEPPadding").run {
            init(Cipher.DECRYPT_MODE, par.private, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT))
            doFinal(Base64.decode(sobre.getString("ek"), Base64.NO_WRAP))
        }
        return JSONObject(Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(clave, "AES"), GCMParameterSpec(128, Base64.decode(sobre.getString("iv"), Base64.NO_WRAP)))
            String(doFinal(Base64.decode(sobre.getString("ct"), Base64.NO_WRAP)))
        })
    }

    @Test fun soloLosTresAntecedentesYNadaMas() {
        val j = Antecedentes.json(perfil).toString()
        listOf("Ana", "21", "Femenino").forEach { assertFalse("No debe incluir '$it'", j.contains(it)) }
        assertTrue(j.contains("Hipertensión") && j.contains("Paracetamol") && j.contains("Losartán"))
    }

    @Test fun viajanCifradosYSoloBienestarLosLee() = runBlocking {
        sync.sesion.compartirNube = false                 // aunque no comparta estadísticas: pidió atención
        assertTrue(sync.enviarAntecedentes(perfil))
        assertTrue("Editar reemplaza el envío pendiente", sync.enviarAntecedentes(perfil.copy(alergias = "Ninguna")))
        assertEquals(1, db.syncPendienteDao().getLote(10, 99).count { it.tabla == SyncManager.TABLA_ANTECEDENTES })
        sync.procesar()
        val envio = nube.de("POST", "/rest/v1/rpc/guardar_antecedentes").single()
        assertFalse("El servidor nunca ve el texto", envio.cuerpo.contains("Hipertensión"))
        val claro = descifrar(JSONObject(envio.cuerpo).getJSONObject("p_cifrado"))
        assertEquals("Hipertensión", claro.getString("patologias"))
        assertEquals("Ninguna", claro.getString("alergias"))
    }

    @Test fun sinClaveDeBienestarNoSeEnvianEnClaro() = runBlocking {
        val sinClave = SyncManager(context, db, ConfigNube(supabaseUrl = nube.base, supabaseAnonKey = "sb_publishable_prueba", firebaseApiKey = "", firebaseProjectId = ""))
        assertFalse(sinClave.enviarAntecedentes(perfil))
        assertTrue(db.syncPendienteDao().getLote(10, 99).none { it.tabla == SyncManager.TABLA_ANTECEDENTES })
    }
}
