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
import com.inacap.health.cuenta.ResultadoCuenta
import com.inacap.health.data.AppDatabase
import com.inacap.health.data.Perfil
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Cuenta con correo: crear (con y sin confirmación), iniciar sesión con restauración y errores. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CuentaTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var nube: NubeFalsa
    private lateinit var sync: SyncManager
    private lateinit var cuenta: CuentaRepositorio

    @Before fun preparar() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        nube = NubeFalsa()
        sync = SyncManager(context, db, ConfigNube(supabaseUrl = nube.base, supabaseAnonKey = "sb_publishable_prueba", firebaseApiKey = "", firebaseProjectId = ""))
        cuenta = CuentaRepositorio(context, sync, db)
        runBlocking {
            db.perfilDao().upsertPerfil(Perfil(nombre = "Ana", edad = 21, sexo = "Femenino", patologias = "asma", alergias = "", medicamentos = "", consentimiento = true, terminosVersion = 2))
        }
    }

    @After fun cerrar() { nube.close(); db.close() }

    private fun puts() = nube.de("PUT", "/auth/v1/user").map { JSONObject(it.cuerpo) }

    @Test fun crearCuenta_sinConfirmacion_quedaRegistradaYConRespaldo() = runBlocking {
        assertEquals(EstadoCuenta.Anonima, cuenta.estado())
        val r = cuenta.crearCuenta("ana@ejemplo.cl", "clave1234")
        assertEquals(ResultadoCuenta.Listo, r)
        assertEquals(EstadoCuenta.Registrada("ana@ejemplo.cl"), cuenta.estado())
        // Se convierte la MISMA cuenta anónima (no se crea otra): primero el correo, luego la contraseña.
        assertEquals(1, nube.de("POST", "/auth/v1/signup").size)
        assertEquals(listOf("email", "password"), puts().map { it.keys().next() })
        assertEquals("clave1234", nube.cuentas["ana@ejemplo.cl"])
        assertTrue("Con cuenta se activa el respaldo", sync.sesion.compartirNube)
        assertTrue(db.perfilDao().getPerfilUnaVez()!!.compartirNube)
        // Las claves publishable no van en Authorization (no son JWT)
        assertFalse(nube.de("POST", "/auth/v1/signup").single().auth.orEmpty().contains("sb_publishable"))
    }

    @Test fun crearCuenta_conConfirmacionDeCorreo() = runBlocking {
        nube.confirmacionRequerida = true
        assertEquals(ResultadoCuenta.ConfirmaCorreo("ana@ejemplo.cl"), cuenta.crearCuenta("ana@ejemplo.cl", "clave1234"))
        assertEquals(EstadoCuenta.Pendiente("ana@ejemplo.cl"), cuenta.estado())
        // Aún no abre el enlace
        assertTrue(cuenta.completarRegistro("clave1234") is ResultadoCuenta.ConfirmaCorreo)
        // Abre el enlace del correo
        nube.emailConfirmado = "ana@ejemplo.cl"
        assertEquals(ResultadoCuenta.Listo, cuenta.completarRegistro("clave1234"))
        assertEquals(EstadoCuenta.Registrada("ana@ejemplo.cl"), cuenta.estado())
        assertEquals("clave1234", nube.cuentas["ana@ejemplo.cl"])
    }

    @Test fun crearCuenta_correoYaRegistrado() = runBlocking {
        nube.cuentas["ana@ejemplo.cl"] = "otra1234"
        val r = cuenta.crearCuenta("ana@ejemplo.cl", "clave1234") as ResultadoCuenta.Error
        assertTrue(r.mensaje, r.mensaje.contains("Iniciar sesión"))
    }

    @Test fun iniciarSesion_restauraDatosYBorraLaCopiaAnonima() = runBlocking {
        sync.clienteSql!!.asegurarSesion()              // el teléfono tenía una cuenta anónima
        nube.cuentas["ana@ejemplo.cl"] = "clave1234"
        nube.registrosRemotos = JSONArray()
            .put(JSONObject("""{"id":"a0000000-0000-4000-8000-000000000001","tipo":"imc","imc":22.9,"agua_ml":null,"frecuencia_cardiaca":null,"pasos":null,"fuente":null,"fecha":"2026-11-10T14:00:00.000+00:00"}"""))
            .put(JSONObject("""{"id":"a0000000-0000-4000-8000-000000000002","tipo":"agua","imc":null,"agua_ml":250,"frecuencia_cardiaca":null,"pasos":null,"fuente":null,"fecha":"2026-11-11T10:00:00+00:00"}"""))
            .put(JSONObject("""{"id":"a0000000-0000-4000-8000-000000000003","tipo":"pulso","imc":null,"agua_ml":null,"frecuencia_cardiaca":71,"pasos":null,"fuente":"camara","fecha":"2026-11-12T09:00:00+00:00"}"""))
            .put(JSONObject("""{"id":"a0000000-0000-4000-8000-000000000004","tipo":"pasos","imc":null,"agua_ml":null,"frecuencia_cardiaca":null,"pasos":6400,"fuente":"sensor","fecha":"2026-11-12T12:00:00+00:00"}"""))
        nube.scoffRemotos = JSONArray().put(JSONObject("""{"id":"b0000000-0000-4000-8000-000000000001","puntaje":1,"requiere_derivacion":false,"fecha":"2026-11-09T12:00:00+00:00"}"""))

        assertEquals(ResultadoAcceso.Estudiante, cuenta.acceder("ana@ejemplo.cl", "clave1234"))
        assertEquals(EstadoCuenta.Registrada("ana@ejemplo.cl"), cuenta.estado())
        assertEquals(NubeFalsa.CUENTA_UID, sync.sesion.sbUid)

        // La copia de la cuenta anónima anterior se borra con SU token
        val borrado = nube.de("POST", "/rest/v1/rpc/eliminar_mis_datos").single()
        assertEquals("Bearer sb-acceso", borrado.auth)

        val regs = db.registroBiometricoDao().getAllUnaVez()
        assertEquals(3, regs.size)
        assertEquals(22.9, regs.first { it.imc != null }.imc!!, 0.0)
        assertTrue(regs.first { it.imc != null }.pesoKg == null)      // el peso nunca estuvo en la nube
        assertEquals(71, regs.first { it.frecuenciaCardiaca != null }.frecuenciaCardiaca)
        assertEquals(6400, db.pasosDiaDao().get("2026-11-12")!!.pasos)
        assertEquals(1, db.resultadoScoffDao().getAllUnaVez().size)

        // Restaurar de nuevo no duplica
        cuenta.restaurar()
        assertEquals(3, db.registroBiometricoDao().getAllUnaVez().size)
        assertEquals(1, db.resultadoScoffDao().getAllUnaVez().size)
        // Lo local queda listo para subir a la cuenta
        assertTrue(db.syncPendienteDao().contar().first() > 0)
    }

    @Test fun iniciarSesion_contrasenaIncorrecta() = runBlocking {
        nube.cuentas["ana@ejemplo.cl"] = "clave1234"
        val r = cuenta.acceder("ana@ejemplo.cl", "otra") as ResultadoAcceso.Error
        assertEquals("Correo o contraseña incorrectos.", r.mensaje)
        assertEquals(EstadoCuenta.Anonima, cuenta.estado())
    }

    @Test fun reglas() {
        assertTrue(ReglasCuenta.emailValido("ana.perez@inacapmail.cl"))
        assertFalse(ReglasCuenta.emailValido("ana@"))
        assertFalse(ReglasCuenta.contrasenaValida("corta1"))
        assertFalse(ReglasCuenta.contrasenaValida("solamenteletras"))
        assertTrue(ReglasCuenta.contrasenaValida("clave1234"))
        assertEquals("No hubo respuesta. Revisa tu internet e intenta de nuevo.", ReglasCuenta.mensaje(ErrorNube(-1, "x")))
        assertTrue(ReglasCuenta.mensaje(ErrorNube(-1, "Sin respuesta (POST /auth/v1/token): SocketTimeoutException: timeout")).contains("tardó demasiado"))
        assertTrue(ReglasCuenta.mensaje(ErrorNube(500, "boom")).contains("500"))
        // 403: mensaje claro (no el técnico "error 403"), por inyección rechazada en el servidor/WAF.
        assertTrue(ReglasCuenta.mensaje(ErrorNube(403, "forbidden")).contains("seguridad"))
        assertFalse(ReglasCuenta.mensaje(ErrorNube(403, "forbidden")).contains("403"))
        assertEquals(1_763_028_000_000L, ReglasCuenta.fechaMs("2025-11-13T10:00:00+00:00"))
    }
}
