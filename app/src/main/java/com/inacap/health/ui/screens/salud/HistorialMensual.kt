package com.inacap.health.ui.screens.salud

import com.inacap.health.data.PasosDia
import com.inacap.health.data.RegistroBiometrico
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Una semana del mes (semana 1 = días 1–7, semana 2 = 8–14…; la 5 son los días que sobran). */
data class SemanaResumen(
    val numero: Int,
    val desde: LocalDate,
    val hasta: LocalDate,
    /** Promedio de agua por día (ml), contando solo los días con registro. */
    val aguaMlPorDia: Int?,
    val pasosPorDia: Int?,
    val pulsoPromedio: Int?,
    /** Último IMC calculado en esa semana. */
    val imc: Double?
) {
    val vacia get() = aguaMlPorDia == null && pasosPorDia == null && pulsoPromedio == null && imc == null
}

/**
 * Tarea 18 · Historial por mes, semana a semana. Lógica pura (probada en la JVM).
 * El mes actual muestra solo hasta la semana de hoy; al empezar el mes siguiente parte de nuevo en la semana 1.
 */
object HistorialMensual {
    private val CL = Locale.forLanguageTag("es-CL")

    fun titulo(mes: YearMonth): String =
        mes.format(DateTimeFormatter.ofPattern("MMMM yyyy", CL)).replaceFirstChar { it.titlecase(CL) }

    fun puedeAvanzar(mes: YearMonth, hoy: LocalDate) = mes < YearMonth.from(hoy)

    /** Rangos de las semanas visibles del mes. */
    fun semanas(mes: YearMonth, hoy: LocalDate): List<Pair<Int, ClosedRange<LocalDate>>> {
        if (mes > YearMonth.from(hoy)) return emptyList()
        val ultimoDia = if (mes == YearMonth.from(hoy)) hoy.dayOfMonth else mes.lengthOfMonth()
        return (1..ultimoDia step 7).mapIndexed { i, inicio ->
            val fin = minOf(inicio + 6, mes.lengthOfMonth())
            (i + 1) to (mes.atDay(inicio)..mes.atDay(fin))
        }
    }

    fun resumir(
        mes: YearMonth,
        hoy: LocalDate,
        registros: List<RegistroBiometrico>,
        pasos: List<PasosDia>,
        zona: ZoneId = ZoneId.systemDefault()
    ): List<SemanaResumen> {
        fun dia(ms: Long) = Instant.ofEpochMilli(ms).atZone(zona).toLocalDate()
        return semanas(mes, hoy).map { (n, rango) ->
            val delaSemana = registros.filter { dia(it.createdAt) in rango }
            val aguaPorDia = delaSemana.filter { it.aguaMl > 0 }.groupBy { dia(it.createdAt) }.mapValues { (_, l) -> l.sumOf { it.aguaMl } }
            val pasosSemana = pasos.mapNotNull { p -> runCatching { LocalDate.parse(p.fecha) }.getOrNull()?.takeIf { it in rango }?.let { p.pasos } }
                .filter { it > 0 }
            val pulsos = delaSemana.mapNotNull { it.frecuenciaCardiaca }
            SemanaResumen(
                numero = n, desde = rango.start, hasta = rango.endInclusive,
                aguaMlPorDia = aguaPorDia.values.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                pasosPorDia = pasosSemana.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                pulsoPromedio = pulsos.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                imc = delaSemana.filter { it.imc != null }.maxByOrNull { it.createdAt }?.imc
            )
        }
    }
}
