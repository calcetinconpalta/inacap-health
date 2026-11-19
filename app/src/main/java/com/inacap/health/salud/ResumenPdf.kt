package com.inacap.health.salud

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.inacap.health.data.PasosDia
import com.inacap.health.data.Perfil
import com.inacap.health.data.RegistroBiometrico
import com.inacap.health.data.ResultadoScoff
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Datos del resumen ya agrupados (lógica pura, probada en la JVM). */
data class DatosResumen(
    val desdeMs: Long,
    val hastaMs: Long,
    val nombre: String?,
    val edad: Int?,
    val sexo: String?,
    val imc: List<FilaImc>,
    val agua: List<Pair<String, Int>>,     // fecha -> ml del día
    val pasos: List<Pair<String, Int>>,    // fecha -> pasos
    val pulsos: List<FilaPulso>,
    val scoff: List<Pair<Long, Int>>?      // null = no incluido
) {
    data class FilaImc(val fechaMs: Long, val pesoKg: Double, val estaturaCm: Double, val imc: Double, val categoria: String)
    data class FilaPulso(val fechaMs: Long, val bpm: Int, val fuente: String)

    val aguaPromedio: Int get() = if (agua.isEmpty()) 0 else agua.sumOf { it.second } / agua.size
    val pasosPromedio: Int get() = if (pasos.isEmpty()) 0 else pasos.sumOf { it.second } / pasos.size

    companion object {
        fun construir(
            perfil: Perfil?,
            registros: List<RegistroBiometrico>,
            pasos: List<PasosDia>,
            scoff: List<ResultadoScoff>,
            dias: Int,
            incluirNombre: Boolean,
            incluirScoff: Boolean,
            ahora: Long = System.currentTimeMillis()
        ): DatosResumen {
            val desde = ahora - dias * 86_400_000L
            val enPeriodo = registros.filter { it.createdAt in desde..ahora }
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return DatosResumen(
                desdeMs = desde,
                hastaMs = ahora,
                nombre = perfil?.nombre?.takeIf { incluirNombre && it.isNotBlank() },
                edad = perfil?.edad,
                sexo = perfil?.sexo?.takeIf { it.isNotBlank() },
                imc = enPeriodo.filter { it.imc != null && it.pesoKg != null && it.estaturaCm != null }
                    .sortedBy { it.createdAt }
                    .map { FilaImc(it.createdAt, it.pesoKg!!, it.estaturaCm!!, it.imc!!, SaludLogicCompartida.categoriaImc(it.imc!!)) },
                agua = enPeriodo.filter { it.aguaMl > 0 }
                    .groupBy { fmt.format(Date(it.createdAt)) }
                    .map { (f, l) -> f to l.sumOf { it.aguaMl } }
                    .sortedBy { it.first },
                pasos = pasos.filter { it.fecha >= fmt.format(Date(desde)) && it.pasos > 0 }
                    .sortedBy { it.fecha }
                    .map { it.fecha to it.pasos },
                pulsos = enPeriodo.filter { it.frecuenciaCardiaca != null }
                    .sortedBy { it.createdAt }
                    .map { FilaPulso(it.createdAt, it.frecuenciaCardiaca!!, if (it.fuente == "health_connect" || it.fuente == "reloj") "Reloj" else "Cámara (estimación)") },
                scoff = if (incluirScoff) scoff.filter { it.createdAt in desde..ahora }.sortedBy { it.createdAt }.map { it.createdAt to it.puntaje } else null
            )
        }
    }
}

/** Rangos de referencia OMS (mismos textos que la app). */
object SaludLogicCompartida {
    fun categoriaImc(imc: Double) = when {
        imc < 18.5 -> "Bajo el rango de referencia"
        imc <= 24.9 -> "Dentro del rango de referencia"
        else -> "Sobre el rango de referencia"
    }
}

/** HU-12 · Dibuja el resumen en un PDF A4 (solo cuando el usuario lo pide). */
object ResumenPdf {
    private const val ANCHO = 595
    private const val ALTO = 842
    private const val MARGEN = 48f

    fun generar(context: Context, d: DatosResumen): File {
        val doc = PdfDocument()
        val lienzo = Lienzo(doc)
        val fechaLarga = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("es-CL"))
        val fechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-CL"))
        val fechaIso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val num = { n: Int -> String.format(Locale.forLanguageTag("es-CL"), "%,d", n) }
        val dec = { x: Double -> String.format(Locale.forLanguageTag("es-CL"), "%.1f", x) }
        val corto = { iso: String -> fechaIso.parse(iso)?.let { fechaLarga.format(it) } ?: iso }

        lienzo.titulo("Resumen de autocuidado · INACAP Health")
        lienzo.texto("Período: ${fechaLarga.format(Date(d.desdeMs))} al ${fechaLarga.format(Date(d.hastaMs))}", gris = true)
        lienzo.texto(listOfNotNull(d.nombre, d.edad?.let { "$it años" }, d.sexo).joinToString(" · ").ifBlank { "Datos personales no incluidos" }, gris = true)
        lienzo.espacio(6f)
        lienzo.texto("Este documento lo generó la persona con la app INACAP Health. Los valores son referencias generales y estimaciones; no constituyen un diagnóstico.", gris = true, tamano = 9f)
        lienzo.espacio(10f)

        lienzo.subtitulo("Peso e IMC")
        if (d.imc.isEmpty()) lienzo.texto("Sin registros en el período.", gris = true)
        else lienzo.tabla(
            listOf("Fecha", "Peso (kg)", "Estatura (cm)", "IMC", "Referencia OMS"),
            listOf(0.18f, 0.15f, 0.17f, 0.1f, 0.4f),
            d.imc.map { listOf(fechaLarga.format(Date(it.fechaMs)), dec(it.pesoKg), dec(it.estaturaCm), dec(it.imc), it.categoria) }
        )

        lienzo.subtitulo("Hidratación")
        if (d.agua.isEmpty()) lienzo.texto("Sin registros en el período.", gris = true)
        else {
            lienzo.texto("Promedio en días con registro: ${num(d.aguaPromedio)} ml (${d.agua.size} días).")
            lienzo.tabla(listOf("Fecha", "Agua (ml)"), listOf(0.5f, 0.5f), d.agua.takeLast(31).map { listOf(corto(it.first), num(it.second)) })
        }

        lienzo.subtitulo("Actividad (pasos)")
        if (d.pasos.isEmpty()) lienzo.texto("Sin registros en el período.", gris = true)
        else {
            lienzo.texto("Promedio diario: ${num(d.pasosPromedio)} pasos (${d.pasos.size} días).")
            lienzo.tabla(listOf("Fecha", "Pasos"), listOf(0.5f, 0.5f), d.pasos.takeLast(31).map { listOf(corto(it.first), num(it.second)) })
        }

        lienzo.subtitulo("Frecuencia cardíaca")
        if (d.pulsos.isEmpty()) lienzo.texto("Sin mediciones en el período.", gris = true)
        else lienzo.tabla(
            listOf("Fecha", "Latidos por minuto", "Fuente"), listOf(0.35f, 0.3f, 0.35f),
            d.pulsos.map { listOf(fechaHora.format(Date(it.fechaMs)), it.bpm.toString(), it.fuente) }
        )

        d.scoff?.let { s ->
            lienzo.subtitulo("Cuestionario SCOFF (autocuidado alimentario)")
            if (s.isEmpty()) lienzo.texto("Sin cuestionarios en el período.", gris = true)
            else {
                lienzo.texto("Puntaje de 0 a 5. Un puntaje de 2 o más sugiere conversarlo con un profesional; no es un diagnóstico.", gris = true, tamano = 9f)
                lienzo.tabla(listOf("Fecha", "Puntaje"), listOf(0.5f, 0.5f), s.map { listOf(fechaLarga.format(Date(it.first)), "${it.second} / 5") })
            }
        }
        lienzo.terminar()

        val carpeta = File(context.cacheDir, "resumenes").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val archivo = File(carpeta, "resumen_inacap_health_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(d.hastaMs))}.pdf")
        archivo.outputStream().use { doc.writeTo(it) }
        doc.close()
        return archivo
    }

    /** Escribe línea a línea y crea páginas nuevas cuando se acaba el espacio. */
    private class Lienzo(private val doc: PdfDocument) {
        private var numero = 0
        private var pagina: PdfDocument.Page = nueva()
        private var y = MARGEN
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)

        private fun nueva(): PdfDocument.Page {
            numero++
            return doc.startPage(PdfDocument.PageInfo.Builder(ANCHO, ALTO, numero).create())
        }

        private fun asegurar(alto: Float) {
            if (y + alto > ALTO - MARGEN) {
                pie(); doc.finishPage(pagina); pagina = nueva(); y = MARGEN
            }
        }

        private fun pie() {
            p.apply { textSize = 8f; color = 0xFF8A8A8A.toInt(); typeface = Typeface.DEFAULT }
            pagina.canvas.drawText("INACAP Health · página $numero", MARGEN, ALTO - 24f, p)
        }

        fun titulo(t: String) {
            asegurar(30f)
            p.apply { textSize = 18f; color = 0xFFD6001C.toInt(); typeface = Typeface.DEFAULT_BOLD }
            pagina.canvas.drawText(t, MARGEN, y + 18f, p); y += 30f
        }

        fun subtitulo(t: String) {
            espacio(10f); asegurar(26f)
            p.apply { textSize = 13f; color = 0xFF1A1A1A.toInt(); typeface = Typeface.DEFAULT_BOLD }
            pagina.canvas.drawText(t, MARGEN, y + 13f, p); y += 22f
        }

        fun texto(t: String, gris: Boolean = false, tamano: Float = 10.5f) {
            p.apply { textSize = tamano; color = if (gris) 0xFF6B6B6B.toInt() else 0xFF1A1A1A.toInt(); typeface = Typeface.DEFAULT }
            partir(t, ANCHO - 2 * MARGEN).forEach { linea ->
                asegurar(tamano + 5f)
                pagina.canvas.drawText(linea, MARGEN, y + tamano, p); y += tamano + 5f
            }
        }

        fun espacio(h: Float) { y += h }

        fun tabla(cabecera: List<String>, anchos: List<Float>, filas: List<List<String>>) {
            val total = ANCHO - 2 * MARGEN
            fun fila(celdas: List<String>, negrita: Boolean) {
                asegurar(18f)
                p.apply { textSize = 9.5f; color = if (negrita) 0xFF6B6B6B.toInt() else 0xFF1A1A1A.toInt(); typeface = if (negrita) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
                var x = MARGEN
                celdas.forEachIndexed { i, c ->
                    val ancho = anchos[i] * total
                    pagina.canvas.drawText(recortar(c, ancho - 6), x, y + 11f, p); x += ancho
                }
                y += 16f
                p.color = 0xFFE0E0E0.toInt()
                pagina.canvas.drawLine(MARGEN, y - 2, MARGEN + total, y - 2, p)
            }
            fila(cabecera, true)
            filas.forEach { fila(it, false) }
            y += 4f
        }

        private fun recortar(t: String, ancho: Float): String {
            if (p.measureText(t) <= ancho) return t
            var s = t
            while (s.isNotEmpty() && p.measureText("$s…") > ancho) s = s.dropLast(1)
            return "$s…"
        }

        private fun partir(t: String, ancho: Float): List<String> {
            val lineas = mutableListOf<String>()
            var actual = ""
            t.split(" ").forEach { palabra ->
                val prueba = if (actual.isEmpty()) palabra else "$actual $palabra"
                if (p.measureText(prueba) <= ancho) actual = prueba else { if (actual.isNotEmpty()) lineas += actual; actual = palabra }
            }
            if (actual.isNotEmpty()) lineas += actual
            return lineas
        }

        fun terminar() { pie(); doc.finishPage(pagina) }
    }
}
