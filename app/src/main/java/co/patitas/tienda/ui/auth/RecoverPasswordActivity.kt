package co.patitas.tienda.ui.auth

import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import co.patitas.tienda.R
import co.patitas.tienda.data.AuthRepository
import co.patitas.tienda.databinding.ActivityRecoverPasswordBinding
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.validate
import co.patitas.tienda.util.value
import co.patitas.tienda.util.visibleIf
import co.patitas.tienda.util.errorMessage
import kotlinx.coroutines.launch

/**
 * Recuperación de contraseña (RF-AUT-04) con el correo de restablecimiento de Firebase
 * Authentication. La respuesta es la misma exista o no la cuenta, para no revelar qué correos
 * están registrados.
 */
class RecoverPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecoverPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecoverPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.buttonSend.setOnClickListener { sendLink() }
        binding.buttonBackToLogin.setOnClickListener { finish() }
        binding.buttonResend.setOnClickListener { send(binding.inputEmail.value, resend = true) }
    }

    private fun sendLink() {
        val email = binding.inputEmail.value
        if (!binding.inputEmail.validate(Patterns.EMAIL_ADDRESS.matcher(email).matches(), getString(R.string.error_email))) return
        send(email, resend = false)
    }

    /** Pide a Firebase el envío del correo con el enlace para crear una contraseña nueva. */
    private fun send(email: String, resend: Boolean) {
        binding.buttonSend.isEnabled = false
        lifecycleScope.launch {
            try {
                AuthRepository.sendPasswordReset(email)
                if (resend) {
                    binding.root.snack(R.string.recover_resent)
                } else {
                    binding.textSentDetail.text = getString(R.string.recover_sent_detail, email)
                    binding.imageState.setImageResource(R.drawable.ic_check_circle)
                    binding.groupForm.visibleIf(false)
                    binding.groupSent.visibleIf(true)
                }
            } catch (e: Exception) {
                binding.root.snack(errorMessage(e))
            } finally {
                binding.buttonSend.isEnabled = true
            }
        }
    }
}
