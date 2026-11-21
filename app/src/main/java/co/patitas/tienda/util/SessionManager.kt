package co.patitas.tienda.util

import android.content.Context

/**
 * Preferencias locales (SharedPreferences): el correo de la última sesión y si ya se ofreció
 * activar la huella. La sesión en sí la guarda Firebase Authentication y la credencial para la
 * huella la guarda cifrada CredentialVault; aquí nunca se guardan contraseñas.
 */
class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("patitas_session", Context.MODE_PRIVATE)

    var lastEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    var biometricOffered: Boolean
        get() = prefs.getBoolean(KEY_OFFERED, false)
        set(value) = prefs.edit().putBoolean(KEY_OFFERED, value).apply()

    private companion object {
        const val KEY_EMAIL = "last_email"
        const val KEY_OFFERED = "biometric_offered"
    }
}
