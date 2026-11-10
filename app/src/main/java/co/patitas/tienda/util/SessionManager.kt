package co.patitas.tienda.util

import android.content.Context

/**
 * Preferencias locales de la sesión (SharedPreferences).
 * Solo guarda el correo de la última sesión y si el ingreso con huella está activo;
 * nunca guarda contraseñas (RNF-SEG-02). En la versión con backend el token de sesión
 * se cifra con una clave del Android Keystore ligada a la biometría.
 */
class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("patitas_session", Context.MODE_PRIVATE)

    var lastEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    var biometricOffered: Boolean
        get() = prefs.getBoolean(KEY_OFFERED, false)
        set(value) = prefs.edit().putBoolean(KEY_OFFERED, value).apply()

    var welcomeSeen: Boolean
        get() = prefs.getBoolean(KEY_WELCOME, false)
        set(value) = prefs.edit().putBoolean(KEY_WELCOME, value).apply()

    fun clearBiometric() {
        prefs.edit().remove(KEY_BIOMETRIC).remove(KEY_OFFERED).apply()
    }

    private companion object {
        const val KEY_EMAIL = "last_email"
        const val KEY_BIOMETRIC = "biometric_enabled"
        const val KEY_OFFERED = "biometric_offered"
        const val KEY_WELCOME = "welcome_seen"
    }
}
