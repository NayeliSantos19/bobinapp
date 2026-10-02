package com.bobinapp.data.seguridad

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Desbloqueo con huella, rostro o, si el teléfono no tiene sensor, con el PIN/patrón del teléfono.
 * BIOMETRIC_WEAK + DEVICE_CREDENTIAL es la combinación que funciona en todas las versiones desde Android 8.
 *
 * El BiometricPrompt se crea una sola vez en onCreate de la actividad (así lo pide la librería para
 * sobrevivir a rotaciones); cada solicitud guarda qué hacer si sale bien.
 */
class Biometria(actividad: FragmentActivity) {
    private var alAutenticar: (() -> Unit)? = null

    private val prompt = BiometricPrompt(
        actividad,
        ContextCompat.getMainExecutor(actividad),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                alAutenticar?.invoke()
                alAutenticar = null
            }

            override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) {
                alAutenticar = null
            }
            // onAuthenticationFailed (huella no reconocida): el diálogo deja reintentar solo.
        },
    )

    fun pedir(titulo: String, subtitulo: String, onExito: () -> Unit) {
        alAutenticar = onExito
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(titulo)
                .setSubtitle(subtitulo)
                .setAllowedAuthenticators(PERMITIDOS)
                .build(),
        )
    }

    enum class Disponibilidad { LISTA, SIN_CONFIGURAR, NO_SOPORTADA }

    companion object {
        private const val PERMITIDOS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

        fun disponibilidad(context: Context): Disponibilidad = when (BiometricManager.from(context).canAuthenticate(PERMITIDOS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Disponibilidad.LISTA
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Disponibilidad.SIN_CONFIGURAR
            else -> Disponibilidad.NO_SOPORTADA
        }
    }
}
