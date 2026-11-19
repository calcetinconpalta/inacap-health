package com.inacap.health.data.remote

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Identifica la app ante las APIs de Google igual que lo hace el SDK de Firebase:
 * nombre del paquete + huella SHA-1 del certificado con que se firmó el APK.
 * Así funciona aunque la API key esté restringida a "apps Android".
 */
object IdentidadApp {
    fun encabezados(context: Context): Map<String, String> {
        val sha1 = runCatching { huellaCertificado(context) }.getOrNull() ?: return mapOf("X-Android-Package" to context.packageName)
        return mapOf("X-Android-Package" to context.packageName, "X-Android-Cert" to sha1)
    }

    @Suppress("DEPRECATION")
    private fun huellaCertificado(context: Context): String? {
        val pm = context.packageManager
        val firma = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        } ?: return null
        return MessageDigest.getInstance("SHA-1").digest(firma.toByteArray()).joinToString("") { "%02X".format(it) }
    }
}
