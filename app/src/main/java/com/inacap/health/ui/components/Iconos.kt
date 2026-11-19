package com.inacap.health.ui.components

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Íconos propios (los que no vienen en material-icons-core). */
object Iconos {
    private fun icono(nombre: String, vararg trazos: String) = ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f).apply {
        trazos.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(androidx.compose.ui.graphics.Color.Black)) }
    }.build()

    val Luna = icono("luna",
        "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9s9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36c-0.98,1.37 -2.58,2.26 -4.4,2.26c-2.98,0 -5.4,-2.42 -5.4,-5.4c0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3z")

    val Sol = icono("sol",
        "M12,7a5,5 0,1 0,0.001 0z",
        "M11,1h2v3h-2z", "M11,20h2v3h-2z", "M1,11h3v2h-3z", "M20,11h3v2h-3z",
        "M4.22,5.64l1.42,-1.42l2.12,2.12l-1.42,1.42z", "M16.24,17.66l1.42,-1.42l2.12,2.12l-1.42,1.42z",
        "M16.24,6.34l2.12,-2.12l1.42,1.42l-2.12,2.12z", "M4.22,18.36l2.12,-2.12l1.42,1.42l-2.12,2.12z")

    val Reloj = icono("reloj",
        "M20,12c0,-2.54 -1.19,-4.81 -3.04,-6.27L16,0H8L7.05,5.73C5.19,7.19 4,9.45 4,12s1.19,4.81 3.05,6.27L8,24h8l0.96,-5.73C18.81,16.81 20,14.54 20,12zM6,12c0,-3.31 2.69,-6 6,-6s6,2.69 6,6s-2.69,6 -6,6S6,15.31 6,12z")

    val Gota = icono("gota",
        "M12,2.5c0,0 -6.5,7.2 -6.5,11.5a6.5,6.5 0,0 0,13 0C18.5,9.7 12,2.5 12,2.5z")

    val Pasos = icono("pasos",
        "M8.5,2C7,2 6,3.8 6,6.2c0,2 0.8,3.6 0.6,5.3L9.9,12c0.3,-1.4 1.1,-3.2 1.1,-5.6C11,3.9 10,2 8.5,2z",
        "M6.6,13.2l3.3,0.5c0.2,1.6 -0.4,3.3 -2,3.3c-1.4,0 -1.8,-1.6 -1.3,-3.8z",
        "M15.5,7C14,7 13,8.8 13,11.2c0,2 0.8,3.6 0.6,5.3l3.3,0.5c0.3,-1.4 1.1,-3.2 1.1,-5.6C18,8.9 17,7 15.5,7z",
        "M13.6,18.2l3.3,0.5c0.2,1.6 -0.4,3.3 -2,3.3c-1.4,0 -1.8,-1.6 -1.3,-3.8z")
}
