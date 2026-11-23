package com.inacap.health.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.inacap.health.HealthApp
import com.inacap.health.data.sync.Anonimizacion
import com.inacap.health.ui.components.HubItem
import com.inacap.health.ui.components.HubScreen
import com.inacap.health.ui.components.PlaceholderScreen
import com.inacap.health.ui.screens.bienestar.DerivacionScreen
import com.inacap.health.ui.screens.bienestar.EducativoScreen
import com.inacap.health.ui.screens.bienestar.OrientacionScreen
import com.inacap.health.ui.screens.bienestar.ScoffScreen
import com.inacap.health.ui.screens.inicio.InicioScreen
import com.inacap.health.ui.screens.legal.TerminosScreen
import com.inacap.health.ui.screens.onboarding.OnboardingScreen
import com.inacap.health.cuenta.CuentaRepositorio
import com.inacap.health.cuenta.EstadoCuenta
import com.inacap.health.ui.screens.perfil.AccesoScreen
import com.inacap.health.ui.screens.perfil.CuentaScreen
import com.inacap.health.ui.screens.perfil.PerfilDatosScreen
import com.inacap.health.ui.screens.seguridad.BloqueoConfigScreen
import com.inacap.health.ui.screens.seguridad.SeguridadScreen
import com.inacap.health.ui.screens.perfil.PerfilUiState
import com.inacap.health.ui.screens.perfil.PerfilViewModel
import com.inacap.health.ui.screens.perfil.PrivacidadScreen
import com.inacap.health.ui.screens.perfil.RecordatoriosScreen
import com.inacap.health.ui.screens.perfil.requiereAceptarTerminos
import com.inacap.health.ui.screens.salud.CalculadorasScreen
import com.inacap.health.ui.screens.salud.HealthConnectScreen
import com.inacap.health.ui.screens.perfil.AparienciaScreen
import com.inacap.health.ui.components.Iconos
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import com.inacap.health.ui.screens.salud.HistorialScreen
import com.inacap.health.ui.screens.salud.PulsoScreen
import com.inacap.health.ui.screens.salud.ResumenPdfScreen
import com.inacap.health.ui.screens.salud.SaludViewModel

sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    object Inicio : BottomNavItem(Routes.INICIO, "Inicio", Icons.Default.Home)
    object Salud : BottomNavItem(Routes.SALUD, "Salud", Icons.Default.Favorite)
    object Bienestar : BottomNavItem(Routes.BIENESTAR, "Bienestar", Icons.Default.Face)
    object Perfil : BottomNavItem(Routes.PERFIL, "Perfil", Icons.Default.Person)
}

@Composable
fun AppNav(
    perfilViewModel: PerfilViewModel = viewModel()
) {
    val perfilUiState by perfilViewModel.perfilUiState.collectAsState()

    when (val state = perfilUiState) {
        is PerfilUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }
        is PerfilUiState.Success -> {
            val startDestination = remember {
                // También pide aceptar de nuevo si cambió la versión de los términos.
                if (state.perfil.requiereAceptarTerminos()) {
                    Routes.ONBOARDING
                } else {
                    Routes.INICIO
                }
            }
            AppNavContent(
                startDestination = startDestination,
                perfilViewModel = perfilViewModel
            )
        }
    }
}

@Composable
private fun AppNavContent(
    startDestination: String,
    perfilViewModel: PerfilViewModel
) {
    val navController = rememberNavController()
    val saludViewModel: SaludViewModel = viewModel()
    val app = LocalContext.current.applicationContext as HealthApp
    val sync = app.sync

    val items = listOf(
        BottomNavItem.Inicio,
        BottomNavItem.Salud,
        BottomNavItem.Bienestar,
        BottomNavItem.Perfil
    )

    val saludItems = listOf(
        HubItem("Calculadoras IMC e hidratación", "Referencia general, no clínica", Routes.IMC_AGUA, true, Iconos.Gota),
        HubItem("Historial", "IMC, agua, pasos, pulso y tests", Routes.HISTORIAL, true, Icons.Default.DateRange),
        HubItem("Pulso con cámara", "Estimación con la cámara y la linterna", Routes.PULSO, true, Icons.Default.Favorite),
        HubItem("Mi reloj", "Conecta tu reloj por Bluetooth o Health Connect", Routes.HEALTH_CONNECT, true, Iconos.Reloj),
        HubItem("Resumen para tu profesional", "Exporta tu historial en PDF", Routes.RESUMEN_PDF, true, Icons.Default.Share)
    )

    val bienestarItems = listOf(
        HubItem("Test SCOFF", "Cuestionario breve de autocuidado", Routes.SCOFF, true, Icons.Default.CheckCircle),
        HubItem("Cuestionarios", "Bienestar, ánimo y más (no es un diagnóstico)", Routes.CUESTIONARIOS, true, Icons.Default.CheckCircle),
        HubItem("Apoyo de Bienestar", "Pide atención confidencial", Routes.DERIVACION, true, Icons.Default.Call),
        HubItem("Aprende", "Nutrición y hábitos saludables", Routes.EDUCATIVO, true, Icons.Default.Info),
        HubItem("Orientación entre pares", "Conversa con un tutor par supervisado", Routes.ORIENTACION, true, Icons.Default.Email)
    )

    val perfilItems = listOf(
        HubItem("Mis datos", "Edad, sexo, alergias y más", Routes.PERFIL_DATOS, true, Icons.Default.Person),
        HubItem("Recordatorios", "Agua y movimiento", Routes.RECORDATORIOS, true, Icons.Default.Notifications),
        HubItem("Apariencia", "Modo claro o noche", Routes.APARIENCIA, true, Iconos.Luna),
        HubItem("Bloqueo de la app", "PIN o huella", Routes.BLOQUEO, true, Icons.Default.Lock),
        HubItem("Seguridad", "Cómo protegemos tu información", Routes.SEGURIDAD, true, Icons.Default.Lock),
        HubItem("Privacidad y mis datos", "Consentimientos, código anónimo y eliminar datos", Routes.PRIVACIDAD, true, Icons.Default.Settings),
        HubItem("Cuenta e inicio de sesión", "Estudiantes y equipo: iniciar sesión o crear cuenta", Routes.CUENTA, true, Icons.Default.AccountCircle),
        HubItem("Términos y condiciones", "Términos y política de privacidad", Routes.TERMINOS, true, Icons.AutoMirrored.Filled.List)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    // La barra inferior se ve en toda la app (antes solo en las 4 pestañas y en las
    // subpantallas no había forma de volver a Inicio). Se oculta en la bienvenida.
    val enBienvenida = currentRoute == Routes.ONBOARDING ||
        (currentRoute == Routes.TERMINOS && navController.previousBackStackEntry?.destination?.route == Routes.ONBOARDING)
    val showBottomBar = currentRoute != null && !enBienvenida && currentRoute != Routes.ACCESO_INICIAL &&
        currentRoute != Routes.EQUIPO_ACCESO && currentRoute != Routes.PERFIL_DATOS_NUEVA_CUENTA
    val pestanaActual = pestanaDe(currentRoute)

    // Base NoSQL: qué pantallas se usan (solo si el usuario aceptó compartir).
    LaunchedEffect(currentRoute) {
        currentRoute?.let { sync.registrarEvento("pantalla_vista", mapOf("pantalla" to it)) }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                // Tarea 18e: barra flotante blanca; la pestaña activa con fondo rojo y texto blanco.
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().padding(6.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                    ) {
                        items.forEach { item ->
                            val activa = pestanaActual == item.route
                            val fondo = if (activa) com.inacap.health.ui.theme.Diseno.BandaRoja else androidx.compose.ui.graphics.Color.Transparent
                            val tinta = if (activa) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            androidx.compose.foundation.layout.Column(
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                                    .background(fondo)
                                    .selectable(selected = activa, role = androidx.compose.ui.semantics.Role.Tab) { irAPestana(navController, item.route) }
                                    .padding(vertical = 8.dp)
                            ) {
                                Icon(item.icon, contentDescription = item.title, tint = tinta)
                                Text(item.title, color = tinta, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // Atrás del teléfono en una pantalla que quedó sola en la pila (no es Inicio ni la bienvenida):
        // vuelve a Inicio. Desde Inicio (raíz) no se intercepta: el sistema cierra la app normalmente.
        BackHandler(enabled = currentRoute != null && currentRoute != Routes.INICIO && !enBienvenida &&
            currentRoute != Routes.ACCESO_INICIAL && navBackStackEntry != null && navController.previousBackStackEntry == null) {
            navController.volver()
        }
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Onboarding
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    viewModel = perfilViewModel,
                    onVerTerminos = { navController.navigate(Routes.TERMINOS) },
                    onSoyEquipo = { navController.navigate(Routes.EQUIPO_ACCESO) },
                    onOnboardingFinished = {
                        // Si hay base en la nube y aún no hay cuenta, se ofrece crearla (opcional).
                        val ofrecerCuenta = CuentaRepositorio(app).estado() == EstadoCuenta.Anonima
                        navController.navigate(if (ofrecerCuenta) Routes.ACCESO_INICIAL else Routes.INICIO) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.EQUIPO_ACCESO) {
                // Desde la bienvenida, antes de aceptar los términos: solo cuentas del equipo.
                // Al entrar, MainActivity cambia a la app del equipo.
                AccesoScreen(
                    desdeBienvenida = false,
                    inicioSesion = true,
                    permitirEstudiante = false,
                    onListo = { navController.volver() },
                    onBack = { navController.volver() }
                )
            }
            composable(Routes.TERMINOS) {
                TerminosScreen(onBack = { navController.volver() })
            }

            // Pestañas principales
            composable(Routes.INICIO) {
                InicioScreen(
                    perfilViewModel = perfilViewModel,
                    saludViewModel = saludViewModel,
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable(Routes.SALUD) {
                HubScreen(
                    titulo = "Salud",
                    descripcion = "Tus mediciones y registros. Todo queda en tu teléfono.",
                    items = saludItems,
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable(Routes.BIENESTAR) {
                HubScreen(
                    titulo = "Bienestar",
                    descripcion = "Herramientas de autocuidado y apoyo confidencial.",
                    items = bienestarItems,
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable(Routes.PERFIL) {
                HubScreen(
                    titulo = "Perfil",
                    descripcion = "Tus datos, tu cuenta y tus preferencias.",
                    items = perfilItems,
                    onNavigate = { route -> navController.navigate(route) }
                )
            }

            // Subrutas Salud
            composable(Routes.IMC_AGUA) {
                CalculadorasScreen(
                    saludViewModel = saludViewModel,
                    onBack = { navController.volver() }
                )
            }
            composable(Routes.HISTORIAL) {
                HistorialScreen(
                    onNavigate = { route -> navController.navigate(route) },
                    onBack = { navController.volver() }
                )
            }
            composable(Routes.PULSO) {
                PulsoScreen(onBack = { navController.volver() })
            }
            composable(Routes.APARIENCIA) { AparienciaScreen(onBack = { navController.volver() }) }
            composable(Routes.HEALTH_CONNECT) {
                HealthConnectScreen(onBack = { navController.volver() })
            }
            composable(Routes.RESUMEN_PDF) {
                ResumenPdfScreen(onBack = { navController.volver() })
            }

            // Subrutas Bienestar
            composable(Routes.SCOFF) {
                ScoffScreen(
                    onNavigateToDerivacion = { navController.navigate(Routes.DERIVACION) },
                    onBack = { navController.volver() }
                )
            }
            composable(Routes.DERIVACION) {
              com.inacap.health.ui.screens.bienestar.PuertaAntecedentesPantalla(
                titulo = "Apoyo de Bienestar", onBack = { navController.volver() },
                onVerMensajes = { navController.navigate(Routes.ATENCION) }
              ) {
                DerivacionScreen(
                    onSolicitudConfirmada = { canal, codigo, contacto ->
                        sync.enSegundoPlano {
                            // Con contacto: cifrado de extremo a extremo (solo Bienestar lo lee).
                            // Sin contacto: solo estadística anónima, si la persona comparte datos.
                            if (contacto != null) enviarDerivacionConContacto(canal, codigo, contacto)
                            else encolarDerivacion(canal, codigo)
                            registrarEvento(
                                "derivacion_solicitada",
                                mapOf("canal" to Anonimizacion.canalDerivacion(canal))
                            )
                        }
                    },
                    onVerMensajes = { navController.navigate(Routes.ATENCION) },
                    onBack = { navController.volver() }
                )
              }
            }
            composable(Routes.CUESTIONARIOS) {
                com.inacap.health.ui.screens.bienestar.CuestionariosScreen(
                    onBack = { navController.volver() },
                    onPedirAtencion = { navController.navigate(Routes.DERIVACION) }
                )
            }
            composable(Routes.ATENCION) {
                OrientacionScreen(
                    onNavigateToDerivacion = { navController.volver() },
                    onBack = { navController.volver() },
                    soloAtencion = true
                )
            }
            composable(Routes.EDUCATIVO) {
                EducativoScreen(
                    onBack = { navController.volver() }
                )
            }
            composable(Routes.ORIENTACION) {
                OrientacionScreen(
                    onNavigateToDerivacion = { navController.navigate(Routes.DERIVACION) },
                    onBack = { navController.volver() }
                )
            }
            composable(Routes.PH_VIDEOLAMADA) {
                PlaceholderScreen(
                    titulo = "Videollamadas y diagnóstico automático",
                    descripcion = "Las videollamadas con nutricionistas y el diagnóstico clínico automatizado quedaron descartados: la app es un primer filtro orientador y nunca emite diagnósticos.",
                    onBack = { navController.volver() }
                )
            }

            // Subrutas Perfil
            composable(Routes.PERFIL_DATOS) {
                PerfilDatosScreen(
                    viewModel = perfilViewModel,
                    onBack = { navController.volver() }
                )
            }
            composable(Routes.PERFIL_DATOS_NUEVA_CUENTA) {
                // Tras crear la cuenta: completar datos u omitir, y a Inicio.
                val irAInicio = { navController.navigate(Routes.INICIO) { popUpTo(0) { inclusive = true } } }
                BackHandler { irAInicio() }
                PerfilDatosScreen(viewModel = perfilViewModel, onBack = { irAInicio() }, recienCreada = true)
            }
            composable(Routes.RECORDATORIOS) {
                RecordatoriosScreen(
                    onBack = { navController.volver() }
                )
            }
            composable(Routes.PRIVACIDAD) {
                PrivacidadScreen(
                    viewModel = perfilViewModel,
                    onBack = { navController.volver() },
                    onVerTerminos = { navController.navigate(Routes.TERMINOS) },
                    onDataDeleted = {
                        navController.navigate(Routes.ONBOARDING) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.BLOQUEO) {
                BloqueoConfigScreen(onBack = { navController.volver() })
            }
            composable(Routes.SEGURIDAD) {
                SeguridadScreen(
                    onBack = { navController.volver() },
                    onConfigurarBloqueo = { navController.navigate(Routes.BLOQUEO) }
                )
            }
            composable(Routes.CUENTA) {
                CuentaScreen(
                    onBack = { navController.volver() },
                    onAcceso = { navController.navigate(Routes.ACCESO) },
                    onIniciarSesion = { navController.navigate(Routes.ACCESO_SESION) },
                    onCerrarSesion = {
                        perfilViewModel.cerrarSesionCuenta {
                            navController.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } }
                        }
                    }
                )
            }
            composable(Routes.ACCESO) {
                AccesoScreen(
                    desdeBienvenida = false,
                    onListo = { navController.volver() },
                    onCuentaCreada = { navController.navigate(Routes.PERFIL_DATOS_NUEVA_CUENTA) { popUpTo(0) { inclusive = true } } },
                    onBack = { navController.volver() }
                )
            }
            composable(Routes.ACCESO_SESION) {
                AccesoScreen(
                    desdeBienvenida = false,
                    inicioSesion = true,
                    onListo = { navController.volver() },
                    onBack = { navController.volver() }
                )
            }
            composable(Routes.ACCESO_INICIAL) {
                val irAInicio = { navController.navigate(Routes.INICIO) { popUpTo(0) { inclusive = true } } }
                AccesoScreen(
                    desdeBienvenida = true, onListo = { irAInicio() }, onBack = { irAInicio() },
                    onCuentaCreada = { navController.navigate(Routes.PERFIL_DATOS_NUEVA_CUENTA) { popUpTo(0) { inclusive = true } } }
                )
            }
        }
    }
}

/**
 * «Atrás» seguro (tarea 17): nunca deja el NavHost sin pantalla (se veía negro al apretar atrás
 * varias veces seguidas). Si no hay a dónde volver, va a Inicio.
 */
internal fun NavHostController.volver() {
    if (previousBackStackEntry != null) popBackStack()
    else if (currentDestination?.route != Routes.INICIO) navigate(Routes.INICIO) { popUpTo(0) { inclusive = true } }
}

/** A qué pestaña pertenece cada pantalla (para resaltarla en la barra inferior). */
private fun pestanaDe(ruta: String?): String = when (ruta) {
    Routes.SALUD, Routes.IMC_AGUA, Routes.HISTORIAL, Routes.PULSO, Routes.HEALTH_CONNECT, Routes.RESUMEN_PDF -> Routes.SALUD
    Routes.BIENESTAR, Routes.SCOFF, Routes.CUESTIONARIOS, Routes.DERIVACION, Routes.EDUCATIVO, Routes.ORIENTACION, Routes.ATENCION, Routes.PH_VIDEOLAMADA -> Routes.BIENESTAR
    Routes.PERFIL, Routes.PERFIL_DATOS, Routes.RECORDATORIOS, Routes.BLOQUEO, Routes.SEGURIDAD,
    Routes.PRIVACIDAD, Routes.CUENTA, Routes.TERMINOS, Routes.ACCESO, Routes.ACCESO_SESION, Routes.APARIENCIA -> Routes.PERFIL
    else -> Routes.INICIO
}

/**
 * Inicio siempre es la base de la pila: tocar una pestaña quita todo lo que haya
 * encima de Inicio y abre el menú de esa pestaña. Tocar la casita vuelve a Inicio.
 */
internal fun irAPestana(nav: NavHostController, ruta: String) {
    if (ruta == Routes.INICIO) {
        if (!nav.popBackStack(Routes.INICIO, inclusive = false)) {
            nav.navigate(Routes.INICIO) { popUpTo(0) { inclusive = true } }
        }
        return
    }
    nav.navigate(ruta) {
        popUpTo(Routes.INICIO) { inclusive = false }
        launchSingleTop = true
    }
}
