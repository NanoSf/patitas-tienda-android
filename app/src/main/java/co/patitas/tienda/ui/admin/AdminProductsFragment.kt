package co.patitas.tienda.ui.admin

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import co.patitas.tienda.R
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.data.Product
import co.patitas.tienda.data.ProductStatus
import co.patitas.tienda.databinding.DialogReasonBinding
import co.patitas.tienda.databinding.FragmentAdminProductsBinding
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.validate
import co.patitas.tienda.util.value
import co.patitas.tienda.util.visibleIf
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Gestión de productos del Administrador (A-05): aprobar, rechazar con motivo y despublicar. */
class AdminProductsFragment : Fragment() {

    private var _binding: FragmentAdminProductsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ModerationAdapter
    private var filter: ProductStatus? = ProductStatus.IN_REVIEW

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ModerationAdapter(
            onApprove = { product -> approve(product) },
            onReject = { product -> reject(product) }
        )
        binding.recyclerProducts.adapter = adapter

        listOf(ProductStatus.IN_REVIEW, ProductStatus.PUBLISHED, ProductStatus.REJECTED, null).forEach { status ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = status?.style?.label ?: getString(R.string.filter_all)
                isCheckable = true
                isChecked = status == filter
                setOnClickListener {
                    filter = status
                    refresh()
                }
            }
            binding.chipGroupStatus.addView(chip)
        }
        binding.empty.imageEmpty.setImageResource(R.drawable.ic_check_circle)
        binding.empty.textEmptyTitle.setText(R.string.moderation_empty_title)
        binding.empty.textEmptyMessage.setText(R.string.moderation_empty_message)
        refresh()
    }

    private fun refresh() {
        val list = PatitasRepository.products.filter { filter == null || it.status == filter }
        adapter.submit(list)
        binding.textCount.text = resources.getQuantityString(R.plurals.products_count, list.size, list.size)
        binding.empty.root.visibleIf(list.isEmpty())
    }

    private fun approve(product: Product) {
        product.status = if (product.stock > 0) ProductStatus.PUBLISHED else ProductStatus.OUT_OF_STOCK
        refresh()
        binding.root.snack(getString(R.string.product_approved, product.name))
    }

    /** Rechazar o despublicar exige un comentario para el vendedor (RF-ADP-01). */
    private fun reject(product: Product) {
        val unpublish = product.status == ProductStatus.PUBLISHED
        val dialog = DialogReasonBinding.inflate(layoutInflater)
        val alert = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (unpublish) R.string.unpublish_title else R.string.reject_title)
            .setMessage(getString(R.string.reject_message, product.sellerName))
            .setView(dialog.root)
            .setPositiveButton(if (unpublish) R.string.action_unpublish else R.string.action_reject, null)
            .setNegativeButton(R.string.action_cancel, null)
            .show()
        alert.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            if (!dialog.inputReason.validate(dialog.inputReason.value.length >= 10, getString(R.string.error_reason))) {
                return@setOnClickListener
            }
            alert.dismiss()
            product.status = if (unpublish) ProductStatus.PAUSED else ProductStatus.REJECTED
            refresh()
            binding.root.snack(getString(R.string.product_rejected, product.name))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
