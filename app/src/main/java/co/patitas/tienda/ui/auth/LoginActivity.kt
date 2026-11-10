package co.patitas.tienda.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import co.patitas.tienda.R
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.data.User
import co.patitas.tienda.databinding.ActivityLoginBinding
import co.patitas.tienda.ui.common.RoleRouter
import co.patitas.tienda.util.BiometricHelper
import co.patitas.tienda.util.SessionManager
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.validate
import co.patitas.tienda.util.value
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Inicio de sesión (M-02) con tres mecanismos: correo y contraseña, Google y huella.
 * Después de autenticar, RoleRouter abre la pantalla del rol.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

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
        binding.inputPassword.editText?.setText(PatitasRepository.DEMO_PASSWORD)
        binding.inputEmail.error = null
        binding.inputPassword.error = null
    }

    private fun loginWithPassword() {
        val email = binding.inputEmail.value
        val password = binding.inputPassword.value
        val emailOk = binding.inputEmail.validate(
            Patterns.EMAIL_ADDRESS.matcher(email).matches(), getString(R.string.error_email)
        )
        val passwordOk = binding.inputPassword.validate(password.isNotEmpty(), getString(R.string.error_required))
        if (!emailOk || !passwordOk) return

        val user = PatitasRepository.login(email, password)
        if (user == null) {
            // Mensaje genérico: no revela si el correo existe (RF-AUT-01).
            binding.inputPassword.error = getString(R.string.error_login)
            return
        }
        session.lastEmail = user.email
        offerBiometricThenContinue(user)
    }

    /** Después del primer ingreso con contraseña se ofrece activar la huella. */
    private fun offerBiometricThenContinue(user: User) {
        if (session.biometricOffered || !BiometricHelper.isAvailable(this)) {
            RoleRouter.open(this, user)
            return
        }
        session.biometricOffered = true
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_fingerprint)
            .setTitle(R.string.biometric_offer_title)
            .setMessage(R.string.biometric_offer_message)
            .setPositiveButton(R.string.action_activate) { _, _ ->
                session.biometricEnabled = true
                RoleRouter.open(this, user)
            }
            .setNegativeButton(R.string.action_not_now) { _, _ -> RoleRouter.open(this, user) }
            .setCancelable(false)
            .show()
    }

    private fun loginWithBiometric() {
        val email = session.lastEmail
        if (!session.biometricEnabled || email == null) {
            binding.root.snack(R.string.biometric_not_enabled)
            return
        }
        if (!BiometricHelper.isAvailable(this)) {
            binding.root.snack(BiometricHelper.unavailableReason(this))
            return
        }
        BiometricHelper.authenticate(
            this,
            onSuccess = {
                val user = PatitasRepository.loginByEmail(email)
                if (user != null) RoleRouter.open(this, user) else binding.root.snack(R.string.error_login)
            },
            onError = { message -> binding.root.snack(message) }
        )
    }
}
