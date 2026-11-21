package co.patitas.tienda.util

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Bóveda de la credencial para el ingreso con huella.
 *
 * Firebase no permite iniciar sesión "solo con huella", así que al activar la huella la
 * contraseña se cifra con una llave AES-256 del Android Keystore que:
 *  - nunca sale del chip seguro del teléfono,
 *  - solo se puede usar después de autenticar con la huella (setUserAuthenticationRequired),
 *  - se invalida si se registra una huella nueva en el teléfono.
 * En SharedPreferences solo queda el texto cifrado y el vector de inicialización (IV).
 * Al ingresar con huella se descifra la contraseña y se inicia sesión en Firebase.
 */
class CredentialVault(context: Context) {

    private val prefs = context.getSharedPreferences("patitas_vault", Context.MODE_PRIVATE)

    val hasCredential: Boolean get() = prefs.contains(KEY_DATA)
    val email: String? get() = prefs.getString(KEY_EMAIL, null)

    /** Cipher para cifrar (al activar la huella). Se autoriza con BiometricPrompt. */
    fun encryptCipher(): Cipher = Cipher.getInstance(TRANSFORMATION).apply {
        init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    }

    /**
     * Cipher para descifrar (al ingresar con huella), o null si la llave fue invalidada
     * (por ejemplo, porque se registró otra huella en el teléfono).
     */
    fun decryptCipher(): Cipher? {
        val iv = prefs.getString(KEY_IV, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            clear()
            null
        }
    }

    /** Cifra y guarda la contraseña con el Cipher ya autorizado por la huella. */
    fun save(cipher: Cipher, email: String, password: String) {
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    /** Descifra la contraseña con el Cipher autorizado por la huella. */
    fun read(cipher: Cipher): String? {
        val data = prefs.getString(KEY_DATA, null) ?: return null
        return String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    /** Desactiva la huella: borra la credencial cifrada y la llave del Keystore. */
    fun clear() {
        prefs.edit().clear().apply()
        try {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
            // Si la llave no existe no hay nada que borrar.
        }
    }

    /** Obtiene la llave AES del Android Keystore o la crea la primera vez. */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val builder = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Cada uso de la llave exige una huella (0 segundos de validez).
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(builder.build())
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "patitas_biometric_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_EMAIL = "email"
        const val KEY_DATA = "data"
        const val KEY_IV = "iv"
    }
}
