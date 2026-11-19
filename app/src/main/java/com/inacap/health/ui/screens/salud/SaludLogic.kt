package com.inacap.health.ui.screens.salud

import kotlin.math.round

object SaludLogic {
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
