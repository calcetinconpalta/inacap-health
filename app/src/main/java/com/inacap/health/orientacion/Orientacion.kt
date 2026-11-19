package com.inacap.health.orientacion

import android.content.Context
import androidx.core.content.edit
import com.inacap.health.HealthApp
import com.inacap.health.data.remote.ErrorNube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

data class Conversacion(val id: String, val tema: String, val estado: String, val actualizadaEn: String, val tipo: String = "orientacion")
data class Mensaje(val id: String, val rol: String, val texto: String, val creadoEn: String)

object Temas {
    val TODOS = listOf(
        "alimentacion" to "Alimentación",
        "hidratacion" to "Hidratación",
        "actividad" to "Actividad física",
        "habitos" to "Hábitos y descanso",
        "otro" to "Otro"
    )
    private val TODOS_CON_ATENCION = TODOS + ("apoyo_profesional" to "Atención profesional")
    fun nombre(clave: String) = TODOS_CON_ATENCION.firstOrNull { it.first == clave }?.second ?: clave
}

/** Detecta mensajes que merecen mostrar recursos de ayuda profesional (no bloquea el envío). */
object SenalesDeAlerta {
    private val frases = listOf(
        "suicid", "matarme", "no quiero vivir", "quitarme la vida", "hacerme daño", "autolesion",
        "vomit", "provoco el vómito", "provocar el vómito", "laxante", "atracón", "atracon",
        "dejé de comer", "deje de comer", "no como nada", "ayuno de días"
    )
    fun hay(texto: String): Boolean = texto.lowercase().let { t -> frases.any { t.contains(it) } }
}

/**
 * HU-13 · Orientación entre pares: la persona conversa con un tutor par
 * (estudiante de nutrición supervisado por un docente). Solo orientación educativa.
 * Necesita internet: los mensajes viven en la base SQL, protegidos por RLS.
 */
class OrientacionRepositorio(private val context: Context) {
    private val app = context.applicationContext as HealthApp
    private val prefs = context.getSharedPreferences("orientacion_prefs", Context.MODE_PRIVATE)

    val disponible: Boolean get() = app.sync.clienteSql != null

    var aceptado: Boolean
        get() = prefs.getBoolean("aceptado", false)
        set(v) = prefs.edit { putBoolean("aceptado", v) }

    private fun sql() = app.sync.clienteSql ?: throw ErrorNube(0, "La orientación no está configurada")
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun conversaciones(): List<Conversacion> = withContext(Dispatchers.IO) {
        val filas = sql().seleccionar("conversaciones", "select=id,tema,estado,tipo,actualizada_en&order=actualizada_en.desc")
        (0 until filas.length()).map { i ->
            filas.getJSONObject(i).let { Conversacion(it.getString("id"), it.getString("tema"), it.getString("estado"), it.getString("actualizada_en"), it.optString("tipo", "orientacion")) }
        }
    }

    suspend fun mensajes(conversacionId: String): List<Mensaje> = withContext(Dispatchers.IO) {
        val filas = sql().seleccionar("mensajes", "select=id,rol_autor,texto,creado_en&conversacion_id=eq.${enc(conversacionId)}&order=creado_en.asc")
        (0 until filas.length()).map { i ->
            filas.getJSONObject(i).let { Mensaje(it.getString("id"), it.getString("rol_autor"), it.getString("texto"), it.getString("creado_en")) }
        }
    }

    suspend fun abrir(tema: String, primerMensaje: String): String = withContext(Dispatchers.IO) {
        app.sync.asegurarUsuarioRemoto()
        val conv = sql().insertar("conversaciones", JSONObject().put("tema", tema))
        val id = conv.getString("id")
        enviarInterno(id, primerMensaje)
        app.sync.registrarEvento("orientacion_abierta", mapOf("tema" to tema))
        id
    }

    suspend fun enviar(conversacionId: String, texto: String) = withContext(Dispatchers.IO) { enviarInterno(conversacionId, texto) }

    private fun enviarInterno(conversacionId: String, texto: String) {
        val limpio = com.inacap.health.util.Saneamiento.texto(texto, 1000)
        if (limpio.isEmpty()) return
        sql().insertar(
            "mensajes",
            JSONObject().put("conversacion_id", conversacionId).put("rol_autor", "estudiante").put("texto", limpio)
        )
    }

    suspend fun cerrar(conversacionId: String) = withContext(Dispatchers.IO) {
        sql().rpc("cambiar_estado_conversacion", JSONObject().put("p_id", conversacionId).put("p_estado", "cerrada"))
    }

    suspend fun borrarTodo() = withContext(Dispatchers.IO) {
        if (app.sync.sesion.sbRefreshToken != null) sql().rpc("borrar_mis_conversaciones")
        aceptado = false
    }
}
