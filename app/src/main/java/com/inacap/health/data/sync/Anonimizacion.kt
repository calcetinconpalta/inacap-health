package com.inacap.health.data.sync

import com.inacap.health.data.Consentimiento
import com.inacap.health.data.PasosDia
import com.inacap.health.data.Perfil
import com.inacap.health.data.RegistroBiometrico
import com.inacap.health.data.ResultadoScoff
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Arma lo que se envía a la nube aplicando MINIMIZACIÓN de datos:
 * nunca se envían nombre, patologías, alergias, medicamentos, peso, estatura
 * ni las respuestas individuales del SCOFF. La edad se envía como rango.
 */
object Anonimizacion {

    val SEXOS_VALIDOS = setOf("Femenino", "Masculino", "Otro", "Prefiero no decir")

    fun rangoEdad(edad: Int?): String = when {
        edad == null -> "sin_dato"
        edad < 18 -> "<18"
        edad <= 24 -> "18-24"
        edad <= 29 -> "25-29"
        edad <= 39 -> "30-39"
        else -> "40+"
    }

    fun sexo(valor: String?): String = if (valor in SEXOS_VALIDOS) valor!! else "sin_dato"

    /** Mismos rangos de referencia OMS que la pantalla de IMC. */
    fun categoriaImc(imc: Double): String = when {
        imc < 18.5 -> "bajo_rango"
        imc <= 24.9 -> "en_rango"
        else -> "sobre_rango"
    }

    fun canalDerivacion(canal: String): String = when {
        canal.startsWith("CESFAM") -> "cesfam"
        canal.contains("correo") -> "bienestar_correo"
        else -> "bienestar_presencial"
    }

    fun fechaIso(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMs))

    // ---------- Filas para la base SQL (Supabase) ----------

    fun usuario(perfil: Perfil?, versionApp: String, ahora: Long = System.currentTimeMillis()): JSONObject =
        JSONObject()
            .put("rango_edad", rangoEdad(perfil?.edad))
            .put("sexo", sexo(perfil?.sexo))
            .put("sede", com.inacap.health.data.Sedes.paraNube(perfil?.sede))
            .put("version_app", versionApp)
            .put("plataforma", "android")
            .put("actualizado_en", fechaIso(ahora))

    /** null si el registro no tiene nada que valga la pena enviar. */
    fun registroBiometrico(r: RegistroBiometrico, nivelActividad: String? = null): JSONObject? {
        val base = JSONObject().put("fecha", fechaIso(r.createdAt))
        return when {
            r.imc != null && r.imc > 0 -> base
                .put("tipo", "imc")
                .put("imc", r.imc)
                .put("categoria_imc", categoriaImc(r.imc))
                .put("nivel_actividad", nivelActividad?.lowercase(Locale.ROOT) ?: JSONObject.NULL)
            r.aguaMl > 0 -> base
                .put("tipo", "agua")
                .put("agua_ml", r.aguaMl)
            r.frecuenciaCardiaca != null && r.frecuenciaCardiaca in 30..220 -> base
                .put("tipo", "pulso")
                .put("frecuencia_cardiaca", r.frecuenciaCardiaca)
                .put("fuente", r.fuente ?: "app")
            else -> null
        }
    }

    /** Pasos del día: se envía la fecha (sin hora) y el total. */
    fun pasosDia(p: PasosDia): JSONObject = JSONObject()
        .put("tipo", "pasos")
        .put("pasos", p.pasos)
        .put("fuente", p.fuente)
        .put("fecha", p.fecha + "T12:00:00.000Z")

    fun resultadoScoff(r: ResultadoScoff): JSONObject = JSONObject()
        .put("puntaje", r.puntaje)
        .put("requiere_derivacion", r.requiereDerivacion)
        .put("fecha", fechaIso(r.createdAt))

    /** Tarea 18f: solo el tipo, el puntaje y si hay alerta (nunca las respuestas). */
    fun cuestionario(r: com.inacap.health.data.ResultadoCuestionario): JSONObject = JSONObject()
        .put("tipo", r.tipo)
        .put("puntaje", r.puntaje)
        .put("alerta", r.alerta)
        .put("fecha", fechaIso(r.createdAt))

    fun consentimiento(c: Consentimiento): JSONObject = JSONObject()
        .put("tipo", c.tipo)
        .put("version", c.version)
        .put("aceptado", c.aceptado)
        .put("fecha", fechaIso(c.createdAt))

    fun derivacion(canal: String, codigo: String, epochMs: Long): JSONObject = JSONObject()
        .put("canal", canalDerivacion(canal))
        .put("codigo", codigo)
        .put("fecha", fechaIso(epochMs))

    // ---------- Documento para la base NoSQL (Firestore) ----------

    /** Las props se limitan a 10 claves con valores simples (las reglas de Firestore lo exigen). */
    /**
     * Únicos nombres de campo que la app manda en los eventos de uso. firestore.rules acepta
     * exactamente esta lista (EventosFirestoreTest lo verifica): si agregas un evento con un
     * campo nuevo, agrégalo en los DOS lugares o esos eventos dejarán de llegar.
     */
    val PROPS_PERMITIDAS = setOf(
        "ml", "calidad", "dias", "pantalla", "detalle", "tema", "canal", "requiere_derivacion",
        "version", "nube", "tipo", "activo", "intervalo", "categoria", "nivel_actividad"
    )
    const val LARGO_MAX_TEXTO = 120

    fun evento(
        tipo: String,
        props: Map<String, Any?>,
        epochMs: Long,
        versionApp: String,
        androidSdk: Int
    ): JSONObject {
        val p = JSONObject()
        props.entries.filter { it.key in PROPS_PERMITIDAS }.take(10).forEach { (k, v) ->
            p.put(
                k,
                when (v) {
                    null -> JSONObject.NULL
                    is Number, is Boolean -> v
                    else -> v.toString().take(LARGO_MAX_TEXTO)
                }
            )
        }
        return JSONObject()
            .put("tipo", tipo.take(40))
            .put("ts", epochMs)
            .put("props", p)
            .put("version_app", versionApp)
            .put("android_sdk", androidSdk)
    }
}
