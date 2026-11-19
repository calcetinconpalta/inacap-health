package com.inacap.health.data.remote

import com.inacap.health.BuildConfig

/** Direcciones y claves de las dos bases en la nube. */
data class ConfigNube(
    val supabaseUrl: String,
    val supabaseAnonKey: String,
    val firebaseApiKey: String,
    val firebaseProjectId: String,
    val urlFirestore: String = "https://firestore.googleapis.com/v1",
    val urlIdentity: String = "https://identitytoolkit.googleapis.com/v1",
    val urlSecureToken: String = "https://securetoken.googleapis.com/v1",
    val bienestarClavePublica: String = ""
) {
    /** Solo HTTPS (o localhost, para pruebas automáticas). */
    private fun urlValida(url: String) =
        url.startsWith("https://") || url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost")

    val sqlConfigurado: Boolean
        get() = urlValida(supabaseUrl) && supabaseAnonKey.isNotBlank()

    val nosqlConfigurado: Boolean
        get() = firebaseApiKey.isNotBlank() && firebaseProjectId.isNotBlank()

    val algunaConfigurada: Boolean
        get() = sqlConfigurado || nosqlConfigurado

    /** Se pueden enviar derivaciones cifradas (hay base SQL y clave pública de Bienestar). */
    val derivacionCifradaDisponible: Boolean
        get() = sqlConfigurado && bienestarClavePublica.length > 200
}

/**
 * Claves reales, leídas de local.properties en tiempo de compilación.
 * Si están vacías la app funciona solo en modo local.
 */
object NubeConfig {
    val actual = ConfigNube(
        supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/'),
        supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
        firebaseApiKey = BuildConfig.FIREBASE_API_KEY,
        firebaseProjectId = BuildConfig.FIREBASE_PROJECT_ID,
        bienestarClavePublica = BuildConfig.BIENESTAR_CLAVE_PUBLICA
    )

    val sqlConfigurado: Boolean get() = actual.sqlConfigurado
    val nosqlConfigurado: Boolean get() = actual.nosqlConfigurado
    val algunaConfigurada: Boolean get() = actual.algunaConfigurada
}
