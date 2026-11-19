package com.inacap.health

import android.app.Application
import com.inacap.health.data.AppDatabase
import com.inacap.health.data.sync.SyncManager
import com.inacap.health.reminders.ReminderScheduler
import com.inacap.health.salud.PasosRepositorio
import com.inacap.health.seguridad.BloqueoManager

class HealthApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val sync: SyncManager by lazy { SyncManager(this, database) }
    val bloqueo: BloqueoManager by lazy { BloqueoManager(this) }
    val pasos: PasosRepositorio by lazy { PasosRepositorio(this, database, sync) }
    val equipo: com.inacap.health.equipo.EquipoRepositorio by lazy { com.inacap.health.equipo.EquipoRepositorio(this) }

    override fun onCreate() {
        super.onCreate()
        com.inacap.health.ui.theme.Apariencia.cargar(this)
        instalarRegistroDeErrores()
        bloqueo.observarCicloDeVida()

        // Antes se reprogramaban SIEMPRE al abrir la app, lo que posponía el
        // siguiente aviso cada vez. Ahora solo si no hay una alarma pendiente.
        ReminderScheduler.reprogramarSiFalta(this)
        pasos.programarMuestreo()

        // Si la app se cerró por un error, se informa (sin datos personales).
        sync.sesion.errorAppPendiente?.let { resumen ->
            sync.registrarEvento("error_app", mapOf("detalle" to resumen))
            sync.sesion.errorAppPendiente = null
        }
        // Reintenta envíos o borrados que quedaron pendientes.
        if (sync.activo || sync.sesion.borradoRemotoPendiente != null) sync.programar()
    }

    /** Guarda el tipo de error y dónde ocurrió (sin mensajes, que podrían tener datos). */
    private fun instalarRegistroDeErrores() {
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
            try {
                val lugar = error.stackTrace.firstOrNull { it.className.startsWith("com.inacap") }
                    ?: error.stackTrace.firstOrNull()
                val resumen = buildString {
                    append(error.javaClass.name)
                    if (lugar != null) append(" @ ${lugar.className.substringAfterLast('.')}.${lugar.methodName}:${lugar.lineNumber}")
                }
                sync.sesion.errorAppPendiente = resumen.take(120)
            } catch (_: Throwable) {
                // Nunca fallar dentro del manejador de errores.
            }
            anterior?.uncaughtException(hilo, error)
        }
    }
}
