package co.patitas.tienda.ui.admin

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import co.patitas.tienda.R
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.data.Role
import co.patitas.tienda.data.User
import co.patitas.tienda.data.UserStatus
import co.patitas.tienda.databinding.DialogReasonBinding
import co.patitas.tienda.databinding.FragmentAdminUsersBinding
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.validate
import co.patitas.tienda.util.value
import co.patitas.tienda.util.visibleIf
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Gestión de usuarios del Administrador (A-02 a A-04, RF-ADU-01 a 06):
 * listado con búsqueda y filtros, creación, edición, suspensión con motivo y eliminación.
 */
class AdminUsersFragment : Fragment() {

    private var _binding: FragmentAdminUsersBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: UserAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = UserAdapter(
            onClick = { user -> openForm(user) },
            onMore = { user, anchor -> showActions(user, anchor) }
        )
        binding.recyclerUsers.adapter = adapter
        binding.chipGroupRole.setOnCheckedStateChangeListener { _, _ -> refresh() }
        binding.inputSearch.editText?.doAfterTextChanged { refresh() }
        binding.fabNew.setOnClickListener { openForm(null) }

        binding.empty.imageEmpty.setImageResource(R.drawable.ic_group)
        binding.empty.textEmptyTitle.setText(R.string.users_empty_title)
        binding.empty.textEmptyMessage.setText(R.string.users_empty_message)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val query = binding.inputSearch.value
        val list = PatitasRepository.users.filter { user ->
            when (binding.chipGroupRole.checkedChipId) {
                R.id.chipRoleBuyer -> user.role == Role.BUYER
                R.id.chipRoleSeller -> user.role == Role.SELLER
                R.id.chipRoleAdmin -> user.role == Role.ADMIN
                R.id.chipRolePending -> user.status == UserStatus.PENDING
                else -> true
            }
        }.filter { user ->
            query.isBlank() || user.name.contains(query, true) || user.email.contains(query, true) ||
                user.document.contains(query, true) || user.storeName.contains(query, true)
        }
        adapter.submit(list)
        binding.textCount.text = resources.getQuantityString(R.plurals.users_count, list.size, list.size)
        binding.empty.root.visibleIf(list.isEmpty())
    }

    private fun openForm(user: User?) {
        val intent = Intent(requireContext(), UserFormActivity::class.java)
        user?.let { intent.putExtra(UserFormActivity.EXTRA_USER_ID, it.id) }
        startActivity(intent)
    }

    private fun showActions(user: User, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_admin_user, popup.menu)
        popup.menu.findItem(R.id.action_toggle_suspend).setTitle(
            if (user.status == UserStatus.SUSPENDED) R.string.action_reactivate
            else if (user.status == UserStatus.PENDING) R.string.action_verify
            else R.string.action_suspend
        )
        val isMe = user.id == PatitasRepository.currentUser?.id
        popup.menu.findItem(R.id.action_delete).isEnabled = !isMe
        popup.menu.findItem(R.id.action_toggle_suspend).isEnabled = !isMe
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit -> openForm(user)
                R.id.action_toggle_suspend -> when (user.status) {
                    UserStatus.ACTIVE -> suspend(user)
                    UserStatus.SUSPENDED -> setStatus(user, UserStatus.ACTIVE, R.string.user_reactivated)
                    UserStatus.PENDING -> setStatus(user, UserStatus.ACTIVE, R.string.user_verified)
                }
                R.id.action_delete -> confirmDelete(user)
            }
            true
        }
        popup.show()
    }

    private fun setStatus(user: User, status: UserStatus, messageRes: Int) {
        user.status = status
        refresh()
        binding.root.snack(getString(messageRes, user.name))
    }

    /** Suspensión con motivo obligatorio que queda registrado en la auditoría (RF-ADU-04). */
    private fun suspend(user: User) {
        val dialog = DialogReasonBinding.inflate(layoutInflater)
        val alert = MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_block)
            .setTitle(getString(R.string.suspend_title, user.name))
            .setMessage(R.string.suspend_message)
            .setView(dialog.root)
            .setPositiveButton(R.string.action_suspend, null)
            .setNegativeButton(R.string.action_cancel, null)
            .show()
        alert.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            if (!dialog.inputReason.validate(dialog.inputReason.value.length >= 10, getString(R.string.error_reason))) {
                return@setOnClickListener
            }
            alert.dismiss()
            setStatus(user, UserStatus.SUSPENDED, R.string.user_suspended)
        }
    }

    private fun confirmDelete(user: User) {
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_delete)
            .setTitle(R.string.delete_user_title)
            .setMessage(getString(R.string.delete_user_message, user.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                PatitasRepository.deleteUser(user)
                refresh()
                binding.root.snack(getString(R.string.user_deleted, user.name))
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
