package com.inacap.health

import android.app.Application
import com.inacap.health.equipo.ConversacionEquipo
import com.inacap.health.equipo.MonitoreoReglas
import com.inacap.health.equipo.RolEquipo
import com.inacap.health.ui.screens.equipo.PestanaEquipo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tarea 3: cada rol ve sus pestañas y el monitoreo del tutor cuenta bien, sin nombres. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DatosPorRolTest {
    private fun conv(id: String, tema: String = "alimentacion", estado: String = "abierta", tipo: String = "orientacion", tutor: String? = null) =
        ConversacionEquipo(id, tema, estado, tipo, tutor, null, "2026-11-23T10:00:00Z")

    @Test fun pestanasPorRol() {
        assertTrue(PestanaEquipo.MONITOREO in PestanaEquipo.de(RolEquipo.TUTOR))
        assertTrue(PestanaEquipo.BITACORA in PestanaEquipo.de(RolEquipo.DOCENTE))
        assertFalse("Bienestar no ve el monitoreo de orientación", PestanaEquipo.MONITOREO in PestanaEquipo.de(RolEquipo.PROFESIONAL))
        val admin = PestanaEquipo.de(RolEquipo.ADMIN)
        assertFalse("El administrador no ve chats", PestanaEquipo.CHATS in admin)
        assertTrue(admin.containsAll(listOf(PestanaEquipo.RESUMEN, PestanaEquipo.EQUIPO, PestanaEquipo.SEGURIDAD)))
        RolEquipo.entries.forEach { assertEquals("Cuenta siempre al final", PestanaEquipo.CUENTA, PestanaEquipo.de(it).last()) }
    }

    @Test fun bienestarNoSeLlamaProfesionalDeSalud() {
        assertEquals("Bienestar", RolEquipo.PROFESIONAL.nombre)
    }

    @Test fun monitoreoCuentaAbiertasPorTemaYEspera() {
        val convs = listOf(
            conv("a"), conv("b", tutor = "t1"), conv("c", tema = "hidratacion", tutor = "t1"),
            conv("d", estado = "cerrada"), conv("e", tipo = "atencion")
        )
        // a: sin mensajes (espera); b: último del tutor (no espera); c: último del estudiante (espera)
        val m = MonitoreoReglas.calcular(convs, mapOf("b" to "tutor", "c" to "estudiante"))
        assertEquals(3, m.abiertas)
        assertEquals(1, m.sinTutor)
        assertEquals(2, m.esperanRespuesta)
        assertEquals(mapOf("alimentacion" to 2, "hidratacion" to 1), m.porTema)
    }

    @Test fun leeElResumenDelAdministrador() {
        val j = JSONObject("""{"periodo_dias":30,"usuarios":{"total":40,"nuevos":5,"activos":12},
            "orientacion":{"conversaciones":7,"abiertas":3,"sin_tutor":1},"derivaciones":{"total":2},
            "scoff":{"tests":9,"sugiere_derivacion":2},"pulso":{"promedio":null},"pasos":{"promedio_diario":6500}}""")
        val r = MonitoreoReglas.resumenDe(j)
        assertEquals(40, r.usuarios); assertEquals(12, r.activos); assertEquals(1, r.sinTutor)
        assertEquals(2, r.derivaciones); assertNull(r.pulsoPromedio); assertEquals(6500, r.pasosPromedio)
    }

    @Test fun leeLaSeguridad() {
        val s = MonitoreoReglas.seguridadDe(JSONObject("""{"rechazos_24h":4,"ips_bloqueadas":[{"ip":"1.2.3.4","hasta":"x","motivo":null}],
            "auditoria":[{"fecha":"f","accion":"rol_asignado","actor":"Ana"}]}"""))
        assertEquals(4, s.rechazos24h); assertEquals("1.2.3.4", s.ips[0].ip); assertNull(s.ips[0].motivo)
        assertEquals("Ana", s.auditoria[0].actor)
    }
}
