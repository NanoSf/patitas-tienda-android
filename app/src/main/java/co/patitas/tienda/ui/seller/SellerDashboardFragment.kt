package co.patitas.tienda.ui.seller

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import co.patitas.tienda.R
import co.patitas.tienda.data.OrderStatus
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.data.ProductStatus
import co.patitas.tienda.data.StatusStyle
import co.patitas.tienda.databinding.FragmentSellerDashboardBinding
import co.patitas.tienda.databinding.ViewStockRowBinding
import co.patitas.tienda.ui.common.OrderAdapter
import co.patitas.tienda.ui.common.RoleHostActivity
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.applyStatus

/** Panel del Vendedor (V-01): ventas, pedidos, productos activos, calificación y alertas. */
class SellerDashboardFragment : Fragment() {

    private var _binding: FragmentSellerDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSellerDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.buttonNewProduct.setOnClickListener {
            startActivity(Intent(requireContext(), ProductFormActivity::class.java))
        }
        binding.buttonAllOrders.setOnClickListener {
            (requireActivity() as RoleHostActivity).selectTab(R.id.tab_seller_orders)
        }
    }

    override fun onResume() {
        super.onResume()
        bind()
    }

    private fun bind() {
        val user = PatitasRepository.currentUser ?: return
        val repo = PatitasRepository
        binding.textGreeting.text = getString(R.string.home_greeting, user.name.substringBefore(" "))
        binding.textStore.text = getString(R.string.seller_store_status, user.storeName.ifBlank { user.name }, user.status.style.label)

        val orders = repo.ordersOfSeller(user.id)
        val newOrders = orders.count { it.status == OrderStatus.NEW }
        val products = repo.productsOfSeller(user.id)

        binding.kpiSales.imageKpiIcon.setImageResource(R.drawable.ic_trending_up)
        binding.kpiSales.textKpiValue.text = Format.moneyShort(orders.filter { it.status != OrderStatus.CANCELLED }.sumOf { it.total })
        binding.kpiSales.textKpiLabel.setText(R.string.kpi_sales_month)

        binding.kpiOrders.imageKpiIcon.setImageResource(R.drawable.ic_orders)
        binding.kpiOrders.textKpiValue.text = newOrders.toString()
        binding.kpiOrders.textKpiLabel.setText(R.string.kpi_new_orders)

        binding.kpiProducts.imageKpiIcon.setImageResource(R.drawable.ic_inventory)
        binding.kpiProducts.textKpiValue.text = products.count { it.status == ProductStatus.PUBLISHED }.toString()
        binding.kpiProducts.textKpiLabel.setText(R.string.kpi_active_products)

        binding.kpiRating.imageKpiIcon.setImageResource(R.drawable.ic_star)
        binding.kpiRating.textKpiValue.text = Format.rating(4.8)
        binding.kpiRating.textKpiLabel.setText(R.string.kpi_rating)

        // Alertas: productos con 5 unidades o menos
        binding.containerStock.removeAllViews()
        val low = products.filter { it.stock <= 5 }
        if (low.isEmpty()) {
            val row = ViewStockRowBinding.inflate(layoutInflater, binding.containerStock, true)
            row.imageIcon.setImageResource(R.drawable.ic_check_circle)
            row.textName.setText(R.string.seller_stock_ok)
            row.textValue.visibility = View.GONE
        }
        low.forEach { product ->
            val row = ViewStockRowBinding.inflate(layoutInflater, binding.containerStock, true)
            row.textName.text = product.name
            val style = if (product.stock == 0) {
                StatusStyle(getString(R.string.stock_zero), R.color.state_error, R.color.state_error_bg)
            } else {
                StatusStyle(resources.getQuantityString(R.plurals.stock_units, product.stock, product.stock), R.color.state_warning, R.color.state_warning_bg)
            }
            row.textValue.applyStatus(style)
        }

        val adapter = OrderAdapter(showBuyer = true) {
            (requireActivity() as RoleHostActivity).selectTab(R.id.tab_seller_orders)
        }
        binding.recyclerOrders.adapter = adapter
        adapter.submit(orders.filter { it.status == OrderStatus.NEW })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
