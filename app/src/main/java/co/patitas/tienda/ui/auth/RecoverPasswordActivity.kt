package co.patitas.tienda.ui.auth

import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import co.patitas.tienda.R
import co.patitas.tienda.databinding.ActivityRecoverPasswordBinding
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.validate
import co.patitas.tienda.util.value
import co.patitas.tienda.util.visibleIf

/**
 * Recuperación de contraseña (RF-AUT-04). La respuesta es la misma exista o no la cuenta,
 * para no revelar qué correos están registrados.
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
        binding.buttonResend.setOnClickListener { binding.root.snack(R.string.recover_resent) }
    }

    private fun sendLink() {
        val email = binding.inputEmail.value
        if (!binding.inputEmail.validate(Patterns.EMAIL_ADDRESS.matcher(email).matches(), getString(R.string.error_email))) return
        binding.textSentDetail.text = getString(R.string.recover_sent_detail, email)
        binding.imageState.setImageResource(R.drawable.ic_check_circle)
        binding.groupForm.visibleIf(false)
        binding.groupSent.visibleIf(true)
    }
}
