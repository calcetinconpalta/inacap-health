package com.inacap.health

import android.Manifest
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.inacap.health.data.AppDatabase
import com.inacap.health.data.remote.ConfigNube
import com.inacap.health.data.sync.SyncManager
import com.inacap.health.salud.AhorroBateria
import com.inacap.health.salud.CalculoPasos
import com.inacap.health.salud.PasosRepositorio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor

/** Tarea 5: con Inicio abierto los pasos se actualizan en vivo y al salir se deja de escuchar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PasosEnVivoTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var sm: SensorManager
    private lateinit var pasos: PasosRepositorio

    @Before fun preparar() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
        context.getSharedPreferences("pasos_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shadowOf(sm).addSensor(ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER))
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val sync = SyncManager(context, db, ConfigNube(supabaseUrl = "", supabaseAnonKey = "", firebaseApiKey = "", firebaseProjectId = ""))
        pasos = PasosRepositorio(context, db, sync)
    }

    @After fun cerrar() = db.close()

    private fun enviar(contador: Float) {
        val e = org.robolectric.shadows.SensorEventBuilder.newBuilder()
            .setSensor(sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)!!)
            .setValues(floatArrayOf(contador))
            .setTimestamp(System.nanoTime())
            .build()
        shadowOf(sm).sendSensorEventToListeners(e)
    }

    private suspend fun esperarPasos(esperado: Int) {
        repeat(100) {
            if ((db.pasosDiaDao().get(CalculoPasos.fecha())?.pasos ?: -1) == esperado) return
            delay(50)
        }
    }

    @Test fun cuentaEnVivoYDejaDeEscucharAlSalir() = runBlocking {
        val escucha = launch(Dispatchers.IO) { pasos.escucharEnVivo() }
        repeat(100) { if (shadowOf(sm).getListeners().isEmpty()) delay(20) }
        assertTrue("Debe estar escuchando el sensor", shadowOf(sm).getListeners().isNotEmpty())

        enviar(10_000f)                      // primera lectura: fija la referencia
        esperarPasos(0)
        delay(CalculoPasos.CADA_MS_EN_VIVO + 200)
        enviar(10_120f)                      // caminó 120 pasos
        esperarPasos(120)
        assertEquals(120, db.pasosDiaDao().get(CalculoPasos.fecha())?.pasos)

        escucha.cancelAndJoin()
        assertTrue("Al salir de Inicio se deja de escuchar", shadowOf(sm).getListeners().isEmpty())
    }

    @Test fun avisoDeBateriaSoloEnMarcasQueCierranApps() {
        assertTrue(AhorroBateria.marcaAgresiva("Xiaomi"))
        assertTrue(AhorroBateria.marcaAgresiva("HONOR"))
        assertFalse("El Motorola del usuario no lo necesita", AhorroBateria.marcaAgresiva("motorola"))
    }
}
