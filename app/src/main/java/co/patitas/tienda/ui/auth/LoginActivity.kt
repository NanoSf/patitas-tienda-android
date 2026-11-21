package co.patitas.tienda.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import co.patitas.tienda.R
import co.patitas.tienda.data.AuthRepository
import co.patitas.tienda.data.DemoSeeder
import co.patitas.tienda.data.User
import co.patitas.tienda.databinding.ActivityLoginBinding
import co.patitas.tienda.ui.common.RoleRouter
import co.patitas.tienda.util.BiometricHelper
import co.patitas.tienda.util.CredentialVault
import co.patitas.tienda.util.SessionManager
import co.patitas.tienda.util.errorMessage
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.validate
import co.patitas.tienda.util.value
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Inicio de sesión (M-02) con correo y contraseña (Firebase Authentication) o con huella.
 * Después de autenticar, RoleRouter abre la pantalla del rol leído en Firestore.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager
    private lateinit var vault: CredentialVault

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)
        vault = CredentialVault(this)

        session.lastEmail?.let { binding.inputEmail.editText?.setText(it) }

        binding.buttonLogin.setOnClickListener { loginWithPassword() }
        binding.inputPassword.editText?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) loginWithPassword()
            false
        }
        binding.buttonForgot.setOnClickListener {
            startActivity(Intent(this, RecoverPasswordActivity::class.java))
        }
        binding.buttonRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.buttonGoogle.setOnClickListener { binding.root.snack(R.string.google_pending) }
        binding.buttonBiometric.setOnClickListener { loginWithBiometric() }

        // Chips de demostración: llenan el formulario con la cuenta de cada rol.
        binding.chipDemoBuyer.setOnClickListener { fillDemo("comprador@patitas.co") }
        binding.chipDemoSeller.setOnClickListener { fillDemo("vendedor@patitas.co") }
        binding.chipDemoAdmin.setOnClickListener { fillDemo("admin@patitas.co") }
    }

    private fun fillDemo(email: String) {
        binding.inputEmail.editText?.setText(email)
        binding.inputPassword.editText?.setText(DemoSeeder.DEMO_PASSWORD)
        binding.inputEmail.error = null
        binding.inputPassword.error = null
    }

    /** Bloquea el botón mientras Firebase responde para evitar dobles envíos. */
    private fun setLoading(loading: Boolean) {
        binding.buttonLogin.isEnabled = !loading
        binding.buttonBiometric.isEnabled = !loading
        binding.buttonLogin.setText(if (loading) R.string.login_loading else R.string.action_login)
    }

    private fun loginWithPassword() {
        val email = binding.inputEmail.value
        val password = binding.inputPassword.editText?.text?.toString().orEmpty()
        val emailOk = binding.inputEmail.validate(
            Patterns.EMAIL_ADDRESS.matcher(email).matches(), getString(R.string.error_email)
        )
        val passwordOk = binding.inputPassword.validate(password.isNotEmpty(), getString(R.string.error_required))
        if (!emailOk || !passwordOk) return

        setLoading(true)
        lifecycleScope.launch {
            try {
                val user = AuthRepository.login(email, password)
                session.lastEmail = user.email
                offerBiometricThenContinue(user, password)
            } catch (e: Exception) {
                setLoading(false)
                // Mensaje genérico: no revela si el correo existe (RF-AUT-01).
                binding.inputPassword.error = errorMessage(e)
            }
        }
    }

    /**
     * Después del primer ingreso con contraseña se ofrece activar la huella.
     * Si el usuario acepta, la contraseña se cifra con la llave del Keystore (CredentialVault).
     */
    private fun offerBiometricThenContinue(user: User, password: String) {
        val sameAccount = vault.email.equals(user.email, ignoreCase = true)
        if ((session.biometricOffered && (sameAccount || !vault.hasCredential)) || !BiometricHelper.isAvailable(this)) {
            RoleRouter.open(this, user)
            return
        }
        session.biometricOffered = true
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_fingerprint)
            .setTitle(R.string.biometric_offer_title)
            .setMessage(R.string.biometric_offer_message)
            .setPositiveButton(R.string.action_activate) { _, _ -> enableBiometric(user, password) }
            .setNegativeButton(R.string.action_not_now) { _, _ -> RoleRouter.open(this, user) }
            .setCancelable(false)
            .show()
    }

    private fun enableBiometric(user: User, password: String) {
        vault.clear()
        BiometricHelper.authenticate(
            this, vault.encryptCipher(),
            subtitle = getString(R.string.biometric_enable_subtitle),
            onSuccess = { cipher ->
                vault.save(cipher, user.email, password)
                binding.root.snack(R.string.biometric_enabled)
                RoleRouter.open(this, user)
            },
            onError = { message ->
                binding.root.snack(message)
                RoleRouter.open(this, user)
            }
        )
    }

    /** Ingreso con huella: la huella habilita el Cipher que descifra la contraseña guardada. */
    private fun loginWithBiometric() {
        if (!BiometricHelper.isAvailable(this)) {
            binding.root.snack(BiometricHelper.unavailableReason(this))
            return
        }
        val email = vault.email
        val cipher = if (vault.hasCredential) vault.decryptCipher() else null
        if (email == null || cipher == null) {
            binding.root.snack(R.string.biometric_not_enabled)
            return
        }
        BiometricHelper.authenticate(
            this, cipher,
            subtitle = getString(R.string.biometric_login_subtitle, email),
            onSuccess = { authenticated ->
                val password = vault.read(authenticated) ?: return@authenticate
                setLoading(true)
                lifecycleScope.launch {
                    try {
                        val user = AuthRepository.login(email, password)
                        session.lastEmail = user.email
                        RoleRouter.open(this@LoginActivity, user)
                    } catch (e: Exception) {
                        setLoading(false)
                        // La contraseña cambió desde que se activó la huella.
                        vault.clear()
                        binding.root.snack(getString(R.string.biometric_outdated, errorMessage(e)))
                    }
                }
            },
            onError = { message -> binding.root.snack(message) }
        )
    }
}
