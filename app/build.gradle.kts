import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// Claves de la nube (Supabase = SQL, Firebase Firestore = NoSQL).
// Se leen de local.properties o de .env (ninguno se sube a git). Si faltan, la app funciona
// igual pero solo en modo local (no envía nada a la nube).
val localProps = Properties().apply {
    val archivo = rootProject.file("local.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}

// Variables del archivo .env de la raíz (mismo formato que .env.example).
val envProps = Properties().apply {
    val archivo = rootProject.file(".env")
    if (archivo.exists()) archivo.readLines().forEach { linea ->
        val m = Regex("""^\s*([A-Z0-9_]+)\s*=\s*(.*)$""").find(linea)
        if (m != null && !linea.trimStart().startsWith("#")) setProperty(m.groupValues[1], m.groupValues[2].trim().trim('"', '\''))
    }
}

fun claveNube(nombre: String): String {
    // Orden: local.properties → .env → variable de entorno → -P de gradle.
    // SUPABASE_KEY y SUPABASE_PUBLISHABLE_KEY se aceptan como sinónimos de SUPABASE_ANON_KEY.
    val nombres = if (nombre == "SUPABASE_ANON_KEY") listOf(nombre, "SUPABASE_PUBLISHABLE_KEY", "SUPABASE_KEY") else listOf(nombre)
    val valor = nombres.firstNotNullOfOrNull { localProps.getProperty(it)?.takeIf(String::isNotBlank) }
        ?: nombres.firstNotNullOfOrNull { envProps.getProperty(it)?.takeIf(String::isNotBlank) }
        ?: nombres.firstNotNullOfOrNull { System.getenv(it)?.takeIf(String::isNotBlank) }
        ?: providers.gradleProperty(nombre).orNull
        ?: ""
    require(!valor.startsWith("sb_secret_")) { "$nombre contiene una clave SECRETA de Supabase: usa la publishable key." }
    return "\"" + valor.trim().replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

android {
    namespace = "com.inacap.health"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.inacap.health"
        minSdk = 24
        targetSdk = 37
        versionCode = 6
        versionName = "1.3.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", claveNube("SUPABASE_URL"))
        buildConfigField("String", "SUPABASE_ANON_KEY", claveNube("SUPABASE_ANON_KEY"))
        buildConfigField("String", "FIREBASE_API_KEY", claveNube("FIREBASE_API_KEY"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", claveNube("FIREBASE_PROJECT_ID"))
        // Clave PÚBLICA de Bienestar Estudiantil para cifrar derivaciones de extremo a extremo.
        buildConfigField("String", "BIENESTAR_CLAVE_PUBLICA", claveNube("BIENESTAR_CLAVE_PUBLICA"))
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // java.time (usado en el historial) solo existe desde Android 8 (API 26).
        // El desugaring lo hace funcionar también en Android 7 (minSdk 24).
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Robolectric: pruebas de Room y de la sincronización en la JVM, sin emulador.
            isIncludeAndroidResources = true
        }
    }
}

room {
    // Exporta el esquema de cada versión de la base local para revisar migraciones.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    // Seguridad (HU-01, HU-08)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.process)
    // Pulso con cámara (HU-05)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    // Health Connect (HU-07)
    implementation(libs.androidx.health.connect)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    // org.json real para pruebas JVM (en Android viene incluido, en la JVM no).
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
