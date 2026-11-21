package co.patitas.tienda.ui.seller

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import co.patitas.tienda.R
import co.patitas.tienda.data.Order
import co.patitas.tienda.data.OrderRepository
import co.patitas.tienda.data.OrderStatus
import co.patitas.tienda.data.Product
import co.patitas.tienda.data.ProductRepository
import co.patitas.tienda.data.ProductStatus
import co.patitas.tienda.data.Reports
import co.patitas.tienda.data.Session
import co.patitas.tienda.data.StatusStyle
import co.patitas.tienda.databinding.FragmentSellerDashboardBinding
import co.patitas.tienda.databinding.ViewStockRowBinding
import co.patitas.tienda.ui.common.OrderAdapter
import co.patitas.tienda.ui.common.RoleHostActivity
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.applyStatus

/**
 * Panel del Vendedor (V-01): ventas del mes, pedidos nuevos, productos activos, calificación
 * y alertas de stock, calculados con los productos y pedidos reales de Firestore.
 */
class SellerDashboardFragment : Fragment() {

    private var _binding: FragmentSellerDashboardBinding? = null
    private val binding get() = _binding!!
    private var products: List<Product> = emptyList()
    private var orders: List<Order> = emptyList()
    private lateinit var adapter: OrderAdapter

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
        adapter = OrderAdapter(showBuyer = true) {
            (requireActivity() as RoleHostActivity).selectTab(R.id.tab_seller_orders)
        }
        binding.recyclerOrders.adapter = adapter

        val uid = Session.user?.id.orEmpty()
        ProductRepository.ofSeller(uid).observe(viewLifecycleOwner) { list ->
            products = list
            bind()
        }
        OrderRepository.ofSeller(uid).observe(viewLifecycleOwner) { list ->
            orders = list
            bind()
        }
        bind()
    }

    private fun bind() {
        val user = Session.user ?: return
        binding.textGreeting.text = getString(R.string.home_greeting, user.name.substringBefore(" "))
        binding.textStore.text = getString(R.string.seller_store_status, user.displayName, user.status.style.label)

        val newOrders = orders.count { it.status == OrderStatus.NEW }

        binding.kpiSales.imageKpiIcon.setImageResource(R.drawable.ic_trending_up)
        binding.kpiSales.textKpiValue.text = Format.moneyShort(Reports.salesThisMonth(orders))
        binding.kpiSales.textKpiLabel.setText(R.string.kpi_sales_month)

        binding.kpiOrders.imageKpiIcon.setImageResource(R.drawable.ic_orders)
        binding.kpiOrders.textKpiValue.text = newOrders.toString()
        binding.kpiOrders.textKpiLabel.setText(R.string.kpi_new_orders)

        binding.kpiProducts.imageKpiIcon.setImageResource(R.drawable.ic_inventory)
        binding.kpiProducts.textKpiValue.text = products.count { it.status == ProductStatus.PUBLISHED }.toString()
        binding.kpiProducts.textKpiLabel.setText(R.string.kpi_active_products)

        binding.kpiRating.imageKpiIcon.setImageResource(R.drawable.ic_star)
        val rated = products.filter { it.reviews > 0 }
        binding.kpiRating.textKpiValue.text = if (rated.isEmpty()) "—" else Format.rating(rated.map { it.rating }.average())
        binding.kpiRating.textKpiLabel.setText(R.string.kpi_rating)

        // Alertas: productos con 5 unidades o menos
        binding.containerStock.removeAllViews()
        val low = products.filter { it.stock <= 5 && it.status != ProductStatus.DRAFT }
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

        adapter.submit(orders.filter { it.status == OrderStatus.NEW })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
