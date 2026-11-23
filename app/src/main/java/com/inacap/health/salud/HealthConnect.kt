package com.inacap.health.salud

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import java.time.Duration
import java.time.Instant
import androidx.health.connect.client.time.TimeRangeFilter
import com.inacap.health.HealthApp
import com.inacap.health.data.RegistroBiometrico
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.util.UUID

/** HU-07 · Estado de Health Connect, sin tipos de API 26 (se puede usar en cualquier versión). */
object HealthConnectDisponibilidad {
    enum class Estado { NO_SOPORTADO, NO_INSTALADO, ACTUALIZAR, DISPONIBLE }

    const val PAQUETE = "com.google.android.apps.healthdata"

    fun estado(context: Context): Estado {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return Estado.NO_SOPORTADO
        return when (HealthConnectClient.getSdkStatus(context, PAQUETE)) {
            HealthConnectClient.SDK_AVAILABLE -> Estado.DISPONIBLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Estado.ACTUALIZAR
            else -> if (Build.VERSION.SDK_INT >= 34) Estado.NO_SOPORTADO else Estado.NO_INSTALADO
        }
    }

    fun vinculado(context: Context) =
        context.getSharedPreferences("hc_prefs", Context.MODE_PRIVATE).getBoolean("vinculado", false)

    fun ultimaSincronizacion(context: Context) =
        context.getSharedPreferences("hc_prefs", Context.MODE_PRIVATE).getLong("ultima", 0)
}

/** Lo último que llegó del reloj (vía Health Connect), para mostrarlo en Inicio. */
data class DatosReloj(val bpm: Int?, val bpmEn: Long, val pasosHoy: Int?, val actualizadoEn: Long)

/** Lógica pura de la tarjeta del reloj (probada en la JVM). */
object RelojResumen {
    /** Cada cuánto se trae el reloj solo mientras Inicio está abierto. */
    const val CADA_MS = 5 * 60_000L

    /** "recién", "hace 8 min", "hace 2 h", "hace 3 días". */
    fun hace(desde: Long, ahora: Long): String {
        val min = ((ahora - desde).coerceAtLeast(0) / 60_000)
        return when {
            min < 1 -> "recién"
            min < 60 -> "hace $min min"
            min < 24 * 60 -> "hace ${min / 60} h"
            else -> "hace ${min / (24 * 60)} ${if (min / (24 * 60) == 1L) "día" else "días"}"
        }
    }

    fun textoPulso(d: DatosReloj, ahora: Long): String? =
        d.bpm?.let { "Pulso del reloj: $it lpm, ${hace(d.bpmEn, ahora)}" }

    /** Lee lo guardado; null si nunca se vinculó un reloj. */
    fun leer(prefs: android.content.SharedPreferences, hoy: String): DatosReloj? {
        if (!prefs.getBoolean("vinculado", false)) return null
        return DatosReloj(
            bpm = prefs.getInt("fc_ultimo", 0).takeIf { it in 30..220 },
            bpmEn = prefs.getLong("fc_ultimo_ms", 0),
            pasosHoy = prefs.getInt("pasos_reloj", 0).takeIf { it > 0 && prefs.getString("pasos_reloj_fecha", null) == hoy },
            actualizadoEn = prefs.getLong("ultima", 0)
        )
    }
}

/** Lectura de pasos y frecuencia cardíaca (solo Android 8+). */
@RequiresApi(Build.VERSION_CODES.O)
class HealthConnectRepositorio(private val context: Context) {
    private val app = context.applicationContext as HealthApp
    private val prefs = context.getSharedPreferences("hc_prefs", Context.MODE_PRIVATE)
    private val cliente by lazy { HealthConnectClient.getOrCreate(context) }

    val permisos = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )

    fun contratoPermisos() = PermissionController.createRequestPermissionResultContract()

    suspend fun tienePermisos(): Boolean =
        cliente.permissionController.getGrantedPermissions().containsAll(permisos)

    /** Importa los últimos [dias] días al historial. Devuelve cuántos días trajo. */
    suspend fun importar(dias: Int = 30): Int {
        if (!tienePermisos()) return 0
        val zona = ZoneId.systemDefault()
        val hoy = LocalDate.now(zona)
        val filtro = TimeRangeFilter.between(hoy.minusDays(dias.toLong() - 1).atStartOfDay(), hoy.plusDays(1).atStartOfDay())
        val porDia = cliente.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL, HeartRateRecord.BPM_AVG),
                timeRangeFilter = filtro,
                timeRangeSlicer = Period.ofDays(1)
            )
        )
        var importados = 0
        var pasosHoy = 0L
        for (g in porDia) {
            val fecha = g.startTime.toLocalDate()
            g.result[StepsRecord.COUNT_TOTAL]?.let { pasos ->
                if (fecha == hoy) pasosHoy = pasos
                if (pasos > 0) { app.pasos.registrarExterno(fecha.toString(), pasos.toInt()); importados++ }
            }
            g.result[HeartRateRecord.BPM_AVG]?.let { bpm -> guardarPulsoDelDia(fecha, bpm.toInt(), zona) }
        }
        val ultimoPulso = runCatching { ultimaMuestraPulso() }.getOrNull()
        prefs.edit {
            putLong("ultima", System.currentTimeMillis()); putBoolean("vinculado", true)
            putInt("pasos_reloj", pasosHoy.toInt()); putString("pasos_reloj_fecha", hoy.toString())
            ultimoPulso?.let { (bpm, en) -> putInt("fc_ultimo", bpm); putLong("fc_ultimo_ms", en) }
        }
        app.sync.registrarEvento("health_connect_sync", mapOf("dias" to importados))
        return importados
    }

    /** La muestra de pulso más reciente de las últimas 24 h (bpm, momento en ms). */
    private suspend fun ultimaMuestraPulso(): Pair<Int, Long>? {
        val respuesta = cliente.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.after(Instant.now().minus(Duration.ofHours(24))),
                ascendingOrder = false,
                pageSize = 5
            )
        )
        val muestra = respuesta.records.flatMap { it.samples }.maxByOrNull { it.time } ?: return null
        return muestra.beatsPerMinute.toInt() to muestra.time.toEpochMilli()
    }

    /** Un registro por día con el promedio; volver a importar lo actualiza (no duplica). */
    private suspend fun guardarPulsoDelDia(fecha: LocalDate, bpm: Int, zona: ZoneId) {
        if (bpm !in 30..220) return
        val dao = app.database.registroBiometricoDao()
        val uuid = UUID.nameUUIDFromBytes("health-connect-fc-$fecha".toByteArray()).toString()
        val existente = dao.getPorUuid(uuid)
        if (existente?.frecuenciaCardiaca == bpm) return
        val registro = (existente ?: RegistroBiometrico(
            pesoKg = null, estaturaCm = null, imc = null, aguaMl = 0, frecuenciaCardiaca = bpm,
            createdAt = fecha.atTime(LocalTime.NOON).atZone(zona).toInstant().toEpochMilli(),
            uuid = uuid, fuente = "health_connect"
        )).copy(frecuenciaCardiaca = bpm)
        dao.upsert(registro)
        app.sync.encolarRegistro(registro)
    }

    suspend fun desvincular() {
        runCatching { cliente.permissionController.revokeAllPermissions() }
        prefs.edit { clear() }
    }
}
