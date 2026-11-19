package com.inacap.health.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.inacap.health.seguridad.CifradoBaseDatos

@Database(
    entities = [
        Perfil::class,
        RegistroBiometrico::class,
        ResultadoScoff::class,
        Consentimiento::class,
        SyncPendiente::class,
        PasosDia::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun perfilDao(): PerfilDao
    abstract fun registroBiometricoDao(): RegistroBiometricoDao
    abstract fun resultadoScoffDao(): ResultadoScoffDao
    abstract fun consentimientoDao(): ConsentimientoDao
    abstract fun syncPendienteDao(): SyncPendienteDao
    abstract fun pasosDiaDao(): PasosDiaDao

    companion object {
        const val NOMBRE = "inacap_health_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Genera un UUID v4 dentro de SQLite (para filas que ya existían). */
        private const val SQL_UUID_V4 =
            "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
                "substr(lower(hex(randomblob(2))), 2) || '-' || " +
                "substr('89ab', 1 + (abs(random()) % 4), 1) || substr(lower(hex(randomblob(2))), 2) || '-' || " +
                "lower(hex(randomblob(6)))"

        /**
         * v1 -> v2: términos y condiciones, consentimiento de nube, historial de
         * consentimientos, UUID para sincronizar y bandeja de salida (outbox).
         * Conserva todos los datos que el usuario ya tenía.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE perfil ADD COLUMN terminosVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE perfil ADD COLUMN terminosAceptadosEn INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE perfil ADD COLUMN compartirNube INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE registro_biometrico ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE registro_biometrico SET uuid = $SQL_UUID_V4 WHERE uuid = ''")

                db.execSQL("ALTER TABLE resultado_scoff ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE resultado_scoff SET uuid = $SQL_UUID_V4 WHERE uuid = ''")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `consentimiento` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`uuid` TEXT NOT NULL, " +
                        "`tipo` TEXT NOT NULL, " +
                        "`version` INTEGER NOT NULL, " +
                        "`aceptado` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_pendiente` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`destino` TEXT NOT NULL, " +
                        "`operacion` TEXT NOT NULL, " +
                        "`tabla` TEXT NOT NULL, " +
                        "`registroId` TEXT NOT NULL, " +
                        "`payload` TEXT NOT NULL, " +
                        "`intentos` INTEGER NOT NULL, " +
                        "`ultimoError` TEXT, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** v2 -> v3: fuente de cada medición (cámara, reloj) y pasos diarios. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE registro_biometrico ADD COLUMN fuente TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pasos_dia` (" +
                        "`fecha` TEXT NOT NULL, " +
                        "`pasos` INTEGER NOT NULL, " +
                        "`fuente` TEXT NOT NULL, " +
                        "`actualizadoEn` INTEGER NOT NULL, " +
                        "`uuid` TEXT NOT NULL, " +
                        "PRIMARY KEY(`fecha`))"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NOMBRE
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .apply {
                        // HU-01: base cifrada con SQLCipher (clave protegida por el Keystore).
                        CifradoBaseDatos.fabrica(context.applicationContext, NOMBRE)?.let { openHelperFactory(it) }
                    }
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
