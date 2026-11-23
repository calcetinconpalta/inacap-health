package com.inacap.health.salud

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.inacap.health.BuildConfig

/**
 * "Enviar a Bienestar": abre la app de correo del propio estudiante con el PDF adjunto y el
 * destinatario FIJO (BIENESTAR_EMAIL). Nada se envía desde el servidor.
 *
 * El límite es local (el correo sale de la cuenta del estudiante, no de la nube): sirve para
 * no saturar la bandeja de Bienestar por error, no como barrera de seguridad.
 */
object EnvioBienestar {
    const val MAX_POR_DIA = 3
    const val MAX_POR_MES = 10
    private const val DIA_MS = 24 * 60 * 60_000L
    private const val MES_MS = 30 * DIA_MS

    val correo: String get() = BuildConfig.BIENESTAR_EMAIL.trim()
    val configurado: Boolean get() = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(correo)

    /** Cuántos envíos quedan hoy (lógica pura: [historial] son las fechas de envíos anteriores). */
    fun restantes(historial: List<Long>, ahora: Long): Int {
        val hoy = historial.count { ahora - it in 0 until DIA_MS }
        val mes = historial.count { ahora - it in 0 until MES_MS }
        return minOf(MAX_POR_DIA - hoy, MAX_POR_MES - mes).coerceAtLeast(0)
    }

    private fun prefs(c: Context) = c.getSharedPreferences("envio_bienestar", Context.MODE_PRIVATE)

    fun historial(c: Context): List<Long> =
        prefs(c).getString("envios", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    fun registrar(c: Context, ahora: Long = System.currentTimeMillis()) {
        val vigentes = historial(c).filter { ahora - it < MES_MS } + ahora
        prefs(c).edit { putString("envios", vigentes.joinToString(",")) }
    }

    /** Intento de correo con el PDF adjunto; el selector "mailto:" deja solo apps de correo. */
    fun intent(pdf: Uri, destinatario: String = correo): Intent =
        Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(destinatario))
            .putExtra(Intent.EXTRA_SUBJECT, "Resumen de salud · INACAP Health")
            .putExtra(Intent.EXTRA_TEXT, "Hola, adjunto mi resumen de INACAP Health para que el equipo de Bienestar lo revise.\n")
            .putExtra(Intent.EXTRA_STREAM, pdf)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")) }
}
