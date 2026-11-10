package co.patitas.tienda.ui.common

import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import co.patitas.tienda.R
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.data.Role
import co.patitas.tienda.data.User
import co.patitas.tienda.databinding.DialogEditProfileBinding
import co.patitas.tienda.databinding.FragmentProfileBinding
import co.patitas.tienda.databinding.ViewSettingRowBinding
import co.patitas.tienda.ui.auth.LoginActivity
import co.patitas.tienda.ui.auth.WelcomeActivity
import co.patitas.tienda.util.BiometricHelper
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.SessionManager
import co.patitas.tienda.util.applyStatus
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.validate
import co.patitas.tienda.util.value
import co.patitas.tienda.util.visibleIf
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Perfil del usuario: CRUD de perfil del Comprador (C-08) y perfil del Vendedor (V-07).
 * Si el rol es Vendedor muestra además los datos de la tienda y sus indicadores.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        session = SessionManager(requireContext())
        val user = PatitasRepository.currentUser
        if (user == null) {
            bindGuest()
            return
        }
        bindUser(user)
        bindPreferences()
        bindPrivacy(user)

        binding.buttonEdit.setOnClickListener { editProfile(user) }
        binding.buttonPhoto.setOnClickListener { binding.root.snack(R.string.photo_camera_pending) }
        binding.buttonLogout.setOnClickListener { (requireActivity() as RoleHostActivity).requestLogout() }
    }

    private fun bindGuest() {
        binding.groupProfile.visibleIf(false)
        binding.buttonPhoto.visibleIf(false)
        binding.textAvatar.text = "?"
        binding.textName.setText(R.string.guest_name)
        binding.textEmail.setText(R.string.guest_email)
        binding.textRole.setText(R.string.guest_role)
        binding.textStatus.visibleIf(false)
        binding.guest.root.visibleIf(true)
        binding.guest.imageEmpty.setImageResource(R.drawable.ic_person)
        binding.guest.textEmptyTitle.setText(R.string.guest_login_title)
        binding.guest.textEmptyMessage.setText(R.string.profile_guest_message)
        binding.guest.buttonEmptyAction.setText(R.string.action_login)
        binding.guest.buttonEmptyAction.visibleIf(true)
        binding.guest.buttonEmptyAction.setOnClickListener {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
        }
    }

    private fun bindUser(user: User) {
        binding.textAvatar.text = user.initials
        binding.textName.text = if (user.role == Role.SELLER && user.storeName.isNotBlank()) user.storeName else user.name
        binding.textEmail.text = user.email
        binding.textRole.text = user.role.label
        binding.textStatus.applyStatus(user.status.style)

        row(binding.rowPhone, R.drawable.ic_phone, getString(R.string.field_phone), user.phone) { editProfile(user) }
        row(binding.rowDocument, R.drawable.ic_verified, getString(R.string.profile_document),
            user.document.ifBlank { getString(R.string.profile_not_registered) })
        row(binding.rowAddress, R.drawable.ic_location, getString(R.string.profile_addresses),
            getString(R.string.profile_address_default)) { binding.root.snack(R.string.addresses_pending) }

        val isSeller = user.role == Role.SELLER
        binding.groupStore.visibleIf(isSeller)
        if (isSeller) {
            binding.kpiRating.imageKpiIcon.setImageResource(R.drawable.ic_star)
            binding.kpiRating.textKpiValue.text = Format.rating(4.8)
            binding.kpiRating.textKpiLabel.setText(R.string.kpi_rating)
            binding.kpiSales.imageKpiIcon.setImageResource(R.drawable.ic_trending_up)
            binding.kpiSales.textKpiValue.text = Format.moneyShort(14_800_000)
            binding.kpiSales.textKpiLabel.setText(R.string.kpi_sales_month)
            row(binding.rowStoreName, R.drawable.ic_store, getString(R.string.field_store_name), user.storeName)
            row(binding.rowSchedule, R.drawable.ic_schedule, getString(R.string.profile_schedule), getString(R.string.profile_schedule_value))
            row(binding.rowDispatch, R.drawable.ic_my_location, getString(R.string.profile_dispatch), getString(R.string.profile_dispatch_value)) {
                binding.root.snack(R.string.gps_located)
            }
            row(binding.rowBank, R.drawable.ic_bank, getString(R.string.profile_bank), getString(R.string.profile_bank_value)) {
                binding.root.snack(R.string.bank_change_note)
            }
        }
    }

    private fun bindPreferences() {
        binding.switchBiometric.isChecked = session.biometricEnabled
        binding.switchBiometric.setOnCheckedChangeListener { button, checked ->
            if (!button.isPressed) return@setOnCheckedChangeListener
            if (!checked) {
                session.biometricEnabled = false
                binding.root.snack(R.string.biometric_disabled)
                return@setOnCheckedChangeListener
            }
            if (!BiometricHelper.isAvailable(requireContext())) {
                button.isChecked = false
                binding.root.snack(BiometricHelper.unavailableReason(requireContext()))
                return@setOnCheckedChangeListener
            }
            // Activar la huella exige confirmar con la propia huella (RF-PER-05).
            BiometricHelper.authenticate(
                requireActivity(),
                onSuccess = {
                    session.biometricEnabled = true
                    session.lastEmail = PatitasRepository.currentUser?.email
                    binding.root.snack(R.string.biometric_enabled)
                },
                onError = { message ->
                    button.isChecked = false
                    binding.root.snack(message)
                }
            )
        }
        binding.switchMarketing.setOnCheckedChangeListener { button, checked ->
            if (button.isPressed) binding.root.snack(if (checked) R.string.marketing_on else R.string.marketing_off)
        }
    }

    private fun bindPrivacy(user: User) {
        row(binding.rowDownload, R.drawable.ic_download, getString(R.string.profile_download), getString(R.string.profile_download_detail)) {
            binding.root.snack(getString(R.string.download_sent, user.email))
        }
        row(binding.rowDeleteAccount, R.drawable.ic_delete, getString(R.string.profile_delete), getString(R.string.profile_delete_detail)) {
            confirmDelete(user)
        }
        val red = ContextCompat.getColor(requireContext(), R.color.state_error)
        binding.rowDeleteAccount.textRowTitle.setTextColor(red)
        binding.rowDeleteAccount.imageRowIcon.imageTintList = ColorStateList.valueOf(red)
    }

    private fun row(row: ViewSettingRowBinding, @DrawableRes icon: Int, title: String, detail: String, onClick: (() -> Unit)? = null) {
        row.imageRowIcon.setImageResource(icon)
        row.textRowTitle.text = title
        row.textRowDetail.text = detail
        row.imageRowChevron.visibleIf(onClick != null)
        if (onClick != null) row.root.setOnClickListener { onClick() } else row.root.isClickable = false
    }

    private fun editProfile(user: User) {
        val dialog = DialogEditProfileBinding.inflate(layoutInflater)
        dialog.inputDialogName.editText?.setText(user.name)
        dialog.inputDialogPhone.editText?.setText(user.phone)
        val alert = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_edit_title)
            .setView(dialog.root)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .show()
        // El botón Guardar valida antes de cerrar el diálogo.
        alert.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            var valid = dialog.inputDialogName.validate(dialog.inputDialogName.value.length >= 3, getString(R.string.error_name))
            valid = dialog.inputDialogPhone.validate(dialog.inputDialogPhone.value.filter { it.isDigit() }.length == 10, getString(R.string.error_phone)) && valid
            if (!valid) return@setOnClickListener
            user.name = dialog.inputDialogName.value
            user.phone = dialog.inputDialogPhone.value
            bindUser(user)
            alert.dismiss()
            binding.root.snack(R.string.profile_updated)
        }
    }

    private fun confirmDelete(user: User) {
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_delete)
            .setTitle(R.string.delete_account_title)
            .setMessage(R.string.delete_account_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                PatitasRepository.deleteUser(user)
                PatitasRepository.logout()
                session.clearBiometric()
                startActivity(Intent(requireContext(), WelcomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
