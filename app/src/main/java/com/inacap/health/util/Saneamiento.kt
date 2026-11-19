package com.inacap.health.util

/**
 * Limpieza de lo que escribe la persona antes de enviarlo (el servidor vuelve a validar).
 * Quita caracteres de control e invisibles, recorta espacios y limita el largo.
 */
object Saneamiento {
    private val INVISIBLES = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F\\u200B-\\u200F\\u2028-\\u202E\\u2060-\\u2064\\uFEFF]")
    private val SALTOS = Regex("\\n{3,}")

    fun texto(t: String, maximo: Int): String =
        t.replace("\r\n", "\n").replace(INVISIBLES, "").replace(SALTOS, "\n\n").trim().take(maximo)

    fun linea(t: String, maximo: Int): String = texto(t.replace('\n', ' '), maximo)
}
