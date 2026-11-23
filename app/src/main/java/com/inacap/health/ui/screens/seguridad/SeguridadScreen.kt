package com.inacap.health.ui.screens.seguridad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.inacap.health.HealthApp
import com.inacap.health.data.remote.NubeConfig
import com.inacap.health.seguridad.CifradoBaseDatos
import com.inacap.health.seguridad.Cifradores
import com.inacap.health.ui.components.BarraSuperior
import com.inacap.health.ui.components.FilaValor
import com.inacap.health.ui.components.Tarjeta
import com.inacap.health.ui.components.TextoSecundario

/** HU-01 · Estado real de la protección de los datos en este teléfono. */
@Composable
fun SeguridadScreen(onBack: () -> Unit, onConfigurarBloqueo: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HealthApp
    val baseCifrada = remember { CifradoBaseDatos.estaCifrada(context) }
    val keystore = remember { Cifradores.porDefecto.protegido }
    val sino = { b: Boolean -> if (b) "Activado" else "No activo" }

    Scaffold(topBar = { BarraSuperior("Seguridad", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Tarjeta(titulo = "En tu teléfono") {
                FilaValor("Tus datos guardados están cifrados", sino(baseCifrada && keystore))
                FilaValor("Bloqueo con PIN o huella", sino(app.bloqueo.tienePin))
                FilaValor("Copias de seguridad automáticas", "Excluidas")
                FilaValor("Capturas de pantalla con bloqueo activo", if (app.bloqueo.tienePin) "Bloqueadas" else "Permitidas")
                if (!app.bloqueo.tienePin) {
                    OutlinedButton(onClick = onConfigurarBloqueo) { Text("Activar bloqueo", color = MaterialTheme.colorScheme.onSurface) }
                }
            }
            Tarjeta(titulo = "Qué protege cada cosa") {
                TextoSecundario("• El PIN y la huella bloquean la pantalla: evitan que otra persona abra la app en tu teléfono.")
                TextoSecundario("• Tus datos se cifran aparte, con una clave que guarda el propio teléfono. Ese cifrado funciona aunque no tengas PIN y no depende de él.")
                TextoSecundario("• Quien conozca tu PIN puede abrir la app y ver tus datos: usa uno difícil de adivinar y no lo compartas. Si lo olvidas no se puede recuperar; tendrías que borrar los datos de la app.")
            }
            Tarjeta(titulo = "Cuando la app se conecta") {
                FilaValor("Conexiones", "Siempre seguras y cifradas")
                FilaValor(
                    "Tus datos de contacto en una solicitud de apoyo",
                    if (NubeConfig.actual.derivacionCifradaDisponible) "Protegidos: solo los lee Bienestar" else "No disponible"
                )
            }
            Tarjeta(titulo = "Si decides compartir datos") {
                TextoSecundario("Se envían sin tu nombre, con un código anónimo. Nadie más puede ver ni cambiar tus registros, y las estadísticas solo muestran cifras totales.")
            }
            if (!baseCifrada) {
                TextoSecundario("Este teléfono no permitió activar el cifrado de la base. Tus datos siguen solo en el teléfono; te recomendamos activar el bloqueo con PIN.")
            }
        }
    }
}
