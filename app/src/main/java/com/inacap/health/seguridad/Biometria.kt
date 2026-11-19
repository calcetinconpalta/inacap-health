package com.inacap.health.seguridad

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object Biometria {
    fun disponible(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    fun autenticar(activity: FragmentActivity, onExito: () -> Unit, onFallo: (String?) -> Unit = {}) {
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onExito()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFallo(if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) null else errString.toString())
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Desbloquear INACAP Health")
                .setSubtitle("Usa tu huella o rostro")
                .setNegativeButtonText("Usar PIN")
                .setAllowedAuthenticators(BIOMETRIC_WEAK)
                .build()
        )
    }
}
