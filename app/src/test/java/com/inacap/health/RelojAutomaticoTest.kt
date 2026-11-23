package com.inacap.health

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.inacap.health.salud.DatosReloj
import com.inacap.health.salud.RelojResumen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tarea 6: el reloj se muestra en Inicio con "hace N min" y sin datos viejos de otro día. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RelojAutomaticoTest {
    private val min = 60_000L

    @Test fun haceCuanto() {
        assertEquals("recién", RelojResumen.hace(0, 30_000))
        assertEquals("hace 8 min", RelojResumen.hace(0, 8 * min))
        assertEquals("hace 2 h", RelojResumen.hace(0, 125 * min))
        assertEquals("hace 1 día", RelojResumen.hace(0, 25 * 60 * min))
        assertEquals("Pulso del reloj: 72 lpm, hace 8 min", RelojResumen.textoPulso(DatosReloj(72, 0, null, 0), 8 * min))
        assertNull(RelojResumen.textoPulso(DatosReloj(null, 0, null, 0), 8 * min))
    }

    @Test fun soloMuestraLosPasosDeHoy() {
        val p = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("hc_test", Context.MODE_PRIVATE)
        p.edit().clear().commit()
        assertNull("Sin reloj vinculado no hay tarjeta", RelojResumen.leer(p, "2026-11-23"))
        p.edit().putBoolean("vinculado", true).putInt("pasos_reloj", 4321).putString("pasos_reloj_fecha", "2026-11-22")
            .putInt("fc_ultimo", 71).putLong("fc_ultimo_ms", 5).commit()
        val d = RelojResumen.leer(p, "2026-11-23")!!
        assertNull("Los pasos de ayer no se muestran como de hoy", d.pasosHoy)
        assertEquals(71, d.bpm)
        assertEquals(4321, RelojResumen.leer(p, "2026-11-22")!!.pasosHoy)
    }
}
