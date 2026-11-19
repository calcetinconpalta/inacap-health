package com.inacap.health.ui.screens.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.inacap.health.ui.components.BarraSuperior
import com.inacap.health.ui.components.Tarjeta
import com.inacap.health.ui.components.TextoSecundario
import com.inacap.health.ui.theme.Apariencia
import com.inacap.health.ui.theme.ModoTema

/** Modo claro / noche activable y desactivable. */
@Composable
fun AparienciaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val modo by Apariencia.modo.collectAsState()
    Scaffold(topBar = { BarraSuperior("Apariencia", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Tarjeta(titulo = "Modo de pantalla") {
                Column(Modifier.selectableGroup()) {
                    ModoTema.entries.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = modo == m, role = Role.RadioButton) { Apariencia.cambiar(context, m) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = modo == m, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
                            Text(m.etiqueta, modifier = Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            TextoSecundario("El modo noche usa colores oscuros y suaves para descansar la vista. También puedes cambiarlo con la luna en Inicio.")
        }
    }
}
