package co.patitas.tienda.ui.auth

import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import co.patitas.tienda.R
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.data.Role
import co.patitas.tienda.databinding.ActivityRegisterBinding
import co.patitas.tienda.ui.common.RoleRouter
import co.patitas.tienda.util.SessionManager
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.validate
import co.patitas.tienda.util.value
import co.patitas.tienda.util.visibleIf

/**
 * Registro de cuenta (RF-USR-01 y RF-USR-02). Los administradores no se registran aquí:
 * solo los crea otro administrador desde la gestión de usuarios.
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    private val isSeller: Boolean
        get() = binding.toggleRole.checkedButtonId == R.id.buttonRoleSeller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toggleRole.addOnButtonCheckedListener { _, _, _ ->
            binding.groupStore.visibleIf(isSeller)
        }
        binding.buttonCreate.setOnClickListener { createAccount() }
    }

    private fun createAccount() {
        val name = binding.inputName.value
        val email = binding.inputEmail.value
        val phone = binding.inputPhone.value
        val password = binding.inputPassword.value
        val confirm = binding.inputConfirm.value

        var valid = binding.inputName.validate(name.length >= 3, getString(R.string.error_name))
        valid = binding.inputEmail.validate(Patterns.EMAIL_ADDRESS.matcher(email).matches(), getString(R.string.error_email)) && valid
        if (valid && PatitasRepository.emailExists(email)) {
            valid = binding.inputEmail.validate(false, getString(R.string.error_email_exists))
        }
        valid = binding.inputPhone.validate(phone.length == 10, getString(R.string.error_phone)) && valid
        valid = binding.inputPassword.validate(isStrong(password), getString(R.string.error_password)) && valid
        valid = binding.inputConfirm.validate(confirm == password && confirm.isNotEmpty(), getString(R.string.error_password_match)) && valid
        if (isSeller) {
            valid = binding.inputStoreName.validate(binding.inputStoreName.value.isNotEmpty(), getString(R.string.error_required)) && valid
            valid = binding.inputDocument.validate(binding.inputDocument.value.length >= 6, getString(R.string.error_document)) && valid
        }
        if (!binding.checkTerms.isChecked) {
            binding.root.snack(R.string.error_terms)
            valid = false
        }
        if (!valid) return

        val user = PatitasRepository.register(
            name, email, phone, if (isSeller) Role.SELLER else Role.BUYER, password,
            binding.inputStoreName.value, binding.inputDocument.value
        )
        SessionManager(this).lastEmail = user.email
        RoleRouter.open(this, user)
    }

    /** Al menos 10 caracteres con mayúscula, minúscula y número (RNF-SEG-07). */
    private fun isStrong(password: String) =
        password.length >= 10 && password.any { it.isUpperCase() } &&
            password.any { it.isLowerCase() } && password.any { it.isDigit() }
}
