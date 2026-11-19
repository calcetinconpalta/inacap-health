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
import com.inacap.health.ui.screens.equipo.AccesoEquipoScreen
import com.inacap.health.ui.screens.equipo.EquipoApp
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
        if (savedInstanceState == null) {
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
                var sesionEquipoExpiro by remember { mutableStateOf(false) }
                // Sesión del equipo: expira tras 30 min sin uso o 12 h desde que se inició.
                LaunchedEffect(equipoActivo) {
                    while (equipoActivo) {
                        if (app.equipo.sesionVencida()) { app.equipo.cerrarSesion(); sesionEquipoExpiro = true }
                        delay(30_000)
                    }
                }

                Box(Modifier.fillMaxSize().pointerInput(equipoActivo) {
                    if (equipoActivo) awaitPointerEventScope {
                        while (true) { awaitPointerEvent(PointerEventPass.Initial); app.equipo.registrarToque() }
                    }
                }) {
                    when {
                        equipoActivo -> EquipoApp(app.equipo, onSalir = {})
                        sesionEquipoExpiro -> AccesoEquipoScreen(
                            repo = app.equipo,
                            onDentro = { sesionEquipoExpiro = false },
                            onBack = { sesionEquipoExpiro = false },
                            mensajeInicial = "Tu sesión del equipo expiró por seguridad. Vuelve a ingresar."
                        )
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
