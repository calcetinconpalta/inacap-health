package com.inacap.health

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.inacap.health.cuenta.CuentaRepositorio
import com.inacap.health.cuenta.ReglasCuenta
import com.inacap.health.cuenta.ResultadoAcceso
import com.inacap.health.cuenta.ResultadoCuenta
import com.inacap.health.data.AppDatabase
import com.inacap.health.data.Perfil
import com.inacap.health.data.remote.ConfigNube
import com.inacap.health.data.remote.ErrorNube
import com.inacap.health.data.remote.ReglasSesionUnica
import com.inacap.health.data.sync.SyncManager
import com.inacap.health.equipo.EquipoRepositorio
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tarea 12: toda cuenta creada desde la app es de alumno; si el correo estaba pre-registrado como
 * del equipo, no queda como alumno. Tarea 13: una sola sesión activa por cuenta.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EquipoPreregistradoYSesionUnicaTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var nube: NubeFalsa
    private lateinit var sync: SyncManager
    private lateinit var cuenta: CuentaRepositorio

    @Before fun preparar() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        context.getSharedPreferences("equipo_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        nube = NubeFalsa()
        sync = SyncManager(context, db, ConfigNube(supabaseUrl = nube.base, supabaseAnonKey = "sb_publishable_prueba", firebaseApiKey = "", firebaseProjectId = ""))
        sync.sesion.consumirSesionCerrada()
        cuenta = CuentaRepositorio(context, sync, db, EquipoRepositorio(context, sync))
        nube.cuentas["ana@ejemplo.cl"] = "clave1234"
        runBlocking {
            db.perfilDao().upsertPerfil(Perfil(nombre = "Ana", edad = 21, sexo = "Femenino", patologias = "", alergias = "", medicamentos = "", consentimiento = true, terminosVersion = 2))
            sync.clienteSql!!.asegurarSesion()
        }
    }

    @After fun cerrar() { nube.close(); db.close(); sync.sesion.consumirSesionCerrada() }

    private fun rol(r: String?) { nube.respuestas["POST /rest/v1/rpc/rol_actual"] = if (r == null) "null" else "\"$r\"" }

    @Test fun cuentaNuevaSinPreregistroEsAlumno() = runBlocking {
        rol(null)
        assertEquals(ResultadoCuenta.Listo, cuenta.crearCuenta("nueva@ejemplo.cl", "clave1234"))
        assertEquals("nueva@ejemplo.cl", sync.sesion.cuentaEmail)
        assertTrue(sync.sesion.compartirNube)
        assertEquals("Registra su sesión como la única vigente", 1, nube.de("POST", "/rest/v1/rpc/registrar_sesion").size)
    }

    @Test fun cuentaNuevaConCorreoPreregistradoNoQuedaComoAlumno() = runBlocking {
        rol("tutor")   // el servidor aplicó la invitación al confirmar el correo
        val r = cuenta.crearCuenta("tutora.nueva@ejemplo.cl", "clave1234")
        assertEquals(ResultadoCuenta.Error(ReglasCuenta.CUENTA_ES_DEL_EQUIPO), r)
        assertNull("No queda sesión de alumno con la cuenta del equipo", sync.sesion.cuentaEmail)
        assertFalse(sync.sesion.compartirNube)
        assertEquals("Se borra lo de alumno que se alcanzó a subir", 1, nube.de("POST", "/rest/v1/rpc/eliminar_mis_datos").size)
        assertTrue("Primero se aplica la invitación", nube.de("POST", "/rest/v1/rpc/reclamar_rol_invitado").isNotEmpty())
    }

    @Test fun alIniciarSesionSeRegistraLaSesionYSeAplicaLaInvitacion() = runBlocking {
        rol(null)
        assertEquals(ResultadoAcceso.Estudiante, cuenta.acceder("ana@ejemplo.cl", "clave1234"))
        val rutas = nube.peticiones.filter { it.ruta.startsWith("/rest/v1/rpc/") }.map { it.ruta.substringAfterLast('/') }
        assertTrue(rutas.toString(), rutas.indexOf("registrar_sesion") in 0 until rutas.indexOf("rol_actual"))
        assertTrue(rutas.toString(), rutas.indexOf("reclamar_rol_invitado") in 0 until rutas.indexOf("rol_actual"))
    }

    @Test fun otroTelefonoTomaLaSesionYEsteVuelveAIniciarSesion() = runBlocking {
        rol(null)
        assertEquals(ResultadoAcceso.Estudiante, cuenta.acceder("ana@ejemplo.cl", "clave1234"))
        nube.sesionReemplazada = true
        val e = runCatching { sync.clienteSql!!.seleccionar("registros_biometricos", "select=id") }.exceptionOrNull()
        assertTrue(e is ErrorNube && e.codigo == 401)
        assertEquals(ReglasSesionUnica.MOTIVO_OTRO_TELEFONO, sync.sesion.sesionCerrada.value)
        assertNull(sync.sesion.cuentaEmail)
        assertFalse("No sigue enviando datos con otra sesión", sync.sesion.compartirNube)
    }

    @Test fun unaCuentaNuncaPasaEnSilencioAAnonima() = runBlocking {
        rol(null)
        assertEquals(ResultadoAcceso.Estudiante, cuenta.acceder("ana@ejemplo.cl", "clave1234"))
        sync.sesion.sbAccessToken = null; sync.sesion.sbExpiraEn = 0     // el token de acceso venció
        nube.sesionReemplazada = true                                     // y el de renovación fue revocado
        val antes = nube.de("POST", "/auth/v1/signup").size
        assertTrue(runCatching { sync.clienteSql!!.asegurarSesion() }.isFailure)
        assertEquals("No se creó una sesión anónima nueva", antes, nube.de("POST", "/auth/v1/signup").size)
        assertEquals(ReglasSesionUnica.MOTIVO_REVOCADA, sync.sesion.sesionCerrada.value)
    }

    @Test fun elAnonimoSigueIgual() = runBlocking {
        // Sin cuenta: si su renovación falla, se crea otra anónima como antes (no hay a qué volver).
        sync.sesion.sbAccessToken = null; sync.sesion.sbExpiraEn = 0
        nube.sesionReemplazada = true
        sync.clienteSql!!.asegurarSesion()
        assertNull(sync.sesion.sesionCerrada.value)
    }
}
