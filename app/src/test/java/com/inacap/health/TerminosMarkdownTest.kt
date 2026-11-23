package com.inacap.health

import com.inacap.health.legal.TextosLegales
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * docs/TERMINOS_Y_CONDICIONES.md debe ser el mismo texto que ve la persona en la app.
 * Para regenerarlo: REGENERAR_TERMINOS=1 ./gradlew testDebugUnitTest --tests '*TerminosMarkdownTest*'
 */
class TerminosMarkdownTest {
    private val archivo = listOf("../docs/TERMINOS_Y_CONDICIONES.md", "docs/TERMINOS_Y_CONDICIONES.md").map(::File).first { it.exists() }

    @Test fun elDocumentoCoincideConLaApp() {
        val esperado = TextosLegales.comoMarkdown()
        if (System.getenv("REGENERAR_TERMINOS") == "1") archivo.writeText(esperado)
        assertEquals("Regenera docs/TERMINOS_Y_CONDICIONES.md (ver comentario de la clase)", esperado, archivo.readText().replace("\r\n", "\n"))
    }

    @Test fun avisoDeAntecedentesYReglaDelEquipo() {
        val texto = TextosLegales.comoTexto()
        assertTrue(texto.contains(TextosLegales.AVISO_ANTECEDENTES))
        assertTrue(texto.contains("No indican ni sugieren medicamentos por chat"))
        assertTrue("Ya no dice que los antecedentes nunca se suben", !texto.contains("NUNCA se envían tu nombre, patologías"))
    }
}
