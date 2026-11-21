package co.patitas.tienda.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import co.patitas.tienda.R
import javax.crypto.Cipher

/**
 * Autenticación con huella mediante BiometricPrompt (clase BIOMETRIC_STRONG), RF-AUT-03.
 *
 * La huella nunca sale del dispositivo. El prompt se abre con un CryptoObject: el Cipher de
 * CredentialVault solo queda habilitado si el sensor reconoce la huella, y con ese Cipher se
 * cifra o descifra la credencial guardada.
 */
object BiometricHelper {

    /** Indica si el teléfono tiene huella registrada y sensor compatible. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** Mensaje para explicar por qué no se puede usar la huella en este dispositivo. */
    fun unavailableReason(context: Context): String =
        when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> context.getString(R.string.biometric_none_enrolled)
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> context.getString(R.string.biometric_no_hardware)
            else -> context.getString(R.string.biometric_unavailable)
        }

    /**
     * Muestra el diálogo del sistema para leer la huella.
     * @param cipher Cipher de CredentialVault; se devuelve autenticado en onSuccess.
     */
    fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        subtitle: String = activity.getString(R.string.biometric_subtitle),
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            /** Huella reconocida: se entrega el Cipher ya autorizado. */
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authenticated = result.cryptoObject?.cipher
                if (authenticated != null) onSuccess(authenticated) else onError(activity.getString(R.string.biometric_unavailable))
            }

            /** Error o cancelación del diálogo (no se muestra si el usuario canceló). */
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                    onError(errString.toString())
                }
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_title))
            .setSubtitle(subtitle)
            .setNegativeButtonText(activity.getString(R.string.biometric_use_password))
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
