package com.inacap.health.data.remote

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpRespuesta(val codigo: Int, val cuerpo: String) {
    val ok: Boolean get() = codigo in 200..299
}

/** Error al hablar con la nube. [codigo] = -1 si no hubo conexión. */
class ErrorNube(val codigo: Int, mensaje: String, val deConfiguracion: Boolean = false) : Exception(mensaje) {
    /** Vale la pena reintentar más tarde (sin red, servidor caído o saturado). */
    val transitorio: Boolean
        get() = !deConfiguracion && (codigo == -1 || codigo == 408 || codigo == 429 || codigo >= 500)

    /** Falla del proyecto en la nube (clave, tabla, regla o proveedor): reintentar no sirve hasta corregirlo. */
    val esDeConfiguracion: Boolean
        get() = deConfiguracion || codigo == 401 || codigo == 403 || codigo == 404
}

/** Cliente HTTP mínimo con HttpURLConnection (sin librerías externas). */
object Http {
    private const val TIMEOUT_MS = 15_000

    fun enviar(
        metodo: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        cuerpo: String? = null,
        contentType: String = "application/json"
    ): HttpRespuesta {
        val conexion = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw ErrorNube(-1, "Sin conexión: ${e.javaClass.simpleName}")
        }
        try {
            conexion.requestMethod = metodo
            conexion.connectTimeout = TIMEOUT_MS
            conexion.readTimeout = TIMEOUT_MS
            conexion.useCaches = false
            conexion.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> conexion.setRequestProperty(k, v) }
            if (cuerpo != null) {
                conexion.doOutput = true
                conexion.setRequestProperty("Content-Type", "$contentType; charset=utf-8")
                conexion.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
            }
            val codigo = conexion.responseCode
            val stream = if (codigo >= 400) conexion.errorStream else conexion.inputStream
            val texto = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return HttpRespuesta(codigo, texto)
        } catch (e: IOException) {
            throw ErrorNube(-1, "Sin conexión: ${e.javaClass.simpleName}")
        } finally {
            conexion.disconnect()
        }
    }

    /** Lanza [ErrorNube] si la respuesta no es 2xx. */
    fun exigirOk(respuesta: HttpRespuesta, contexto: String, deConfiguracion: Boolean = false): HttpRespuesta {
        if (!respuesta.ok) {
            val config = deConfiguracion && respuesta.codigo != 429 && respuesta.codigo < 500
            throw ErrorNube(respuesta.codigo, "$contexto: HTTP ${respuesta.codigo} ${respuesta.cuerpo.take(300)}", config)
        }
        return respuesta
    }
}
