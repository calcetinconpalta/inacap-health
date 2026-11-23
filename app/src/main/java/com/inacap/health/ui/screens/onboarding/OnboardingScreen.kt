package com.inacap.health.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inacap.health.data.remote.NubeConfig
import com.inacap.health.legal.TextosLegales
import com.inacap.health.ui.screens.perfil.PerfilViewModel

@Composable
fun OnboardingScreen(
    viewModel: PerfilViewModel,
    onVerTerminos: () -> Unit,
    onOnboardingFinished: () -> Unit,
    onSoyEquipo: () -> Unit = {}
) {
    val perfil by viewModel.perfil.collectAsState()
    // Si ya aceptó una versión anterior, se va directo al paso de los términos.
    val esReaceptacion = perfil?.consentimiento == true
    var step by rememberSaveable { mutableIntStateOf(1) }
    var aceptaTerminos by rememberSaveable { mutableStateOf(false) }
    var mayoriaEdad by rememberSaveable { mutableStateOf(false) }
    var compartirNube by rememberSaveable { mutableStateOf(false) }
    var precargado by rememberSaveable { mutableStateOf(false) }
    var guardando by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(perfil) {
        val p = perfil
        if (!precargado && p != null) {
            compartirNube = p.compartirNube
            if (p.consentimiento) step = 2
            precargado = true
        }
    }

    BackHandler(enabled = step == 2 && !esReaceptacion) { step = 1 }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (step == 1) {
                Spacer(modifier = Modifier.height(32.dp))
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "INACAP Health",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "App de autocuidado para estudiantes. Te ayuda a conocer tus hábitos y te orienta hacia un profesional cuando corresponda.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
                Spacer(modifier = Modifier.height(40.dp))
                Button(
                    onClick = { step = 2 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Siguiente",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onSoyEquipo, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Soy del equipo: iniciar sesión",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (esReaceptacion) "Actualizamos nuestros términos" else "Aviso y consentimiento",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                if (esReaceptacion) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Para seguir usando la app, revisa y acepta la versión ${TextosLegales.VERSION}. Tus datos se mantienen.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        PuntoConsentimiento(
                            numero = "a",
                            texto = "Es un primer filtro informativo, no entrega diagnósticos ni reemplaza a un médico o nutricionista."
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PuntoConsentimiento(
                            numero = "b",
                            texto = "Tus datos de salud son sensibles: se guardan en tu teléfono y nunca se venden. Solo si tú lo activas, se envía una copia anonimizada (sin tu nombre ni datos clínicos) para estadísticas. " +
                                com.inacap.health.legal.TextosLegales.AVISO_ANTECEDENTES
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PuntoConsentimiento(
                            numero = "c",
                            texto = "Puedes ver, desactivar el envío o eliminar tus datos cuando quieras desde Perfil."
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PuntoConsentimiento(
                            numero = "d",
                            texto = "Se rige por la Ley 19.628 (modificada por la Ley 21.719) sobre protección de datos personales."
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onVerTerminos, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Leer Términos y Condiciones y Política de Privacidad",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // "Aceptar todo": marca o desmarca las tres casillas de una vez (indica que incluye la opcional).
                val marcadas = listOf(aceptaTerminos, mayoriaEdad, compartirNube).count { it }
                val estadoTodo = when (marcadas) { 3 -> ToggleableState.On; 0 -> ToggleableState.Off; else -> ToggleableState.Indeterminate }
                val cambiarTodo = {
                    val nuevo = estadoTodo != ToggleableState.On
                    aceptaTerminos = nuevo; mayoriaEdad = nuevo; compartirNube = nuevo
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { cambiarTodo() }.padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TriStateCheckbox(
                            state = estadoTodo, onClick = { cambiarTodo() },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Column(Modifier.padding(vertical = 10.dp)) {
                            Text("Aceptar todo", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Incluye compartir datos anónimos (opcional). Puedes desmarcar cualquiera abajo.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                FilaCheck(
                    checked = aceptaTerminos,
                    onChange = { aceptaTerminos = it },
                    texto = "He leído y acepto los Términos y Condiciones y la Política de Privacidad (versión ${TextosLegales.VERSION}).",
                    obligatorio = true
                )
                FilaCheck(
                    checked = mayoriaEdad,
                    onChange = { mayoriaEdad = it },
                    texto = "Tengo 18 años o más, o cuento con la autorización de mi madre, padre o tutor.",
                    obligatorio = true
                )
                FilaCheck(
                    checked = compartirNube,
                    onChange = { compartirNube = it },
                    texto = if (NubeConfig.algunaConfigurada) {
                        "Opcional: compartir datos anonimizados (rango de edad, IMC, agua, resultado SCOFF y uso de la app) para estadísticas de bienestar estudiantil. Puedes desactivarlo cuando quieras."
                    } else {
                        "Opcional: compartir datos anonimizados para estadísticas (no disponible en esta versión, por ahora no se enviará nada)."
                    },
                    obligatorio = false
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        guardando = true
                        viewModel.aceptarTerminos(compartirNube) {
                            onOnboardingFinished()
                        }
                    },
                    enabled = aceptaTerminos && mayoriaEdad && !guardando,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (esReaceptacion) "Aceptar y continuar" else "Comenzar",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!esReaceptacion) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { step = 1 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Atrás",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaCheck(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    texto: String,
    obligatorio: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = texto + if (obligatorio) " *" else "",
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = if (obligatorio) FontWeight.Medium else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun PuntoConsentimiento(numero: String, texto: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "($numero)",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp)
        )
        Text(
            text = texto,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 20.sp
        )
    }
}
