package com.inacap.health

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.inacap.health.data.AppDatabase
import com.inacap.health.data.Perfil
import com.inacap.health.data.PasosDia
import com.inacap.health.data.RegistroBiometrico
import com.inacap.health.data.ResultadoScoff
import com.inacap.health.data.remote.ConfigNube
import com.inacap.health.data.remote.ErrorNube
import com.inacap.health.data.sync.SyncManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prueba la sincronización completa (bandeja local → SQL y NoSQL) contra un
 * servidor falso, sin internet ni cuentas reales.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SyncManagerTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var nube: NubeFalsa
    private lateinit var sync: SyncManager

    @Before
    fun preparar() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        nube = NubeFalsa()
        sync = SyncManager(
            context, db,
            ConfigNube(
                supabaseUrl = nube.base,
                supabaseAnonKey = "anon-key",
                firebaseApiKey = "fb-key",
                firebaseProjectId = "p",
                urlFirestore = "${nube.base}/fs/v1",
                urlIdentity = "${nube.base}/id/v1",
                urlSecureToken = "${nube.base}/st/v1"
            )
        )
        runBlocking {
            db.perfilDao().upsertPerfil(
                Perfil(
                    nombre = "Ana Pérez", edad = 21, sexo = "Femenino",
                    patologias = "asma bronquial", alergias = "maní", medicamentos = "salbutamol",
                    consentimiento = true, terminosVersion = 1
                )
            )
        }
    }

    @After
    fun cerrar() {
        nube.close()
        db.close()
    }

    private fun pendientes() = runBlocking { db.syncPendienteDao().contar().first() }

    private fun esperarPendientes(n: Int) {
        val limite = System.currentTimeMillis() + 5_000
        while (pendientes() < n && System.currentTimeMillis() < limite) Thread.sleep(20)
        assertEquals(n, pendientes())
    }

    private val imc = RegistroBiometrico(pesoKg = 70.0, estaturaCm = 175.0, imc = 22.9, aguaMl = 0, frecuenciaCardiaca = null, createdAt = 1_700_000_000_000)
    private val scoff = ResultadoScoff(puntaje = 2, respuestas = "1,1,0,0,0", requiereDerivacion = true, createdAt = 1_700_000_000_000)

    @Test
    fun sinConsentimiento_nadaSaleDelTelefono() = runBlocking {
        sync.sesion.compartirNube = false
        sync.encolarRegistro(imc)
        sync.encolarScoff(scoff)
        sync.registrarEvento("agua_registrada")
        Thread.sleep(200)
        assertEquals(0, pendientes())
        sync.procesar()
        assertTrue("No debe haber ninguna petición a la nube", nube.peticiones.isEmpty())
    }

    @Test
    fun conConsentimiento_enviaAnonimizadoYEnOrden() = runBlocking {
        sync.sesion.compartirNube = true
        sync.encolarRegistro(imc, "Moderado")
        sync.encolarScoff(scoff)
        sync.registrarEvento("imc_calculado", mapOf("categoria" to "en_rango"))
        esperarPendientes(3)

        sync.procesar()

        assertEquals(0, pendientes())
        assertTrue(sync.sesion.ultimoEnvio > 0)

        // Sesiones anónimas (sin correo ni nombre)
        assertEquals(1, nube.de("POST", "/auth/v1/signup").size)
        assertEquals(1, nube.de("POST", "/id/v1/accounts:signUp").size)

        // "usuarios" se envía antes que sus registros (llave foránea)
        val sql = nube.peticiones.filter { it.ruta.startsWith("/rest/v1/") }
        assertEquals("/rest/v1/usuarios", sql.first().ruta)
        val usuario = JSONArray(sql.first().cuerpo).getJSONObject(0)
        assertEquals(NubeFalsa.SB_UID, usuario.getString("id"))
        assertEquals("18-24", usuario.getString("rango_edad"))

        // Registro IMC: con el mismo UUID del teléfono y upsert idempotente
        val reg = nube.de("POST", "/rest/v1/registros_biometricos").single()
        assertTrue(reg.query.contains("on_conflict=id"))
        val fila = JSONArray(reg.cuerpo).getJSONObject(0)
        assertEquals(imc.uuid, fila.getString("id"))
        assertEquals("en_rango", fila.getString("categoria_imc"))
        assertEquals("Bearer sb-acceso", reg.auth)

        val sc = JSONArray(nube.de("POST", "/rest/v1/resultados_scoff").single().cuerpo).getJSONObject(0)
        assertEquals(2, sc.getInt("puntaje"))
        assertFalse(sc.has("respuestas"))

        // Evento NoSQL: con el uid de Firebase, como timestamp y en formato tipado
        val ev = nube.de("POST", "/fs/v1/projects/p/databases/(default)/documents/eventos").single()
        val campos = JSONObject(ev.cuerpo).getJSONObject("fields")
        assertEquals(NubeFalsa.FB_UID, campos.getJSONObject("uid").getString("stringValue"))
        assertEquals("imc_calculado", campos.getJSONObject("tipo").getString("stringValue"))
        assertTrue(campos.getJSONObject("ts").has("timestampValue"))
        assertEquals("Bearer fb-id", ev.auth)

        // Ningún dato personal o clínico sale del teléfono
        val todo = nube.peticiones.joinToString("\n") { it.cuerpo }
        listOf("Ana", "Pérez", "asma", "maní", "salbutamol", "70.0", "175.0", "1,1,0,0,0").forEach {
            assertFalse("Se filtró '$it' a la nube", todo.contains(it))
        }
    }

    @Test
    fun reenvio_noDuplica() = runBlocking {
        sync.sesion.compartirNube = true
        sync.registrarEvento("app_abierta")
        esperarPendientes(1)
        // Simula que el evento ya llegó antes (p. ej. se cortó la conexión justo después)
        val id = db.syncPendienteDao().getLote(10, 3).single().registroId
        nube.documentos.add(id)

        sync.procesar()

        assertEquals(0, pendientes())                 // 409 = ya estaba: no se reintenta
        assertEquals(1, nube.documentos.size)         // y no hay duplicado
    }

    @Test
    fun sinConexion_conservaPendientesYLuegoEnvia() = runBlocking {
        sync.sesion.compartirNube = true
        sync.encolarRegistro(imc)
        sync.registrarEvento("app_abierta")
        esperarPendientes(2)

        nube.caida = true
        try {
            sync.procesar()
            fail("Debía fallar sin conexión")
        } catch (e: ErrorNube) {
            assertTrue("Un 503 se reintenta", e.transitorio)
        }
        assertEquals("No se pierde nada", 2, pendientes())

        nube.caida = false
        sync.procesar()
        assertEquals(0, pendientes())
        assertEquals(1, nube.de("POST", "/rest/v1/registros_biometricos").size)
    }

    @Test
    fun borrarUnRegistro_enviaDeleteConSuId() = runBlocking {
        sync.sesion.compartirNube = true
        sync.encolarRegistro(imc)
        sync.procesar()
        sync.encolarBorradoRegistro(imc.uuid)
        sync.procesar()
        val del = nube.de("DELETE", "/rest/v1/registros_biometricos").single()
        assertEquals("id=eq.${imc.uuid}", del.query)
    }

    @Test
    fun revocar_borraEnAmbasBasesYLimpiaSesion() = runBlocking {
        sync.sesion.compartirNube = true
        sync.encolarRegistro(imc)
        sync.registrarEvento("app_abierta")
        esperarPendientes(2)
        sync.procesar()
        assertEquals(1, nube.documentos.size)

        sync.encolarScoff(scoff)                       // quedaba algo pendiente…
        sync.desactivarNube(SyncManager.MOTIVO_REVOCACION)
        assertEquals("…y se descarta, no se envía", 0, pendientes())
        assertEquals(SyncManager.MOTIVO_REVOCACION, sync.sesion.borradoRemotoPendiente)

        sync.procesar()

        val rpc = nube.de("POST", "/rest/v1/rpc/eliminar_mis_datos").single()
        assertEquals("revocacion", JSONObject(rpc.cuerpo).getString("motivo"))
        assertTrue("Eventos borrados en Firestore", nube.documentos.isEmpty())
        assertEquals(1, nube.de("POST", "/id/v1/accounts:delete").size)
        assertTrue(nube.de("POST", "/rest/v1/resultados_scoff").isEmpty())
        assertNull(sync.sesion.borradoRemotoPendiente)
        assertNull("Cuenta de Firebase cerrada", sync.sesion.fbRefreshToken)
        assertTrue("Se conserva la sesión SQL (para borrar derivaciones pedidas)", sync.sesion.sbRefreshToken != null)
    }

    @Test
    fun eliminarTodo_cierraTodasLasSesiones() = runBlocking {
        sync.sesion.compartirNube = true
        sync.encolarRegistro(imc)
        sync.registrarEvento("app_abierta")
        esperarPendientes(2)
        sync.procesar()
        sync.desactivarNube(SyncManager.MOTIVO_ELIMINACION)
        sync.procesar()
        assertEquals("eliminacion", JSONObject(nube.de("POST", "/rest/v1/rpc/eliminar_mis_datos").single().cuerpo).getString("motivo"))
        assertFalse(sync.sesion.tieneSesion)
    }

    @Test
    fun activarNube_subeElHistorialQueYaExistia() = runBlocking {
        db.registroBiometricoDao().insert(imc)
        db.resultadoScoffDao().insert(scoff)
        sync.activarNube()
        assertEquals(2, pendientes())
        sync.procesar()
        assertEquals(1, nube.de("POST", "/rest/v1/registros_biometricos").size)
        assertEquals(1, nube.de("POST", "/rest/v1/resultados_scoff").size)
    }

    @Test
    fun errorDeConfiguracion_noPierdeDatosYExplicaQueFalta() = runBlocking {
        sync.sesion.compartirNube = true
        sync.encolarRegistro(imc)
        sync.registrarEvento("app_abierta")
        esperarPendientes(2)

        nube.faltaEsquema = true
        nube.reglasFirestoreSinPublicar = true
        repeat(5) { runCatching { sync.procesar() } }           // muchos intentos fallidos…
        assertEquals("…y no se descarta nada", 2, pendientes())
        assertTrue(sync.sincronizarAhora().contains("schema.sql"))

        val diag = sync.diagnosticar().toMap()
        assertTrue(diag["Base SQL (Supabase)"]!!.contains("schema.sql"))
        assertTrue(diag["Base NoSQL (Firestore)"]!!.contains("reglas"))

        // Al corregir la configuración, todo se envía
        nube.faltaEsquema = false
        nube.reglasFirestoreSinPublicar = false
        assertTrue(sync.sincronizarAhora().startsWith("Listo"))
        assertEquals(0, pendientes())
    }

    @Test
    fun inicioAnonimoDesactivado_seExplica() = runBlocking {
        nube.anonimoDesactivado = true
        sync.sesion.compartirNube = true
        sync.encolarRegistro(imc)
        val r = sync.sincronizarAhora()
        assertTrue(r, r.contains("Allow anonymous sign-ins"))
        assertEquals(1, pendientes())
    }

    @Test
    fun sinCompartir_avisaPorQueNoSeEnvia() = runBlocking {
        sync.sesion.compartirNube = false
        assertTrue(sync.sincronizarAhora().contains("desactivado"))
    }

    @Test
    fun registroSubidoConOtraSesion_seReenviaConIdNuevoYNoTrabaLaCola() = runBlocking {
        sync.sesion.compartirNube = true
        val pasos = PasosDia(fecha = "2026-11-19", pasos = 4200, fuente = "sensor")
        db.pasosDiaDao().upsert(pasos)
        db.registroBiometricoDao().insert(imc)
        // El servidor ya tiene estos ids, pero de una sesión anónima anterior del teléfono.
        nube.idsDeOtraSesion += listOf(pasos.uuid, imc.uuid)
        sync.encolarPasos(pasos)
        sync.encolarRegistro(imc)
        sync.encolarScoff(scoff)
        esperarPendientes(3)

        sync.procesar()

        assertEquals("No debe quedar nada trabado", 0, pendientes())
        assertNull(sync.sesion.ultimoErrorSync)
        val enviados = nube.de("POST", "/rest/v1/registros_biometricos").map { JSONArray(it.cuerpo).getJSONObject(0).getString("id") }
        assertEquals(4, enviados.size)   // 2 rechazados + 2 reenviados con id nuevo
        val nuevoPasos = db.pasosDiaDao().get("2026-11-19")!!.uuid
        val nuevoImc = db.registroBiometricoDao().getPorUuid(imc.uuid)
        assertNull("El registro local cambió de id", nuevoImc)
        assertNotEquals(pasos.uuid, nuevoPasos)
        assertTrue(enviados.contains(nuevoPasos))
        assertEquals(1, nube.de("POST", "/rest/v1/resultados_scoff").size)

        // Las actualizaciones siguientes del mismo día usan el id nuevo (no vuelven a chocar).
        sync.encolarPasos(db.pasosDiaDao().get("2026-11-19")!!.copy(pasos = 6000))
        esperarPendientes(1)
        sync.procesar()
        assertEquals(nuevoPasos, JSONArray(nube.de("POST", "/rest/v1/registros_biometricos").last().cuerpo).getJSONObject(0).getString("id"))
        assertEquals(0, pendientes())
    }
}
