package com.inacap.health.salud

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.inacap.health.HealthApp
import com.inacap.health.data.AppDatabase
import com.inacap.health.data.PasosDia
import com.inacap.health.data.sync.SyncManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Cálculos puros (probados en la JVM). */
object CalculoPasos {
    const val META_POR_DEFECTO = 6000
    val METAS = listOf(4000, 6000, 8000, 10000)

    /**
     * El sensor entrega los pasos acumulados desde que se encendió el teléfono.
     * Devuelve cuántos pasos nuevos hubo desde la lectura anterior.
     */
    fun delta(anterior: Long?, actual: Long): Long = when {
        anterior == null -> 0              // primera lectura: solo fija la referencia
        actual >= anterior -> actual - anterior
        else -> actual                     // el contador volvió a 0: el teléfono se reinició
    }

    /** Pasos del día: el mayor entre el sensor y Health Connect (no se suman para no duplicar). */
    fun combinar(sensor: Int, healthConnect: Int) = maxOf(sensor, healthConnect)

    fun fecha(ms: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
}

/** HU-10 · Pasos diarios desde el contador del teléfono. */
class PasosRepositorio(
    private val context: Context,
    private val db: AppDatabase,
    private val sync: SyncManager
) {
    private val prefs = context.getSharedPreferences("pasos_prefs", Context.MODE_PRIVATE)

    var meta: Int
        get() = prefs.getInt("meta", CalculoPasos.META_POR_DEFECTO)
        set(v) = prefs.edit { putInt("meta", v) }

    val tieneSensor: Boolean
        get() = (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
            ?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    val tienePermiso: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Lee el sensor una vez y suma los pasos nuevos al día de hoy. */
    suspend fun muestrear() {
        if (!tieneSensor || !tienePermiso) return
        val actual = leerSensor() ?: return
        val anterior = if (prefs.contains("ultimo_contador")) prefs.getLong("ultimo_contador", 0) else null
        val delta = CalculoPasos.delta(anterior, actual)
        val hoy = CalculoPasos.fecha()
        val sensorHoy = (if (prefs.getString("sensor_fecha", null) == hoy) prefs.getInt("sensor_pasos", 0) else 0) + delta.toInt()
        prefs.edit {
            putLong("ultimo_contador", actual)
            putString("sensor_fecha", hoy)
            putInt("sensor_pasos", sensorHoy)
        }
        if (delta > 0 || anterior == null) guardar(hoy, sensorHoy, "sensor")
    }

    /** Pasos que llegan desde Health Connect (HU-07). */
    suspend fun registrarExterno(fecha: String, pasos: Int) = guardar(fecha, pasos, "health_connect")

    private suspend fun guardar(fecha: String, pasos: Int, fuente: String) {
        val dao = db.pasosDiaDao()
        val existente = dao.get(fecha)
        if (existente != null && existente.pasos >= pasos) return
        val dia = PasosDia(
            fecha = fecha,
            pasos = CalculoPasos.combinar(pasos, existente?.pasos ?: 0),
            fuente = fuente,
            uuid = existente?.uuid ?: java.util.UUID.randomUUID().toString()
        )
        dao.upsert(dia)
        sync.encolarPasos(dia)
    }

    private suspend fun leerSensor(): Long? {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return null
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { cont ->
                val escucha = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(e.values[0].toLong())
                    }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
                }
                sm.registerListener(escucha, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(escucha) }
            }
        }
    }

    /** Muestreo cada 15 minutos aunque la app esté cerrada. */
    fun programarMuestreo() {
        if (!tieneSensor || !tienePermiso) return
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "pasos", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PasosWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    fun borrarTodo() {
        prefs.edit(commit = true) { clear() }
        WorkManager.getInstance(context).cancelUniqueWork("pasos")
    }
}

class PasosWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as HealthApp).pasos.muestrear()
        return Result.success()
    }
}
