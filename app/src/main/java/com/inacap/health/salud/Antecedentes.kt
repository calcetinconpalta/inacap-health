package com.inacap.health.salud

import android.content.Context
import androidx.core.content.edit
import com.inacap.health.data.Perfil
import org.json.JSONObject

/**
 * Tarea 18d · Antecedentes de salud para quien atiende (patologías, alergias, medicamentos).
 *
 * - Compartirlos es OBLIGATORIO para hacer una consulta (orientación o apoyo), no para el resto de la app.
 * - Se cifran en el teléfono con la clave pública de Bienestar (como el contacto de las derivaciones):
 *   solo Bienestar los lee; ni la base ni los administradores. No se usan para estadísticas.
 * - Se suben al pedir atención y cada vez que se editan en Mis datos. Al revocar o eliminar, se borran.
 */
object Antecedentes {
    private const val ARCHIVO = "antecedentes_prefs"
    private const val K_ACEPTO = "acepto_compartir"
    const val VERSION = 1

    const val TITULO = "Necesitamos saber más de ti para darte la mejor atención"
    const val POR_QUE =
        "Quien te atiende debe conocer tus antecedentes (por ejemplo, hipertensión o alergia al paracetamol) para orientarte con seguridad y saber cuándo derivarte a un médico. " +
            "Se envían cifrados y solo los lee el equipo de Bienestar. Nunca se venden, publican ni usan para estadísticas."
    const val SIN_ACEPTAR =
        "Sin compartir tus antecedentes no podemos recibir tu consulta, porque quien te atiende necesita conocerlos para hacerlo con seguridad. El resto de la app sigue funcionando igual."

    fun aceptado(c: Context): Boolean = c.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).getBoolean(K_ACEPTO, false)

    fun marcarAceptado(c: Context, valor: Boolean) =
        c.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).edit(commit = true) { putBoolean(K_ACEPTO, valor) }

    /** Lo que se cifra: solo los tres antecedentes (nunca nombre, peso ni nada más). */
    fun json(perfil: Perfil?): JSONObject = JSONObject()
        .put("v", VERSION)
        .put("patologias", perfil?.patologias?.trim().orEmpty().take(300))
        .put("alergias", perfil?.alergias?.trim().orEmpty().take(300))
        .put("medicamentos", perfil?.medicamentos?.trim().orEmpty().take(300))
}
