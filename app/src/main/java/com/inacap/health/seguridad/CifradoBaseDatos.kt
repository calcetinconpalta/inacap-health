package com.inacap.health.seguridad

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.SecureRandom

/**
 * Cifrado de la base local con SQLCipher (AES-256).
 * La contraseña de la base es aleatoria y se guarda cifrada con el Keystore.
 * Si el teléfono tenía una base sin cifrar (versión 1.0), se convierte una vez.
 */
object CifradoBaseDatos {
    private const val TAG = "CifradoBaseDatos"
    private const val PREFS = "seguridad_prefs"
    private const val K_CLAVE = "clave_base_cifrada"
    private const val K_ESTADO = "base_cifrada"
    private val CABECERA_PLANA = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /** true si la base está cifrada en este teléfono (se muestra en Seguridad). */
    fun estaCifrada(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_ESTADO, false)

    /**
     * Devuelve la fábrica de SQLCipher para Room, o null si no se pudo preparar
     * (en ese caso la app sigue funcionando sin cifrar y lo informa en Seguridad).
     */
    fun fabrica(context: Context, nombreBase: String): SupportSQLiteOpenHelper.Factory? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cifrador = Cifradores.porDefecto
        if (!cifrador.protegido) {
            prefs.edit { putBoolean(K_ESTADO, false) }
            return null
        }
        return try {
            System.loadLibrary("sqlcipher")
            val clave = obtenerClave(context, cifrador)
            val archivo = context.getDatabasePath(nombreBase)
            if (esBasePlana(archivo)) convertirACifrada(archivo, clave)
            prefs.edit { putBoolean(K_ESTADO, true) }
            SupportOpenHelperFactory(clave.toByteArray(Charsets.UTF_8))
        } catch (e: Throwable) {
            Log.e(TAG, "No se pudo activar el cifrado de la base: ${e.javaClass.simpleName}")
            prefs.edit { putBoolean(K_ESTADO, false) }
            null
        }
    }

    private fun obtenerClave(context: Context, cifrador: Cifrador): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(K_CLAVE, null)?.let { guardada ->
            cifrador.descifrar(guardada)?.let { return it }
            throw IllegalStateException("La clave de la base no se pudo recuperar del Keystore")
        }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val clave = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit(commit = true) { putString(K_CLAVE, cifrador.cifrar(clave)) }
        return clave
    }

    fun esBasePlana(archivo: File): Boolean {
        if (!archivo.exists() || archivo.length() < CABECERA_PLANA.size) return false
        val cabecera = ByteArray(CABECERA_PLANA.size)
        archivo.inputStream().use { it.read(cabecera) }
        return cabecera.contentEquals(CABECERA_PLANA)
    }

    /** Copia la base sin cifrar a una cifrada (sqlcipher_export) y reemplaza el archivo. */
    private fun convertirACifrada(archivo: File, clave: String) {
        val temporal = File(archivo.parentFile, archivo.name + ".cifrando")
        temporal.delete()
        val plana = SQLiteDatabase.openDatabase(archivo.absolutePath, "", null, SQLiteDatabase.OPEN_READWRITE, null)
        try {
            val version = plana.version
            plana.execSQL("ATTACH DATABASE '${temporal.absolutePath}' AS cifrada KEY '$clave'")
            plana.rawQuery("SELECT sqlcipher_export('cifrada')", null).use { it.moveToFirst() }
            plana.execSQL("PRAGMA cifrada.user_version = $version")
            plana.execSQL("DETACH DATABASE cifrada")
        } finally {
            plana.close()
        }
        listOf("", "-wal", "-shm", "-journal").forEach { File(archivo.path + it).delete() }
        if (!temporal.renameTo(archivo)) throw IllegalStateException("No se pudo reemplazar la base")
        Log.i(TAG, "Base local convertida a cifrada")
    }
}
