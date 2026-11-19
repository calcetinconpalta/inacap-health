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
        for (g in porDia) {
            val fecha = g.startTime.toLocalDate()
            g.result[StepsRecord.COUNT_TOTAL]?.let { pasos ->
                if (pasos > 0) { app.pasos.registrarExterno(fecha.toString(), pasos.toInt()); importados++ }
            }
            g.result[HeartRateRecord.BPM_AVG]?.let { bpm -> guardarPulsoDelDia(fecha, bpm.toInt(), zona) }
        }
        prefs.edit { putLong("ultima", System.currentTimeMillis()); putBoolean("vinculado", true) }
        app.sync.registrarEvento("health_connect_sync", mapOf("dias" to importados))
        return importados
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
