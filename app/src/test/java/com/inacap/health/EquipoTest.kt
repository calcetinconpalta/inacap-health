package com.inacap.health

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.inacap.health.cuenta.CuentaRepositorio
import com.inacap.health.cuenta.ResultadoAcceso
import com.inacap.health.data.AppDatabase
import com.inacap.health.data.remote.ConfigNube
import com.inacap.health.data.sync.SyncManager
import com.inacap.health.equipo.EquipoRepositorio
import com.inacap.health.equipo.RolEquipo
import com.inacap.health.equipo.SesionEquipoReglas
import com.inacap.health.util.Saneamiento
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Modo equipo: sesión con expiración, rol decidido por el servidor y saneamiento de lo que se envía. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EquipoTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var nube: NubeFalsa
    private lateinit var sync: SyncManager
    private lateinit var equipo: EquipoRepositorio
    private lateinit var cuenta: CuentaRepositorio

    @Before fun preparar() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        context.getSharedPreferences("equipo_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        nube = NubeFalsa()
        sync = SyncManager(context, db, ConfigNube(supabaseUrl = nube.base, supabaseAnonKey = "sb_publishable_prueba", firebaseApiKey = "", firebaseProjectId = ""))
        equipo = EquipoRepositorio(context, sync)
        cuenta = CuentaRepositorio(context, sync, db, equipo)
        nube.cuentas["tutora@inacap.cl"] = "Clave1234"
    }

    @After fun cerrar() { nube.close(); db.close() }

    @Test fun reglasDeExpiracion() {
        val t0 = 1_000_000_000L
        assertFalse(SesionEquipoReglas.vencida(t0, t0, t0 + 29 * 60_000))
        assertTrue("30 min sin uso", SesionEquipoReglas.vencida(t0, t0, t0 + 31 * 60_000))
        assertTrue("12 h máximo aunque haya actividad", SesionEquipoReglas.vencida(t0, t0 + 12 * 3_600_000, t0 + 12 * 3_600_000 + 1))
        assertTrue("sin inicio registrado", SesionEquipoReglas.vencida(0, t0, t0))
    }

    @Test fun saneamientoQuitaInvisiblesYLimita() {
        assertEquals("hola mundo", Saneamiento.texto("  hola​ mundo\u0000 ", 100))
        assertEquals("a\n\nb", Saneamiento.texto("a\r\n\r\n\r\n\r\nb", 100))
        assertEquals("abc", Saneamiento.texto("abcdef", 3))
        assertEquals("una línea", Saneamiento.linea("una\nlínea", 50))
    }

    /** Entra con el inicio de sesión único y acepta el compromiso (como en la pantalla). */
    private fun entrar(rol: String, personal: String = "[]") = runBlocking {
        nube.respuestas["POST /rest/v1/rpc/rol_actual"] = "\"$rol\""
        nube.respuestas["GET /rest/v1/personal"] = personal
        val r = cuenta.acceder("tutora@inacap.cl", "Clave1234") as ResultadoAcceso.Equipo
        cuenta.confirmarEquipo(r)
    }

    @Test fun iniciarSesion_conRolDelServidor() = runBlocking {
        entrar("tutor", """[{"nombre_visible":"Tutora Camila"}]""")
        assertEquals(RolEquipo.TUTOR, equipo.rol)
        assertEquals("Tutora Camila", equipo.nombre)
        assertTrue(equipo.activo.value)
        assertTrue("en modo equipo no se envían datos de estudiante", sync.sesion.modoEquipo)
        assertFalse(equipo.sesionVencida())
    }

    @Test fun contrasenaIncorrecta_noEntra() = runBlocking {
        assertTrue(cuenta.acceder("tutora@inacap.cl", "otra") is ResultadoAcceso.Error)
        assertNull(equipo.rol)
        assertFalse(sync.sesion.modoEquipo)
    }

    @Test fun enviarMensaje_vaSaneadoYConRolDelEquipo() = runBlocking {
        entrar("bienestar", """[{"nombre_visible":"Psicóloga"}]""")
        assertEquals(RolEquipo.PROFESIONAL, equipo.rol)
        nube.respuestas["POST /rest/v1/mensajes"] = """[{"id":"m1"}]"""
        equipo.enviar("c1", "  Hola​, ¿cómo estás?  ")
        val cuerpo = JSONObject(nube.de("POST", "/rest/v1/mensajes").last().cuerpo.let { if (it.startsWith("[")) it.removePrefix("[").removeSuffix("]") else it })
        assertEquals("Hola, ¿cómo estás?", cuerpo.getString("texto"))
        assertEquals("profesional", cuerpo.getString("rol_autor"))
    }

    @Test fun cerrarSesion_limpiaTodo() = runBlocking {
        entrar("docente")
        equipo.cerrarSesion()
        assertNull(equipo.rol)
        assertFalse(equipo.activo.value)
        assertFalse(sync.sesion.modoEquipo)
    }
}
