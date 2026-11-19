package com.inacap.health.salud

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * HU-05 · Estimación del pulso por fotopletismografía (PPG) con la cámara.
 *
 * Con el dedo sobre la lente y la linterna encendida, cada latido cambia un poco
 * cuánta luz roja atraviesa el dedo. Aquí se toma esa señal (brillo rojo medio de
 * cada cuadro), se limpia y se cuentan los latidos. Es una ESTIMACIÓN: no es un
 * dispositivo médico.
 */
object Fotopletismografia {
    const val DURACION_MS = 20_000L
    const val BPM_MIN = 40
    const val BPM_MAX = 180

    enum class Calidad { BUENA, REGULAR, INSUFICIENTE }

    data class Resultado(val bpm: Int?, val calidad: Calidad, val latidos: Int)

    /** El dedo cubre la lente si la imagen es rojiza y brillante (luz de la linterna a través del dedo). */
    fun dedoDetectado(rojoMedio: Double, verdeMedio: Double): Boolean =
        rojoMedio >= 90 && rojoMedio >= verdeMedio * 1.4

    /** Promedios de rojo y verde desde un cuadro YUV (submuestreado). */
    fun rojoYVerde(yMedio: Double, uMedio: Double, vMedio: Double): Pair<Double, Double> {
        val r = yMedio + 1.402 * (vMedio - 128)
        val g = yMedio - 0.344 * (uMedio - 128) - 0.714 * (vMedio - 128)
        return r.coerceIn(0.0, 255.0) to g.coerceIn(0.0, 255.0)
    }

    /**
     * @param tiemposMs instante de cada muestra (milisegundos, creciente).
     * @param valores brillo rojo medio de cada cuadro.
     */
    fun estimar(tiemposMs: LongArray, valores: DoubleArray): Resultado {
        val n = minOf(tiemposMs.size, valores.size)
        if (n < 60 || tiemposMs[n - 1] - tiemposMs[0] < 8_000) return Resultado(null, Calidad.INSUFICIENTE, 0)

        // 1) Quitar la tendencia lenta (presión del dedo, cambios de exposición): ventana ~1,5 s.
        val sinTendencia = DoubleArray(n) { i -> valores[i] - mediaVentana(tiemposMs, valores, n, i, 750) }
        // 2) Suavizar el ruido rápido: ventana ~0,15 s.
        val suave = DoubleArray(n) { i -> mediaVentana(tiemposMs, sinTendencia, n, i, 75) }

        // 3) Picos: máximos locales sobre un umbral, separados al menos por el latido más rápido posible.
        val desv = sqrt(suave.sumOf { it * it } / n)
        if (desv < 1e-6) return Resultado(null, Calidad.INSUFICIENTE, 0)
        val distanciaMin = 60_000L / BPM_MAX
        val picos = mutableListOf<Int>()
        for (i in 1 until n - 1) {
            if (suave[i] > 0.3 * desv && suave[i] >= suave[i - 1] && suave[i] > suave[i + 1]) {
                val ultimo = picos.lastOrNull()
                if (ultimo == null || tiemposMs[i] - tiemposMs[ultimo] >= distanciaMin) picos += i
                else if (suave[i] > suave[ultimo]) picos[picos.size - 1] = i
            }
        }

        // 4) Intervalos entre latidos dentro del rango fisiológico.
        val intervalos = picos.zipWithNext { a, b -> (tiemposMs[b] - tiemposMs[a]).toDouble() }
            .filter { it in (60_000.0 / BPM_MAX)..(60_000.0 / BPM_MIN) }
        if (intervalos.size < 6) return Resultado(null, Calidad.INSUFICIENTE, picos.size)

        val mediana = intervalos.sorted().let { s -> if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }
        val bpm = (60_000.0 / mediana).roundToInt()
        // 5) Calidad: qué tan regulares son los intervalos (desviación absoluta relativa a la mediana).
        val variacion = intervalos.map { abs(it - mediana) }.sorted()[intervalos.size / 2] / mediana
        val calidad = when {
            bpm !in BPM_MIN..BPM_MAX -> Calidad.INSUFICIENTE
            variacion < 0.08 -> Calidad.BUENA
            variacion < 0.18 -> Calidad.REGULAR
            else -> Calidad.INSUFICIENTE
        }
        return Resultado(if (calidad == Calidad.INSUFICIENTE) null else bpm, calidad, picos.size)
    }

    private fun mediaVentana(t: LongArray, v: DoubleArray, n: Int, i: Int, radioMs: Long): Double {
        var suma = 0.0
        var c = 0
        var j = i
        while (j >= 0 && t[i] - t[j] <= radioMs) { suma += v[j]; c++; j-- }
        j = i + 1
        while (j < n && t[j] - t[i] <= radioMs) { suma += v[j]; c++; j++ }
        return suma / c
    }
}
