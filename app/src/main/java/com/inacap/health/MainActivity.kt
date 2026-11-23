package com.inacap.health

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import com.inacap.health.cuenta.CuentaRepositorio
import com.inacap.health.cuenta.ReglasCuenta
import com.inacap.health.equipo.SesionEquipoReglas
import com.inacap.health.ui.screens.equipo.EquipoApp
import com.inacap.health.ui.screens.perfil.AccesoScreen
import com.inacap.health.ui.screens.perfil.PerfilUiState
import com.inacap.health.ui.screens.perfil.requiereAceptarTerminos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inacap.health.ui.nav.AppNav
import com.inacap.health.ui.screens.perfil.PerfilViewModel
import com.inacap.health.ui.screens.seguridad.PantallaBloqueo
import com.inacap.health.ui.theme.INACAPHealthTheme
import com.inacap.health.ui.theme.temaOscuroActivo

// FragmentActivity (en vez de ComponentActivity) porque BiometricPrompt lo necesita.
class MainActivity : FragmentActivity() {
    override fun onResume() {
        super.onResume()
        val app = application as HealthApp
        if (app.equipo.sesionVencida()) lifecycleScope.launch { app.equipo.cerrarSesion() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as HealthApp
        val primeraVez = savedInstanceState == null
        if (primeraVez) {
            app.sync.registrarEvento("app_abierta")
        }
        setContent {
            val oscuro = temaOscuroActivo()
            // Íconos de la barra de estado según el modo elegido en la app (no solo el del teléfono).
            LaunchedEffect(oscuro) {
                val estilo = if (oscuro) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
            }
            INACAPHealthTheme(oscuro = oscuro) {
                val perfilViewModel: PerfilViewModel = viewModel()
                val bloqueada by app.bloqueo.bloqueada.collectAsState()

                // Con PIN activo: sin capturas de pantalla y oculta en "recientes".
                LaunchedEffect(bloqueada) {
                    if (app.bloqueo.tienePin) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }

                val equipoActivo by app.equipo.activo.collectAsState()
                val perfilUi by perfilViewModel.perfilUiState.collectAsState()
                val terminosAceptados = (perfilUi as? PerfilUiState.Success)?.perfil?.requiereAceptarTerminos() == false

                // Pantalla «Iniciar sesión» sobre la app: null = no se muestra; "" = sin mensaje.
                // Se abre al salir del modo equipo (cerrar sesión o expiración), nunca la app de estudiante directo.
                var accesoPendiente by remember { mutableStateOf<String?>(null) }
                var estabaEnEquipo by remember { mutableStateOf(equipoActivo) }
                LaunchedEffect(equipoActivo) {
                    accesoPendiente = SesionEquipoReglas.accesoTrasCambio(estabaEnEquipo, equipoActivo, accesoPendiente)
                    estabaEnEquipo = equipoActivo
                    // Sesión del equipo: expira tras 30 min sin uso o 12 h desde que se inició.
                    while (equipoActivo) {
                        if (app.equipo.sesionVencida()) {
                            accesoPendiente = "Tu sesión del equipo expiró por seguridad. Vuelve a ingresar."
                            app.equipo.cerrarSesion()
                        }
                        delay(30_000)
                    }
                }
                // Una cuenta del equipo nunca queda como estudiante (versiones anteriores lo permitían).
                LaunchedEffect(Unit) {
                    if (primeraVez && CuentaRepositorio(app).revisarCuentaActual()) accesoPendiente = ReglasCuenta.CUENTA_ES_DEL_EQUIPO
                }

                Box(Modifier.fillMaxSize().pointerInput(equipoActivo) {
                    if (equipoActivo) awaitPointerEventScope {
                        while (true) { awaitPointerEvent(PointerEventPass.Initial); app.equipo.registrarToque() }
                    }
                }) {
                    when {
                        equipoActivo -> EquipoApp(app.equipo, onSalir = {})
                        accesoPendiente != null -> {
                            val cerrar = { accesoPendiente = null }
                            BackHandler(onBack = cerrar)
                            AccesoScreen(
                                desdeBienvenida = false,
                                inicioSesion = true,
                                permitirEstudiante = terminosAceptados,
                                mensajeInicial = accesoPendiente?.takeIf { it.isNotEmpty() },
                                onListo = cerrar,
                                onBack = cerrar
                            )
                        }
                        else -> AppNav(perfilViewModel = perfilViewModel)
                    }
                    if (bloqueada) {
                        PantallaBloqueo(
                            bloqueo = app.bloqueo,
                            onOlvidePin = {
                                perfilViewModel.eliminarTodo { recreate() }
                            }
                        )
                    }
                }
            }
        }
    }
}
