package co.patitas.tienda.ui.seller

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import co.patitas.tienda.R
import co.patitas.tienda.data.Product
import co.patitas.tienda.data.ProductRepository
import co.patitas.tienda.data.ProductStatus
import co.patitas.tienda.data.Session
import co.patitas.tienda.databinding.FragmentSellerProductsBinding
import co.patitas.tienda.util.errorMessage
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.visibleIf
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Panel de productos del Vendedor (V-02). CRUD completo sobre Firestore:
 * Crear (botón flotante), Consultar (lista en tiempo real y filtros), Actualizar (editar,
 * pausar, duplicar) y Eliminar (con confirmación y opción de deshacer).
 */
class SellerProductsFragment : Fragment() {

    private var _binding: FragmentSellerProductsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SellerProductAdapter
    private var filter: ProductStatus? = null
    private var products: List<Product> = emptyList()

    private val filters = listOf(
        null, ProductStatus.PUBLISHED, ProductStatus.IN_REVIEW, ProductStatus.PAUSED,
        ProductStatus.OUT_OF_STOCK, ProductStatus.DRAFT, ProductStatus.REJECTED
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSellerProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = SellerProductAdapter(
            onClick = { product -> openForm(product) },
            onMore = { product, anchor -> showActions(product, anchor) }
        )
        binding.recyclerProducts.adapter = adapter

        filters.forEach { status ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = status?.style?.label ?: getString(R.string.filter_all)
                isCheckable = true
                isChecked = status == null
                setOnClickListener {
                    filter = status
                    refresh()
                }
            }
            binding.chipGroupStatus.addView(chip)
        }

        binding.fabNew.setOnClickListener { openForm(null) }
        // El botón flotante se contrae al desplazarse para no tapar la lista.
        binding.recyclerProducts.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) binding.fabNew.shrink() else if (dy < 0) binding.fabNew.extend()
            }
        })

        binding.empty.imageEmpty.setImageResource(R.drawable.ic_inventory)
        binding.empty.textEmptyTitle.setText(R.string.seller_products_empty_title)
        binding.empty.textEmptyMessage.setText(R.string.seller_products_empty_message)

        // Consultar: los productos de la tienda llegan de Firestore en tiempo real.
        val sellerId = Session.user?.id.orEmpty()
        ProductRepository.ofSeller(sellerId).observe(viewLifecycleOwner) { list ->
            products = list
            refresh()
        }
    }

    private fun refresh() {
        val list = products.filter { filter == null || it.status == filter }
        adapter.submit(list)
        binding.textCount.text = resources.getQuantityString(R.plurals.products_count, list.size, list.size)
        binding.empty.root.visibleIf(list.isEmpty())
    }

    private fun openForm(product: Product?) {
        val intent = Intent(requireContext(), ProductFormActivity::class.java)
        product?.let { intent.putExtra(ProductFormActivity.EXTRA_PRODUCT_ID, it.id) }
        startActivity(intent)
    }

    /** Ejecuta una operación en Firestore y muestra el mensaje o el error. */
    private fun execute(successMessage: Int?, block: suspend () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                block()
                successMessage?.let { _binding?.root?.snack(it) }
            } catch (e: Exception) {
                _binding?.root?.snack(requireContext().errorMessage(e))
            }
        }
    }

    private fun showActions(product: Product, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_seller_product, popup.menu)
        popup.menu.findItem(R.id.action_toggle_pause).setTitle(
            if (product.status == ProductStatus.PAUSED) R.string.action_activate else R.string.action_pause
        )
        // Solo se puede pausar o reactivar un producto que el administrador ya aprobó.
        popup.menu.findItem(R.id.action_toggle_pause).isEnabled =
            product.status == ProductStatus.PUBLISHED || product.status == ProductStatus.PAUSED
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit -> openForm(product)
                R.id.action_toggle_pause -> togglePause(product)
                R.id.action_duplicate -> execute(R.string.product_duplicated) { ProductRepository.duplicate(product) }
                R.id.action_delete -> confirmDelete(product)
            }
            true
        }
        popup.show()
    }

    private fun togglePause(product: Product) {
        val paused = product.status == ProductStatus.PAUSED
        execute(if (paused) R.string.product_activated else R.string.product_paused) {
            ProductRepository.setStatus(product, if (paused) ProductStatus.PUBLISHED else ProductStatus.PAUSED)
        }
    }

    private fun confirmDelete(product: Product) {
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_delete)
            .setTitle(R.string.delete_product_title)
            .setMessage(getString(R.string.delete_product_message, product.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        // Se guarda la foto antes de borrar para poder deshacer.
                        val image = if (product.hasPhoto) ProductRepository.fullImage(product.id) else null
                        ProductRepository.delete(product)
                        Snackbar.make(binding.root, R.string.product_deleted, Snackbar.LENGTH_LONG)
                            .setAction(R.string.action_undo) { execute(null) { ProductRepository.restore(product, image) } }
                            .show()
                    } catch (e: Exception) {
                        _binding?.root?.snack(requireContext().errorMessage(e))
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
