package com.inacap.health.ui.screens.salud

import kotlin.math.round
import kotlin.math.roundToInt

object SaludLogic {
    // ---------- Agua en vasos y litros (tarea 15): nada de "ml" en pantalla ----------
    const val VASO_ML = 250
    /** Meta general cuando aún no hay peso registrado. */
    const val META_GENERAL_ML = 2000

    /** Vasos completos tomados (cada registro es un vaso). */
    fun vasos(ml: Int): Int = (ml.coerceAtLeast(0)) / VASO_ML

    /** Meta en vasos, redondeada (mínimo 1). 2000 ml → 8; 2300 ml → 9. */
    fun metaVasos(metaMl: Int): Int = (metaMl.toDouble() / VASO_ML).roundToInt().coerceAtLeast(1)

    /** Litros con coma y sin ceros de sobra: 750 → "0,75"; 2000 → "2"; 2300 → "2,3". */
    fun litros(ml: Int, decimales: Int = 2): String {
        val factor = if (decimales <= 1) 10.0 else 100.0
        val l = round(ml / 1000.0 * factor) / factor
        val txt = if (l == l.toLong().toDouble()) l.toLong().toString() else l.toString()
        return txt.replace('.', ',')
    }

    /** "Llevas 3 de 8 vasos (0,75 de 2 L)". */
    fun textoProgresoAgua(aguaMl: Int, metaMl: Int): String {
        val n = vasos(aguaMl)
        val meta = metaVasos(metaMl)
        return "Llevas $n de $meta ${if (meta == 1) "vaso" else "vasos"} (${litros(aguaMl)} de ${litros(metaMl, 1)} L)"
    }

    /** "Con tu peso te recomendamos unos 2,3 L al día (9 vasos)". */
    fun recomendacionAgua(metaMl: Int): String =
        "Con tu peso te recomendamos unos ${litros(metaMl, 1)} L al día (${metaVasos(metaMl)} vasos)"

    /** "1,8 L al día (7 vasos)", para promedios del historial. */
    fun litrosYVasos(ml: Int): String = "${litros(ml, 1)} L (${(ml.toDouble() / VASO_ML).roundToInt()} vasos)"

    fun calcularImc(pesoKg: Double, estaturaCm: Double): Double {
        if (estaturaCm <= 0.0) return 0.0
        val estaturaM = estaturaCm / 100.0
        val imc = pesoKg / (estaturaM * estaturaM)
        return round(imc * 10.0) / 10.0
    }

    fun categoriaImc(imc: Double): String {
        return when {
            imc < 18.5 -> "Por debajo del rango de referencia"
            imc <= 24.9 -> "Dentro del rango de referencia"
            else -> "Sobre el rango de referencia"
        }
    }

    fun mensajeImc(imc: Double): String {
        return if (imc in 18.5..24.9) {
            "¡Vas por buen camino! Mantén tus buenos hábitos diarios como realizar actividad física regular, mantenerte hidratado y asegurar un descanso adecuado."
        } else {
            "El IMC es solo un indicador general. Si tienes dudas, considera conversar con un profesional de salud; tu bienestar es lo importante."
        }
    }

    fun metaAguaMl(pesoKg: Double, nivel: String): Int {
        val extra = when (nivel.trim().lowercase()) {
            "bajo" -> 0
            "moderado" -> 350
            "alto" -> 700
            else -> 350
        }
        val base = pesoKg * 35.0 + extra
        val rounded = (round(base / 50.0) * 50).toInt()
        return if (rounded < 500) 500 else rounded
    }
}
