package com.inacap.health.ui.screens.perfil

import androidx.core.content.edit
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inacap.health.HealthApp
import com.inacap.health.data.Consentimiento
import com.inacap.health.data.Perfil
import com.inacap.health.data.TipoConsentimiento
import com.inacap.health.data.remote.NubeConfig
import com.inacap.health.data.remote.SesionNube
import com.inacap.health.data.sync.SyncManager
import com.inacap.health.legal.TextosLegales
import com.inacap.health.reminders.ReminderScheduler
import com.inacap.health.util.observar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PerfilUiState {
    object Loading : PerfilUiState
    data class Success(val perfil: Perfil?) : PerfilUiState
}

/** Lo que la pantalla de privacidad muestra sobre la nube. */
data class EstadoNube(
    val configurada: Boolean = NubeConfig.algunaConfigurada,
    val compartiendo: Boolean = false,
    val pendientes: Int = 0,
    val ultimoEnvio: Long = 0,
    val ultimoError: String? = null,
    val idAnonimo: String? = null,
    val borradoPendiente: Boolean = false
)

/** El usuario debe (re)aceptar los términos si nunca lo hizo o si cambió la versión. */
fun Perfil?.requiereAceptarTerminos(): Boolean =
    this == null || !consentimiento || terminosVersion < TextosLegales.VERSION

class PerfilViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HealthApp
    private val perfilDao = app.database.perfilDao()
    private val registroBiometricoDao = app.database.registroBiometricoDao()
    private val resultadoScoffDao = app.database.resultadoScoffDao()
    private val consentimientoDao = app.database.consentimientoDao()
    private val sync = app.sync

    val perfilUiState: StateFlow<PerfilUiState> = perfilDao.getPerfil()
        .map { PerfilUiState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PerfilUiState.Loading
        )

    val perfil: StateFlow<Perfil?> = perfilDao.getPerfil()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val consentimientos: StateFlow<List<Consentimiento>> = consentimientoDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val estadoNube: StateFlow<EstadoNube> = combine(
        sync.pendientes,
        sync.sesion.prefs.observar { it }
    ) { pendientes, prefs ->
        EstadoNube(
            compartiendo = prefs.getBoolean(SesionNube.K_COMPARTIR, false),
            pendientes = pendientes,
            ultimoEnvio = prefs.getLong(SesionNube.K_ULTIMO_ENVIO, 0),
            ultimoError = prefs.getString(SesionNube.K_ULTIMO_ERROR, null),
            idAnonimo = prefs.getString(SesionNube.K_SB_UID, null),
            borradoPendiente = prefs.getString(SesionNube.K_BORRADO, null) != null
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EstadoNube())

    /**
     * Guarda la aceptación de los términos (y la mayoría de edad / autorización)
     * y la decisión sobre la nube. Cada decisión queda en el historial.
     */
    fun aceptarTerminos(compartirNube: Boolean, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val ahora = System.currentTimeMillis()
            // Se lee directo de la base: perfil.value puede ser null si nadie lo observa,
            // y eso antes borraba el nombre y demás datos al volver a aceptar.
            val actual = perfilDao.getPerfilUnaVez()
            val yaCompartia = actual?.compartirNube == true

            val nuevos = buildList {
                add(Consentimiento(tipo = TipoConsentimiento.TERMINOS, version = TextosLegales.VERSION, aceptado = true, createdAt = ahora))
                add(Consentimiento(tipo = TipoConsentimiento.MAYORIA_EDAD, version = TextosLegales.VERSION, aceptado = true, createdAt = ahora))
                if (compartirNube != yaCompartia || actual == null || actual.terminosVersion < TextosLegales.VERSION) {
                    add(Consentimiento(tipo = TipoConsentimiento.NUBE, version = TextosLegales.VERSION, aceptado = compartirNube, createdAt = ahora))
                }
            }
            nuevos.forEach { consentimientoDao.insert(it) }

            val base = actual ?: Perfil(
                id = 1, nombre = "", edad = null, sexo = "",
                patologias = "", alergias = "", medicamentos = "", consentimiento = true
            )
            perfilDao.upsertPerfil(
                base.copy(
                    consentimiento = true,
                    terminosVersion = TextosLegales.VERSION,
                    terminosAceptadosEn = ahora,
                    compartirNube = compartirNube
                )
            )

            when {
                compartirNube && !yaCompartia -> sync.activarNube()
                compartirNube && yaCompartia -> nuevos.forEach { sync.encolarConsentimiento(it) }
                !compartirNube && yaCompartia -> sync.desactivarNube(SyncManager.MOTIVO_REVOCACION)
                else -> sync.sesion.compartirNube = false
            }
            sync.registrarEvento(
                "terminos_aceptados",
                mapOf("version" to TextosLegales.VERSION, "nube" to compartirNube)
            )
            onComplete()
        }
    }

    /** Interruptor de Privacidad: activar o revocar el envío anonimizado a la nube. */
    fun cambiarCompartirNube(activar: Boolean) {
        viewModelScope.launch {
            val actual = perfilDao.getPerfilUnaVez() ?: return@launch
            if (actual.compartirNube == activar) return@launch
            val consentimiento = Consentimiento(
                tipo = TipoConsentimiento.NUBE, version = TextosLegales.VERSION, aceptado = activar
            )
            consentimientoDao.insert(consentimiento)
            perfilDao.upsertPerfil(actual.copy(compartirNube = activar))
            if (activar) {
                sync.activarNube()
                sync.registrarEvento("nube_activada")
            } else {
                // Al revocar se deja de enviar y se borra lo que ya estaba en la nube.
                sync.desactivarNube(SyncManager.MOTIVO_REVOCACION)
            }
        }
    }

    fun guardarPerfil(
        nombre: String,
        edad: Int?,
        sexo: String,
        patologias: String,
        alergias: String,
        medicamentos: String,
        sede: String? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val actual = perfilDao.getPerfilUnaVez()
            val nuevoPerfil = (actual ?: Perfil(
                id = 1, nombre = "", edad = null, sexo = "",
                patologias = "", alergias = "", medicamentos = "", consentimiento = true
            )).copy(
                nombre = nombre,
                edad = edad,
                sexo = sexo,
                patologias = patologias,
                alergias = alergias,
                medicamentos = medicamentos,
                sede = sede ?: actual?.sede ?: ""
            )
            perfilDao.upsertPerfil(nuevoPerfil)
            // Solo viajan el rango de edad, el sexo y la sede (nunca el nombre ni datos clínicos).
            sync.encolarActualizacionUsuario()
            onComplete()
        }
    }

    fun eliminarTodo(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            // Primero se ordena borrar la copia en la nube (se completa al haber internet).
            // Si hay cuenta con correo, también se elimina la cuenta.
            sync.desactivarNube(SyncManager.MOTIVO_ELIMINACION)
            borrarLocal()
            onComplete()
        }
    }

    /**
     * Cerrar sesión de la cuenta con correo: los datos quedan en la cuenta (nube) y se
     * borran de ESTE teléfono, para que otra persona que lo use no los vea.
     */
    fun cerrarSesionCuenta(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            com.inacap.health.cuenta.CuentaRepositorio(app).cerrarSesion()
            sync.sesion.compartirNube = false
            app.database.syncPendienteDao().deleteAll()
            sync.sesion.limpiarSesiones()
            borrarLocal()
            onComplete()
        }
    }

    private suspend fun borrarLocal() {
        perfilDao.deleteAll()
        registroBiometricoDao.deleteAll()
        resultadoScoffDao.deleteAll()
        consentimientoDao.deleteAll()
        app.database.pasosDiaDao().deleteAll()
        app.database.estadoAnimoDao().deleteAll()
        app.database.resultadoCuestionarioDao().deleteAll()
        app.pasos.borrarTodo()
        // Health Connect: se quitan los permisos; orientación: el servidor borra las
        // conversaciones junto con la cuenta (eliminar_mis_datos).
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            com.inacap.health.salud.HealthConnectDisponibilidad.vinculado(app)
        ) runCatching { com.inacap.health.salud.HealthConnectRepositorio(app).desvincular() }
        app.getSharedPreferences("orientacion_prefs", Context.MODE_PRIVATE).edit { clear() }

        app.bloqueo.quitarBloqueo()
        ReminderScheduler.cancelar(app, ReminderScheduler.TIPO_AGUA)
        ReminderScheduler.cancelar(app, ReminderScheduler.TIPO_MOV)

        val sharedPrefs = app.getSharedPreferences("health_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit { clear() }
    }
}
