package com.inacap.health

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.inacap.health.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Simula a un usuario con la versión 1.0 instalada (base v1 con datos) que
 * actualiza a la 1.1: Room debe migrar, validar el esquema y conservar todo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MigracionTest {

    @Test
    fun v1aV3_conservaLosDatosYAgregaLoNuevo() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val archivo = context.getDatabasePath("migracion.db").apply { parentFile?.mkdirs(); delete() }

        // Esquema exacto de la versión 1.0 (antes de este cambio)
        SQLiteDatabase.openOrCreateDatabase(archivo, null).use { v1 ->
            v1.execSQL("CREATE TABLE perfil (`id` INTEGER NOT NULL, `nombre` TEXT NOT NULL, `edad` INTEGER, `sexo` TEXT NOT NULL, `patologias` TEXT NOT NULL, `alergias` TEXT NOT NULL, `medicamentos` TEXT NOT NULL, `consentimiento` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            v1.execSQL("CREATE TABLE registro_biometrico (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pesoKg` REAL, `estaturaCm` REAL, `imc` REAL, `aguaMl` INTEGER NOT NULL, `frecuenciaCardiaca` INTEGER, `createdAt` INTEGER NOT NULL)")
            v1.execSQL("CREATE TABLE resultado_scoff (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `puntaje` INTEGER NOT NULL, `respuestas` TEXT NOT NULL, `requiereDerivacion` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
            v1.execSQL("INSERT INTO perfil VALUES (1, 'Ana', 21, 'Femenino', 'asma', '', '', 1)")
            v1.execSQL("INSERT INTO registro_biometrico (pesoKg, estaturaCm, imc, aguaMl, frecuenciaCardiaca, createdAt) VALUES (70, 175, 22.9, 0, NULL, 1), (NULL, NULL, NULL, 250, NULL, 2)")
            v1.execSQL("INSERT INTO resultado_scoff (puntaje, respuestas, requiereDerivacion, createdAt) VALUES (2, '1,1,0,0,0', 1, 3)")
            v1.version = 1
        }

        // Abrir con Room v2 ejecuta MIGRATION_1_2 y valida contra las entidades
        val db = Room.databaseBuilder(context, AppDatabase::class.java, archivo.absolutePath)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val perfil = db.perfilDao().getPerfilUnaVez()!!
            assertEquals("Ana", perfil.nombre)
            assertEquals("asma", perfil.patologias)
            assertTrue(perfil.consentimiento)
            assertEquals("Debe pedir aceptar los términos nuevos", 0, perfil.terminosVersion)
            assertFalse(perfil.compartirNube)

            val registros = db.registroBiometricoDao().getAllUnaVez()
            assertEquals(2, registros.size)
            assertEquals(22.9, registros[0].imc!!, 0.0)
            val uuids = registros.map { it.uuid } + db.resultadoScoffDao().getAllUnaVez().map { it.uuid }
            val v4 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
            assertTrue("UUID v4 válidos: $uuids", uuids.all { v4.matches(it) })
            assertEquals(uuids.size, uuids.toSet().size)

            assertTrue(registros.all { it.fuente == null })
            // v3: la tabla de pasos existe y acepta datos
            db.pasosDiaDao().upsert(com.inacap.health.data.PasosDia(fecha = "2026-11-19", pasos = 100, fuente = "sensor"))
            assertEquals(100, db.pasosDiaDao().get("2026-11-19")!!.pasos)
            db.pasosDiaDao().deleteAll()
            assertTrue(db.pasosDiaDao().getAllUnaVez().isEmpty())
            assertTrue(db.consentimientoDao().getAllUnaVez().isEmpty())
            assertEquals(0, db.syncPendienteDao().getLote(10, 3).size)
        }
        db.close()
    }
}
