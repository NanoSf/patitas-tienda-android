package co.patitas.tienda.ui.admin

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import co.patitas.tienda.R
import co.patitas.tienda.data.Product
import co.patitas.tienda.data.ProductRepository
import co.patitas.tienda.data.ProductStatus
import co.patitas.tienda.databinding.DialogReasonBinding
import co.patitas.tienda.databinding.FragmentAdminProductsBinding
import co.patitas.tienda.util.errorMessage
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.validate
import co.patitas.tienda.util.value
import co.patitas.tienda.util.visibleIf
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Gestión de productos del Administrador (A-05) sobre Firestore: consultar todos los productos,
 * aprobar, rechazar con motivo, despublicar y eliminar.
 */
class AdminProductsFragment : Fragment() {

    private var _binding: FragmentAdminProductsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ModerationAdapter
    private var filter: ProductStatus? = ProductStatus.IN_REVIEW
    private var products: List<Product> = emptyList()

    /** Infla el layout del fragmento con ViewBinding. */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** Configura la vista: adaptadores, eventos y observadores de Firestore. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ModerationAdapter(
            onApprove = { product -> approve(product) },
            onReject = { product -> reject(product) },
            onDelete = { product -> confirmDelete(product) }
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
        ProductRepository.all().observe(viewLifecycleOwner) { list ->
            products = list
            refresh()
        }
    }

    /** Aplica el filtro seleccionado a la lista y actualiza el contador y el estado vacío. */
    private fun refresh() {
        val list = products.filter { filter == null || it.status == filter }
        adapter.submit(list)
        binding.textCount.text = resources.getQuantityString(R.plurals.products_count, list.size, list.size)
        binding.empty.root.visibleIf(list.isEmpty())
    }

    /** Aprueba el producto: queda publicado (o agotado si no tiene stock). */
    private fun approve(product: Product) {
        val status = if (product.stock > 0) ProductStatus.PUBLISHED else ProductStatus.OUT_OF_STOCK
        execute(getString(R.string.product_approved, product.name)) { ProductRepository.setStatus(product, status) }
    }

    /** Ejecuta la operación en Firestore; la lista se actualiza sola con el listener. */
    private fun execute(message: String, block: suspend () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                block()
                _binding?.root?.snack(message)
            } catch (e: Exception) {
                _binding?.root?.snack(requireContext().errorMessage(e))
            }
        }
    }

    /** Pide confirmación antes de eliminar. */
    private fun confirmDelete(product: Product) {
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_delete)
            .setTitle(R.string.delete_product_title)
            .setMessage(getString(R.string.delete_product_message, product.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                execute(getString(R.string.product_deleted)) { ProductRepository.delete(product) }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
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
            val status = if (unpublish) ProductStatus.PAUSED else ProductStatus.REJECTED
            val reason = dialog.inputReason.value
            execute(getString(R.string.product_rejected, product.name)) { ProductRepository.setStatus(product, status, reason) }
        }
    }

    /** Libera el binding cuando se destruye la vista para evitar fugas de memoria. */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
