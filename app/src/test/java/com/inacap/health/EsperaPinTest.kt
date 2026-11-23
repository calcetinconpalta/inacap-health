package com.inacap.health

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.inacap.health.seguridad.BloqueoManager
import com.inacap.health.seguridad.Pin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tarea 7: cambiar la hora o reiniciar el teléfono no acorta la espera tras fallar el PIN. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EsperaPinTest {
    private lateinit var context: Context
    private var monotono = 1_000_000L
    private var arranque = 7

    private fun gestor() = BloqueoManager(context, relojMonotono = { monotono }, numeroArranque = { arranque })

    @Before fun preparar() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("bloqueo_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun fallarCinco(b: BloqueoManager) {
        b.configurarPin("2580")
        repeat(5) { assertFalse(b.verificarPin("0000")) }
    }

    @Test fun reglaPura() {
        assertEquals(20_000, Pin.esperaRestante(30_000, 100, 1, 10_100, 1))
        assertEquals(0, Pin.esperaRestante(30_000, 100, 1, 99_000, 1))
        assertEquals("Reinició: cuenta desde el encendido", 25_000, Pin.esperaRestante(30_000, 900_000, 1, 5_000, 2))
        assertEquals("Monótono retrocedió = reinicio", 25_000, Pin.esperaRestante(30_000, 900_000, -1, 5_000, -1))
        assertEquals(0, Pin.esperaRestante(0, 0, 0, 0, 0))
    }

    @Test fun cambiarLaHoraNoAnulaLaEspera() {
        val b = gestor()
        fallarCinco(b)
        assertEquals(30_000, b.esperaRestante())
        // Adelantar la hora del teléfono no mueve el reloj monótono: la espera sigue igual.
        assertEquals(30_000, b.esperaRestante())
        assertFalse("Con espera no se acepta ni el PIN correcto", b.verificarPin("2580"))
        monotono += 30_001
        assertTrue(b.verificarPin("2580"))
    }

    @Test fun reiniciarElTelefonoNoSaltaLaEspera() {
        fallarCinco(gestor())
        monotono = 2_000        // el teléfono se reinició: 2 s desde el encendido
        arranque = 8
        val despues = gestor()  // la app se vuelve a abrir
        assertEquals(28_000, despues.esperaRestante())
        assertFalse(despues.verificarPin("2580"))
    }
}
