package com.inacap.health

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.inacap.health.cuenta.CuentaRepositorio
import com.inacap.health.cuenta.EstadoCuenta
import com.inacap.health.cuenta.ReglasCuenta
import com.inacap.health.cuenta.ResultadoAcceso
import com.inacap.health.data.AppDatabase
import com.inacap.health.data.Perfil
import com.inacap.health.data.remote.ConfigNube
import com.inacap.health.data.sync.SyncManager
import com.inacap.health.equipo.EquipoRepositorio
import com.inacap.health.equipo.RolEquipo
import com.inacap.health.equipo.SesionEquipoReglas
import kotlinx.coroutines.flow.first
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
 * Tarea 1 · Inicio de sesión único con detección de rol:
 * una cuenta del equipo nunca queda como alumno, el estudiante (anónimo o con correo) sigue igual
 * y al salir del modo equipo se vuelve a iniciar sesión.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AccesoUnicoTest {
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
        nube.cuentas["ana@ejemplo.cl"] = "clave1234"
        runBlocking {
            db.perfilDao().upsertPerfil(Perfil(nombre = "Ana", edad = 21, sexo = "Femenino", patologias = "", alergias = "", medicamentos = "", consentimiento = true, terminosVersion = 2))
            sync.clienteSql!!.asegurarSesion()      // el teléfono ya usaba la app como estudiante anónimo
        }
    }

    @After fun cerrar() { nube.close(); db.close() }

    private fun rolDelServidor(rol: String?) {
        nube.respuestas["POST /rest/v1/rpc/rol_actual"] = if (rol == null) "null" else "\"$rol\""
    }

    @Test fun cuentaDelEquipo_noQuedaComoAlumno() = runBlocking {
        rolDelServidor("tutor")
        nube.respuestas["GET /rest/v1/personal"] = """[{"nombre_visible":"Tutora Camila"}]"""

        val r = cuenta.acceder("tutora@inacap.cl", "Clave1234")
        assertTrue("debe reconocerse como cuenta del equipo: $r", r is ResultadoAcceso.Equipo)
        r as ResultadoAcceso.Equipo
        assertEquals(RolEquipo.TUTOR, r.rol)
        assertEquals("Tutora Camila", r.nombre)

        // Nada de la ruta de estudiante: no restaura, no borra la copia anónima, no activa el respaldo.
        assertTrue(nube.de("GET", "/rest/v1/registros_biometricos").isEmpty())
        assertTrue(nube.de("POST", "/rest/v1/rpc/eliminar_mis_datos").isEmpty())
        assertFalse(sync.sesion.compartirNube)
        assertTrue("mientras decide, no se envían datos de estudiante", sync.sesion.modoEquipo)
        assertFalse("el modo equipo se abre recién al aceptar el compromiso", equipo.activo.value)

        cuenta.confirmarEquipo(r)
        assertTrue(equipo.activo.value)
        assertEquals(RolEquipo.TUTOR, equipo.rol)
        assertTrue(equipo.aceptoConfidencialidad)
        assertTrue(sync.sesion.modoEquipo)
    }

    @Test fun cuentaDelEquipo_desdeLaBienvenidaTambienEntra() = runBlocking {
        rolDelServidor("docente")
        val r = cuenta.acceder("tutora@inacap.cl", "Clave1234", permitirEstudiante = false)
        assertEquals(RolEquipo.DOCENTE, (r as ResultadoAcceso.Equipo).rol)
    }

    @Test fun administrador_esDelEquipo() = runBlocking {
        rolDelServidor("admin")
        val r = cuenta.acceder("tutora@inacap.cl", "Clave1234")
        assertEquals(RolEquipo.ADMIN, (r as ResultadoAcceso.Equipo).rol)
    }

    @Test fun cancelarElModoEquipo_vuelveALaSesionAnonima() = runBlocking {
        val antes = sync.sesion.instantaneaSql()
        rolDelServidor("tutor")
        val r = cuenta.acceder("tutora@inacap.cl", "Clave1234") as ResultadoAcceso.Equipo
        cuenta.cancelarEquipo(r)

        assertEquals(antes.access, sync.sesion.sbAccessToken)
        assertEquals(antes.refresh, sync.sesion.sbRefreshToken)
        assertEquals(NubeFalsa.SB_UID, sync.sesion.sbUid)
        assertNull(sync.sesion.cuentaEmail)
        assertFalse(sync.sesion.modoEquipo)
        assertNull(equipo.rol)
        assertEquals(EstadoCuenta.Anonima, cuenta.estado())
    }

    @Test fun cerrarSesionDelEquipo_reabreElAcceso() = runBlocking {
        rolDelServidor("bienestar")
        cuenta.confirmarEquipo(cuenta.acceder("tutora@inacap.cl", "Clave1234") as ResultadoAcceso.Equipo)
        assertTrue(equipo.activo.value)

        equipo.cerrarSesion()
        assertFalse(equipo.activo.value)
        assertFalse(sync.sesion.modoEquipo)
        // Al salir del modo equipo se muestra «Iniciar sesión», no la app de estudiante.
        assertEquals("", SesionEquipoReglas.accesoTrasCambio(estabaEnEquipo = true, equipoActivo = false, actual = null))
        val expiro = "Tu sesión del equipo expiró"
        assertEquals(expiro, SesionEquipoReglas.accesoTrasCambio(true, false, expiro))
        assertNull("al entrar al modo equipo se oculta", SesionEquipoReglas.accesoTrasCambio(false, true, ""))
        assertNull("un estudiante que nunca entró al equipo no ve la pantalla", SesionEquipoReglas.accesoTrasCambio(false, false, null))
    }

    @Test fun estudianteConCorreo_quedaComoEstudiante() = runBlocking {
        rolDelServidor(null)
        assertEquals(ResultadoAcceso.Estudiante, cuenta.acceder("ana@ejemplo.cl", "clave1234"))
        assertEquals(EstadoCuenta.Registrada("ana@ejemplo.cl"), cuenta.estado())
        assertFalse(sync.sesion.modoEquipo)
        assertTrue(sync.sesion.compartirNube)
        assertNull(equipo.rol)
        assertFalse(equipo.activo.value)
    }

    @Test fun estudianteAnonimo_intacto() = runBlocking {
        // Sin iniciar sesión: la app sigue igual que siempre.
        assertEquals(EstadoCuenta.Anonima, cuenta.estado())
        assertFalse(sync.sesion.modoEquipo)
        assertFalse(equipo.activo.value)
        assertFalse("sin correo no hay nada que revisar", cuenta.revisarCuentaActual())
        assertEquals(NubeFalsa.SB_UID, sync.sesion.sbUid)
        assertTrue(nube.de("POST", "/rest/v1/rpc/rol_actual").isEmpty())
    }

    @Test fun cuentaSinRol_desdeLaBienvenida_seRechazaYVuelveALaSesionAnterior() = runBlocking {
        val antes = sync.sesion.instantaneaSql()
        rolDelServidor(null)
        val r = cuenta.acceder("ana@ejemplo.cl", "clave1234", permitirEstudiante = false)
        assertEquals(ResultadoAcceso.Error(ReglasCuenta.CUENTA_SIN_ROL), r)
        assertEquals(antes.access, sync.sesion.sbAccessToken)
        assertNull(sync.sesion.cuentaEmail)
        assertFalse(sync.sesion.modoEquipo)
        assertTrue("no se restauran datos de estudiante", nube.de("GET", "/rest/v1/registros_biometricos").isEmpty())
    }

    @Test fun contrasenaIncorrecta_noCambiaNada() = runBlocking {
        val antes = sync.sesion.instantaneaSql()
        val r = cuenta.acceder("tutora@inacap.cl", "otra") as ResultadoAcceso.Error
        assertEquals("Correo o contraseña incorrectos.", r.mensaje)
        assertEquals(antes.access, sync.sesion.sbAccessToken)
        assertFalse(sync.sesion.modoEquipo)
        assertNull(equipo.rol)
    }

    @Test fun cuentaDelEquipoQueQuedoComoAlumno_seCorrigeAlAbrir() = runBlocking {
        // Así quedaba en versiones anteriores: una cuenta del equipo entró por la pantalla de estudiante.
        rolDelServidor(null)
        cuenta.acceder("tutora@inacap.cl", "Clave1234")
        assertEquals(EstadoCuenta.Registrada("tutora@inacap.cl"), cuenta.estado())

        rolDelServidor("tutor")       // el servidor dice que es del equipo
        assertTrue(cuenta.revisarCuentaActual())
        assertNull(sync.sesion.cuentaEmail)
        assertNull(sync.sesion.sbRefreshToken)
        assertFalse(sync.sesion.compartirNube)
        assertEquals(0, db.syncPendienteDao().contar().first())
    }

    @Test fun estudianteConCorreoReal_noSeToca() = runBlocking {
        rolDelServidor(null)
        cuenta.acceder("ana@ejemplo.cl", "clave1234")
        assertFalse(cuenta.revisarCuentaActual())
        assertEquals(EstadoCuenta.Registrada("ana@ejemplo.cl"), cuenta.estado())
    }

    @Test fun inicioDeSesionQueQuedoAMedias_seLimpia() {
        // La app se cerró con el diálogo del compromiso abierto: sesión marcada del equipo pero sin rol.
        sync.sesion.modoEquipo = true
        val nuevo = EquipoRepositorio(context, sync)
        assertFalse(nuevo.activo.value)
        assertFalse(sync.sesion.modoEquipo)
    }

    @Test fun rolDeLaRespuesta() {
        assertEquals(RolEquipo.TUTOR, RolEquipo.deRespuesta("\"tutor\""))
        assertEquals(RolEquipo.ADMIN, RolEquipo.deRespuesta(" admin \n"))
        assertNull(RolEquipo.deRespuesta("null"))
        assertNull(RolEquipo.deRespuesta(""))
        assertNull(RolEquipo.deRespuesta("\"estudiante\""))
    }
}
