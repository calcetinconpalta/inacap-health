package com.inacap.health

import com.inacap.health.data.sync.Anonimizacion
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Tarea 8: la lista de campos de los eventos es la misma en la app y en firestore.rules.
 * Si no coinciden, los eventos con el campo que falta dejarían de llegar (o se aceptaría basura).
 */
class EventosFirestoreTest {
    private fun reglas(): String {
        val candidatos = listOf("../backend/firebase/firestore.rules", "backend/firebase/firestore.rules")
        return candidatos.map(::File).first { it.exists() }.readText()
    }

    @Test fun mismaListaEnLaAppYEnLasReglas() {
        val texto = reglas()
        val hasOnly = Regex("""p\.keys\(\)\.hasOnly\(\[([^\]]*)]\)""").find(texto)!!.groupValues[1]
        val enReglas = Regex("'([a-z_]+)'").findAll(hasOnly).map { it.groupValues[1] }.toSet()
        assertEquals(Anonimizacion.PROPS_PERMITIDAS, enReglas)
        val conTamano = Regex("""valorOk\(p, '([a-z_]+)'\)""").findAll(texto).map { it.groupValues[1] }.toSet()
        assertEquals("Cada campo permitido tiene su límite de tamaño", Anonimizacion.PROPS_PERMITIDAS, conTamano)
    }
}
