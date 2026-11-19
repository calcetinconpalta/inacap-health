package com.inacap.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.inacap.health.ui.screens.legal.TerminosScreen
import com.inacap.health.ui.theme.INACAPHealthTheme

/** La abre Health Connect para mostrar cómo usamos los datos que nos comparte. */
class PoliticaPrivacidadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            INACAPHealthTheme { TerminosScreen(onBack = { finish() }, pestanaInicial = 1) }
        }
    }
}
