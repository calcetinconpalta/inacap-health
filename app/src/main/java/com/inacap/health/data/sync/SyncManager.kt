package com.inacap.health.data.sync

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.inacap.health.BuildConfig
import com.inacap.health.data.AppDatabase
import com.inacap.health.data.Consentimiento
import com.inacap.health.data.PasosDia
import com.inacap.health.data.RegistroBiometrico
import com.inacap.health.data.ResultadoScoff
import com.inacap.health.data.SyncPendiente
import com.inacap.health.data.remote.ErrorNube
import com.inacap.health.data.remote.ExplicacionError
import kotlinx.coroutines.flow.first
import com.inacap.health.data.remote.FirestoreCliente
import com.inacap.health.data.remote.IdentidadApp
import com.inacap.health.data.remote.ConfigNube
import com.inacap.health.data.remote.NubeConfig
import com.inacap.health.data.remote.SesionNube
import com.inacap.health.data.remote.SupabaseCliente
import com.inacap.health.seguridad.CifradoE2E
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Sincronización offline-first con las dos bases de la nube:
 *  - SQL (Supabase/PostgreSQL): tablas estructuradas y relacionadas.
 *  - NoSQL (Firestore): eventos de uso, alto volumen y esquema flexible.
 *
 * Nada sale del teléfono si el usuario no activó "Compartir datos
 * anonimizados". Todo pasa primero por la bandeja local sync_pendiente.
 */
class SyncManager(
    private val context: Context,
    private val db: AppDatabase,
    private val config: ConfigNube = NubeConfig.actual
) {

    val sesion = SesionNube(context)
    private val outbox = db.syncPendienteDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val supabase by lazy {
        SupabaseCliente(config.supabaseUrl.trimEnd('/'), config.supabaseAnonKey, sesion)
    }
    private val firestore by lazy {
        FirestoreCliente(
            config.firebaseApiKey, config.firebaseProjectId, sesion,
            config.urlFirestore, config.urlIdentity, config.urlSecureToken,
            IdentidadApp.encabezados(context)
        )
    }

    /** Cantidad de cambios esperando ser enviados. */
    val pendientes: Flow<Int> = outbox.contar()

    /** true si el usuario aceptó compartir y hay al menos una base configurada. */
    val activo: Boolean
        get() = sesion.compartirNube && config.algunaConfigurada && !sesion.modoEquipo

    // ------------------------------------------------------------------
    // Encolar cambios
    // ------------------------------------------------------------------

    suspend fun encolarSql(
        tabla: String,
        registroId: String,
        payload: JSONObject?,
        operacion: String = OP_UPSERT
    ) {
        if (!activo || !config.sqlConfigurado) return
        if (operacion == OP_UPSERT && payload == null) return
        outbox.insert(
            SyncPendiente(
                destino = DESTINO_SQL,
                operacion = operacion,
                tabla = tabla,
                registroId = registroId,
                payload = (payload ?: JSONObject()).toString()
            )
        )
        programar()
    }

    suspend fun encolarRegistro(r: RegistroBiometrico, nivelActividad: String? = null) =
        encolarSql(TABLA_REGISTROS, r.uuid, Anonimizacion.registroBiometrico(r, nivelActividad))

    suspend fun encolarPasos(p: PasosDia) =
        encolarSql(TABLA_REGISTROS, p.uuid, Anonimizacion.pasosDia(p))

    suspend fun encolarBorradoRegistro(uuid: String) =
        encolarSql(TABLA_REGISTROS, uuid, null, OP_DELETE)

    suspend fun encolarScoff(r: ResultadoScoff) =
        encolarSql(TABLA_SCOFF, r.uuid, Anonimizacion.resultadoScoff(r))

    suspend fun encolarConsentimiento(c: Consentimiento) =
        encolarSql(TABLA_CONSENTIMIENTOS, c.uuid, Anonimizacion.consentimiento(c))

    suspend fun encolarDerivacion(canal: String, codigo: String) =
        encolarSql(
            TABLA_DERIVACIONES, UUID.randomUUID().toString(),
            Anonimizacion.derivacion(canal, codigo, System.currentTimeMillis())
        )

    /**
     * HU-06 · Solicitud de derivación con datos de contacto, cifrada de extremo a extremo.
     * Se envía aunque no se compartan estadísticas: la persona pidió ser contactada.
     * @return false si no está configurado (sin base SQL o sin clave pública de Bienestar).
     */
    suspend fun enviarDerivacionConContacto(canal: String, codigo: String, contacto: JSONObject): Boolean {
        if (!config.derivacionCifradaDisponible || sesion.modoEquipo) return false
        val sobre = CifradoE2E.cifrar(contacto.toString(), config.bienestarClavePublica)
        val payload = Anonimizacion.derivacion(canal, codigo, System.currentTimeMillis()).put("contacto_cifrado", sobre)
        outbox.insert(
            SyncPendiente(
                destino = DESTINO_SQL, operacion = OP_UPSERT_EXPLICITO, tabla = TABLA_DERIVACIONES,
                registroId = UUID.randomUUID().toString(), payload = payload.toString()
            )
        )
        programar()
        return true
    }

    /** Sesión anónima + fila "usuarios" lista (la usa el chat de orientación). */
    suspend fun asegurarUsuarioRemoto() = withContext(Dispatchers.IO) { enviarUsuario() }

    /** Cliente SQL para funciones en línea (chat). null si no está configurado. */
    val clienteSql: SupabaseCliente? get() = if (config.sqlConfigurado) supabase else null

    /** El perfil cambió: se reenvía la fila anonimizada (rango de edad y sexo). */
    suspend fun encolarActualizacionUsuario() =
        encolarSql(TABLA_USUARIOS, "usuario", JSONObject())

    /** Ejecuta en segundo plano (para pantallas sin ViewModel). */
    fun enSegundoPlano(bloque: suspend SyncManager.() -> Unit) {
        scope.launch { bloque() }
    }

    /** Evento de uso para la base NoSQL. No bloquea la interfaz. */
    fun registrarEvento(tipo: String, props: Map<String, Any?> = emptyMap()) {
        if (!activo || !config.nosqlConfigurado) return
        val datos = Anonimizacion.evento(
            tipo, props, System.currentTimeMillis(), BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT
        )
        scope.launch {
            outbox.insert(
                SyncPendiente(
                    destino = DESTINO_NOSQL,
                    operacion = OP_UPSERT,
                    tabla = COLECCION_EVENTOS,
                    registroId = UUID.randomUUID().toString(),
                    payload = datos.toString()
                )
            )
            programar()
        }
    }

    // ------------------------------------------------------------------
    // Consentimiento de nube
    // ------------------------------------------------------------------

    /** Activa el envío y sube el historial que ya existía en el teléfono. */
    suspend fun activarNube() {
        sesion.compartirNube = true
        if (!config.algunaConfigurada) return
        withContext(Dispatchers.IO) {
            db.consentimientoDao().getAllUnaVez().forEach { encolarConsentimiento(it) }
            db.registroBiometricoDao().getAllUnaVez().forEach { encolarRegistro(it) }
            db.resultadoScoffDao().getAllUnaVez().forEach { encolarScoff(it) }
            db.pasosDiaDao().getAllUnaVez().forEach { encolarPasos(it) }
        }
        programar()
    }

    /**
     * Deja de enviar y pide borrar en la nube todo lo enviado.
     * @param motivo "revocacion" (apagó el interruptor) o "eliminacion" (borró todo).
     */
    suspend fun desactivarNube(motivo: String) {
        val habiaEnviado = sesion.tieneSesion
        sesion.compartirNube = false
        // Al revocar se conservan las derivaciones pedidas; al eliminar todo, no.
        if (motivo == MOTIVO_ELIMINACION) outbox.deleteAll() else outbox.deleteNoExplicitos(OP_UPSERT_EXPLICITO)
        if (habiaEnviado && config.algunaConfigurada) {
            sesion.borradoRemotoPendiente = motivo
            programar()
        }
    }

    // ------------------------------------------------------------------
    // Envío (lo ejecuta SyncWorker en segundo plano)
    // ------------------------------------------------------------------

    fun programar(reemplazar: Boolean = false) {
        val pedido = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInitialDelay(3, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(NOMBRE_TRABAJO, if (reemplazar) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, pedido)
    }

    /** "Sincronizar ahora": envía en primer plano y devuelve el resultado en palabras. */
    suspend fun sincronizarAhora(): String {
        if (!config.algunaConfigurada) return "Compartir datos no está disponible en esta versión de la app."
        if (!sesion.compartirNube && outbox.contar().first() == 0) {
            return "No hay nada que enviar: «Compartir datos anonimizados» está desactivado (Perfil → Privacidad y mis datos)."
        }
        val antes = outbox.contar().first()
        return try {
            procesar()
            val despues = outbox.contar().first()
            "Listo: se enviaron ${antes - despues} cambios" + if (despues > 0) " (quedan $despues)." else "."
        } catch (e: Exception) {
            ExplicacionError.explicar(e.message, (e as? ErrorNube)?.codigo)
        }
    }

    /** Prueba cada base por separado y explica qué falta configurar. */
    suspend fun diagnosticar(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val r = mutableListOf<Pair<String, String>>()
        r += "Compartir datos" to if (sesion.compartirNube) "Activado" else "Desactivado: no se envían estadísticas"
        r += "Base SQL (Supabase)" to if (!config.sqlConfigurado) "Sin configurar en local.properties" else try {
            supabase.asegurarSesion()
            supabase.seleccionar(TABLA_USUARIOS, "select=id&limit=1")
            "Conectada ✓"
        } catch (e: Exception) { ExplicacionError.explicar(e.message, (e as? ErrorNube)?.codigo) }
        r += "Base NoSQL (Firestore)" to if (!config.nosqlConfigurado) "Sin configurar en local.properties" else try {
            firestore.asegurarSesion()
            firestore.crearDocumento(
                COLECCION_EVENTOS, UUID.randomUUID().toString(),
                Anonimizacion.evento("diagnostico", emptyMap(), System.currentTimeMillis(), BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT)
            )
            "Conectada ✓"
        } catch (e: Exception) { ExplicacionError.explicar(e.message, (e as? ErrorNube)?.codigo) }
        r += "Envíos pendientes" to outbox.contar().first().toString()
        r
    }

    /** Envía todo lo pendiente. Lanza [ErrorNube] transitorio para que el Worker reintente. */
    suspend fun procesar() = withContext(Dispatchers.IO) {
        if (sesion.modoEquipo) return@withContext   // nunca enviar datos de estudiante con la cuenta del equipo
        sesion.borradoRemotoPendiente?.let { motivo ->
            if (config.sqlConfigurado) supabase.eliminarMisDatos(motivo)
            if (config.nosqlConfigurado) firestore.eliminarMisDatos()
            // Al revocar se conserva la sesión SQL: así la persona puede borrar más tarde
            // las derivaciones que pidió. Al eliminar todo, no queda nada.
            if (motivo == MOTIVO_ELIMINACION) sesion.limpiarSesiones() else sesion.limpiarSesionFirebase()
            sesion.borradoRemotoPendiente = null
        }

        // Sin consentimiento de estadísticas solo se envía lo pedido explícitamente.
        if (!sesion.compartirNube) outbox.deleteNoExplicitos(OP_UPSERT_EXPLICITO)

        var usuarioEnviado = false
        while (true) {
            val lote = outbox.getLote(LOTE, MAX_INTENTOS)
            if (lote.isEmpty()) break
            for (item in lote) {
                try {
                    when (item.destino) {
                        DESTINO_SQL -> {
                            if (!usuarioEnviado) {
                                enviarUsuario()
                                usuarioEnviado = true
                            }
                            if (item.tabla == TABLA_USUARIOS) {
                                // Ya se envió arriba con los datos actuales del perfil.
                            } else if (item.operacion == OP_DELETE) {
                                supabase.eliminar(item.tabla, item.registroId)
                            } else {
                                val fila = JSONObject(item.payload).put("id", item.registroId)
                                supabase.upsert(item.tabla, JSONArray().put(fila))
                            }
                        }
                        DESTINO_NOSQL ->
                            firestore.crearDocumento(item.tabla, item.registroId, JSONObject(item.payload))
                    }
                    outbox.deleteById(item.id)
                    sesion.ultimoEnvio = System.currentTimeMillis()
                    sesion.ultimoErrorSync = null
                } catch (e: ErrorNube) {
                    sesion.ultimoErrorSync = e.message
                    // Sin red o servidor caído: se reintenta. Clave, tabla, regla o proveedor
                    // mal configurado: se DETIENE sin borrar nada (se enviará al corregirlo).
                    if (e.transitorio || e.esDeConfiguracion) throw e
                    descartarSiAgotado(item, e.message)
                } catch (e: JSONException) {
                    descartarSiAgotado(item, "JSON inválido")
                }
            }
        }
    }

    private suspend fun descartarSiAgotado(item: SyncPendiente, error: String?) {
        Log.w(TAG, "No se pudo enviar ${item.tabla}/${item.registroId}: $error")
        if (item.intentos + 1 >= MAX_INTENTOS) outbox.deleteById(item.id)
        else outbox.registrarFallo(item.id, error ?: "error")
    }

    /** La fila "usuarios" debe existir antes que sus registros (llave foránea). */
    private suspend fun enviarUsuario() {
        supabase.asegurarSesion()
        val uid = sesion.sbUid ?: throw ErrorNube(401, "Sesión sin id de usuario")
        // Sin consentimiento de estadísticas, la fila no lleva rango de edad ni sexo.
        val perfil = if (sesion.compartirNube) db.perfilDao().getPerfilUnaVez() else null
        val fila = Anonimizacion.usuario(perfil, BuildConfig.VERSION_NAME).put("id", uid)
        supabase.upsert(TABLA_USUARIOS, JSONArray().put(fila))
    }

    companion object {
        private const val TAG = "SyncManager"
        const val NOMBRE_TRABAJO = "sync_nube"
        const val DESTINO_SQL = "sql"
        const val DESTINO_NOSQL = "nosql"
        const val OP_UPSERT = "upsert"
        const val OP_DELETE = "delete"
        const val OP_UPSERT_EXPLICITO = "upsert_explicito"
        const val TABLA_USUARIOS = "usuarios"
        const val TABLA_REGISTROS = "registros_biometricos"
        const val TABLA_SCOFF = "resultados_scoff"
        const val TABLA_CONSENTIMIENTOS = "consentimientos"
        const val TABLA_DERIVACIONES = "derivaciones"
        const val COLECCION_EVENTOS = "eventos"
        const val MOTIVO_REVOCACION = "revocacion"
        const val MOTIVO_ELIMINACION = "eliminacion"
        private const val LOTE = 50
        private const val MAX_INTENTOS = 3
    }
}
