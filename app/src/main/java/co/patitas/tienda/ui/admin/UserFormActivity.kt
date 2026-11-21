package co.patitas.tienda.ui.admin

import android.os.Bundle
import android.util.Patterns
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import co.patitas.tienda.R
import co.patitas.tienda.data.AuthRepository
import co.patitas.tienda.data.Role
import co.patitas.tienda.data.Session
import co.patitas.tienda.data.User
import co.patitas.tienda.data.UserRepository
import co.patitas.tienda.data.UserStatus
import co.patitas.tienda.data.formatDate
import co.patitas.tienda.databinding.ActivityUserFormBinding
import co.patitas.tienda.util.errorMessage
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.validate
import co.patitas.tienda.util.value
import co.patitas.tienda.util.visibleIf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.launch

/**
 * Crear y editar usuarios (A-03, RF-ADU-02 y 03).
 *  - Crear: cuenta en Firebase Authentication con contraseña temporal + perfil en Firestore
 *    (AuthRepository.createUserAsAdmin, sin cerrar la sesión del administrador).
 *  - Actualizar: datos, rol y estado en Firestore. El correo no se edita porque es la credencial.
 *  - Eliminar: borra el perfil; la cuenta ya no puede ingresar.
 * Solo un administrador puede crear otros administradores (lo exigen las reglas de seguridad).
 */
class UserFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserFormBinding
    private var editing: User? = null
    private val statuses = UserStatus.entries

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getStringExtra(EXTRA_USER_ID)
        binding.toolbar.setTitle(if (id == null) R.string.user_form_new else R.string.user_form_edit)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.dropdownStatus.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, statuses.map { it.style.label })
        )
        binding.toggleRole.addOnButtonCheckedListener { _, _, _ -> updateStoreField() }
        binding.buttonSave.setOnClickListener { save() }
        binding.buttonDelete.setOnClickListener { confirmDelete() }

        if (id == null) {
            binding.textInitials.text = "+"
            binding.textCreated.setText(R.string.user_form_new_hint)
            binding.dropdownStatus.setText(UserStatus.ACTIVE.style.label, false)
            binding.buttonDelete.visibleIf(false)
            updateStoreField()
        } else {
            binding.inputPassword.visibleIf(false)
            binding.inputEmail.isEnabled = false
            binding.buttonSave.isEnabled = false
            lifecycleScope.launch {
                try {
                    UserRepository.get(id)?.let { fill(it) } ?: finish()
                } catch (e: Exception) {
                    binding.root.snack(errorMessage(e))
                }
                binding.buttonSave.isEnabled = true
            }
        }
    }

    private fun fill(user: User) {
        editing = user
        binding.textInitials.text = user.initials
        binding.textCreated.text = getString(R.string.user_form_created, formatDate(user.createdAt))
        binding.inputName.editText?.setText(user.name)
        binding.inputEmail.editText?.setText(user.email)
        binding.inputPhone.editText?.setText(user.phone)
        binding.inputDocument.editText?.setText(user.document)
        binding.inputStoreName.editText?.setText(user.storeName)
        binding.toggleRole.check(
            when (user.role) {
                Role.BUYER -> R.id.buttonRoleBuyer
                Role.SELLER -> R.id.buttonRoleSeller
                Role.ADMIN -> R.id.buttonRoleAdmin
            }
        )
        binding.dropdownStatus.setText(user.status.style.label, false)
        binding.buttonDelete.visibleIf(user.id != Session.user?.id)
        updateStoreField()
    }

    /** El nombre de la tienda solo aplica al rol Vendedor. */
    private fun updateStoreField() {
        binding.inputStoreName.visibleIf(binding.toggleRole.checkedButtonId == R.id.buttonRoleSeller)
    }

    private fun save() {
        val email = binding.inputEmail.value
        val password = binding.inputPassword.editText?.text?.toString().orEmpty()
        var valid = binding.inputName.validate(binding.inputName.value.length >= 3, getString(R.string.error_name))
        valid = binding.inputEmail.validate(Patterns.EMAIL_ADDRESS.matcher(email).matches(), getString(R.string.error_email)) && valid
        valid = binding.inputPhone.validate(binding.inputPhone.value.filter { it.isDigit() }.length == 10, getString(R.string.error_phone)) && valid
        if (editing == null) {
            valid = binding.inputPassword.validate(password.length >= 10, getString(R.string.error_password)) && valid
        }
        if (!valid) return

        val role = when (binding.toggleRole.checkedButtonId) {
            R.id.buttonRoleSeller -> Role.SELLER
            R.id.buttonRoleAdmin -> Role.ADMIN
            else -> Role.BUYER
        }
        val status = statuses.firstOrNull { it.style.label == binding.dropdownStatus.text.toString() } ?: UserStatus.ACTIVE
        val base = editing
        val user = User(
            id = base?.id.orEmpty(),
            name = binding.inputName.value,
            email = email,
            phone = binding.inputPhone.value,
            role = role,
            status = status,
            document = binding.inputDocument.value,
            storeName = if (role == Role.SELLER) binding.inputStoreName.value else "",
            createdAt = base?.createdAt ?: System.currentTimeMillis()
        )
        // Cambiar el rol a administrador exige confirmación explícita (RF-ADU-03).
        if (role == Role.ADMIN && base?.role != Role.ADMIN) {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_shield)
                .setTitle(R.string.admin_role_title)
                .setMessage(R.string.admin_role_message)
                .setPositiveButton(R.string.action_confirm) { _, _ -> persist(user, password) }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        } else {
            persist(user, password)
        }
    }

    private fun persist(user: User, password: String) {
        binding.buttonSave.isEnabled = false
        lifecycleScope.launch {
            try {
                if (editing == null) {
                    AuthRepository.createUserAsAdmin(applicationContext, user, password)
                } else {
                    UserRepository.update(user)
                }
                finish()
            } catch (e: Exception) {
                binding.buttonSave.isEnabled = true
                if (e is FirebaseAuthUserCollisionException) {
                    binding.inputEmail.validate(false, getString(R.string.error_email_exists))
                } else {
                    binding.root.snack(errorMessage(e))
                }
            }
        }
    }

    private fun confirmDelete() {
        val user = editing ?: return
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_delete)
            .setTitle(R.string.delete_user_title)
            .setMessage(getString(R.string.delete_user_message, user.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    try {
                        UserRepository.delete(user)
                        finish()
                    } catch (e: Exception) {
                        binding.root.snack(errorMessage(e))
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
    }
}
