package co.patitas.tienda.ui.admin

import android.os.Bundle
import android.util.Patterns
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import co.patitas.tienda.R
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.data.Role
import co.patitas.tienda.data.User
import co.patitas.tienda.data.UserStatus
import co.patitas.tienda.databinding.ActivityUserFormBinding
import co.patitas.tienda.util.validate
import co.patitas.tienda.util.value
import co.patitas.tienda.util.visibleIf
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Crear y editar usuarios (A-03, RF-ADU-02 y 03). Solo un administrador puede crear
 * otros administradores; los cambios de rol quedan registrados en la auditoría.
 */
class UserFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserFormBinding
    private var editing: User? = null
    private val statuses = UserStatus.entries

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getIntExtra(EXTRA_USER_ID, -1)
        editing = PatitasRepository.users.firstOrNull { it.id == id }
        binding.toolbar.setTitle(if (editing == null) R.string.user_form_new else R.string.user_form_edit)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.dropdownStatus.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, statuses.map { it.style.label })
        )

        val user = editing
        if (user != null) {
            binding.textInitials.text = user.initials
            binding.textCreated.text = getString(R.string.user_form_id, user.id)
            binding.inputName.editText?.setText(user.name)
            binding.inputEmail.editText?.setText(user.email)
            binding.inputPhone.editText?.setText(user.phone)
            binding.inputDocument.editText?.setText(user.document)
            binding.toggleRole.check(
                when (user.role) {
                    Role.BUYER -> R.id.buttonRoleBuyer
                    Role.SELLER -> R.id.buttonRoleSeller
                    Role.ADMIN -> R.id.buttonRoleAdmin
                }
            )
            binding.dropdownStatus.setText(user.status.style.label, false)
            val isMe = user.id == PatitasRepository.currentUser?.id
            binding.buttonDelete.visibleIf(!isMe)
        } else {
            binding.textInitials.text = "+"
            binding.textCreated.setText(R.string.user_form_new_hint)
            binding.dropdownStatus.setText(UserStatus.ACTIVE.style.label, false)
        }

        binding.buttonSave.setOnClickListener { save() }
        binding.buttonDelete.setOnClickListener { confirmDelete() }
    }

    private fun save() {
        val email = binding.inputEmail.value
        var valid = binding.inputName.validate(binding.inputName.value.length >= 3, getString(R.string.error_name))
        valid = binding.inputEmail.validate(Patterns.EMAIL_ADDRESS.matcher(email).matches(), getString(R.string.error_email)) && valid
        val duplicated = PatitasRepository.users.any { it.email.equals(email, true) && it.id != editing?.id }
        if (valid && duplicated) valid = binding.inputEmail.validate(false, getString(R.string.error_email_exists))
        valid = binding.inputPhone.validate(binding.inputPhone.value.filter { it.isDigit() }.length == 10, getString(R.string.error_phone)) && valid
        if (!valid) return

        val role = when (binding.toggleRole.checkedButtonId) {
            R.id.buttonRoleSeller -> Role.SELLER
            R.id.buttonRoleAdmin -> Role.ADMIN
            else -> Role.BUYER
        }
        val status = statuses.firstOrNull { it.style.label == binding.dropdownStatus.text.toString() } ?: UserStatus.ACTIVE
        val base = editing
        val user = User(
            id = base?.id ?: PatitasRepository.newUserId(),
            name = binding.inputName.value,
            email = email,
            phone = binding.inputPhone.value,
            role = role,
            status = status,
            document = binding.inputDocument.value,
            storeName = base?.storeName ?: ""
        )
        // Cambiar el rol a administrador exige confirmación explícita (RF-ADU-03).
        if (role == Role.ADMIN && base?.role != Role.ADMIN) {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_shield)
                .setTitle(R.string.admin_role_title)
                .setMessage(R.string.admin_role_message)
                .setPositiveButton(R.string.action_confirm) { _, _ -> persist(user) }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        } else {
            persist(user)
        }
    }

    private fun persist(user: User) {
        PatitasRepository.saveUser(user)
        finish()
    }

    private fun confirmDelete() {
        val user = editing ?: return
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_delete)
            .setTitle(R.string.delete_user_title)
            .setMessage(getString(R.string.delete_user_message, user.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                PatitasRepository.deleteUser(user)
                finish()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
    }
}
