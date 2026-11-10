package co.patitas.tienda.ui.seller

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import co.patitas.tienda.R
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.data.Product
import co.patitas.tienda.data.ProductStatus
import co.patitas.tienda.databinding.FragmentSellerProductsBinding
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.visibleIf
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * Panel de productos del Vendedor (V-02). CRUD completo:
 * Crear (botón flotante), Consultar (lista y filtros), Actualizar (editar, pausar, duplicar)
 * y Eliminar (con confirmación y opción de deshacer).
 */
class SellerProductsFragment : Fragment() {

    private var _binding: FragmentSellerProductsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SellerProductAdapter
    private var filter: ProductStatus? = null

    private val filters = listOf(
        null, ProductStatus.PUBLISHED, ProductStatus.IN_REVIEW, ProductStatus.PAUSED,
        ProductStatus.OUT_OF_STOCK, ProductStatus.DRAFT
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
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun sellerId() = PatitasRepository.currentUser?.id ?: -1

    private fun refresh() {
        val list = PatitasRepository.productsOfSeller(sellerId(), filter)
        adapter.submit(list)
        binding.textCount.text = resources.getQuantityString(R.plurals.products_count, list.size, list.size)
        binding.empty.root.visibleIf(list.isEmpty())
    }

    private fun openForm(product: Product?) {
        val intent = Intent(requireContext(), ProductFormActivity::class.java)
        product?.let { intent.putExtra(ProductFormActivity.EXTRA_PRODUCT_ID, it.id) }
        startActivity(intent)
    }

    private fun showActions(product: Product, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_seller_product, popup.menu)
        popup.menu.findItem(R.id.action_toggle_pause).setTitle(
            if (product.status == ProductStatus.PAUSED) R.string.action_activate else R.string.action_pause
        )
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit -> openForm(product)
                R.id.action_toggle_pause -> togglePause(product)
                R.id.action_duplicate -> {
                    PatitasRepository.duplicateProduct(product)
                    refresh()
                    binding.root.snack(R.string.product_duplicated)
                }
                R.id.action_delete -> confirmDelete(product)
            }
            true
        }
        popup.show()
    }

    private fun togglePause(product: Product) {
        product.status = if (product.status == ProductStatus.PAUSED) ProductStatus.PUBLISHED else ProductStatus.PAUSED
        refresh()
        binding.root.snack(if (product.status == ProductStatus.PAUSED) R.string.product_paused else R.string.product_activated)
    }

    private fun confirmDelete(product: Product) {
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_delete)
            .setTitle(R.string.delete_product_title)
            .setMessage(getString(R.string.delete_product_message, product.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                PatitasRepository.deleteProduct(product)
                refresh()
                Snackbar.make(binding.root, R.string.product_deleted, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo) {
                        PatitasRepository.saveProduct(product)
                        refresh()
                    }
                    .show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
