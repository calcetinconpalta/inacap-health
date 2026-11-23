package com.inacap.health

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.inacap.health.salud.EnvioBienestar
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tarea 4: PDF a Bienestar con destinatario fijo y límite de envíos. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EnvioBienestarTest {
    private val hora = 60 * 60_000L
    private val dia = 24 * hora

    @Test fun limitePorDiaYPorMes() {
        val ahora = 100 * dia
        assertEquals(3, EnvioBienestar.restantes(emptyList(), ahora))
        assertEquals(0, EnvioBienestar.restantes(listOf(ahora - hora, ahora - 2 * hora, ahora - 3 * hora), ahora))
        assertEquals("Los de ayer no cuentan para hoy", 3, EnvioBienestar.restantes(listOf(ahora - dia - hora), ahora))
        val mes = (1..9).map { ahora - it * 2 * dia }
        assertEquals("Queda 1 por el tope mensual", 1, EnvioBienestar.restantes(mes, ahora))
        assertEquals("Lo de hace más de 30 días ya no cuenta", 3, EnvioBienestar.restantes(listOf(ahora - 31 * dia), ahora))
    }

    @Test fun elCorreoVaConDestinatarioFijoYSoloAppsDeCorreo() {
        val i = EnvioBienestar.intent(Uri.parse("content://x/resumen.pdf"), "bienestar@inacap.cl")
        assertArrayEquals(arrayOf("bienestar@inacap.cl"), i.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals("application/pdf", i.type)
        assertEquals("mailto", i.selector?.data?.scheme)
    }
}
