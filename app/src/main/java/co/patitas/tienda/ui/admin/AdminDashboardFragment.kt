package co.patitas.tienda.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import co.patitas.tienda.R
import co.patitas.tienda.data.Order
import co.patitas.tienda.data.OrderRepository
import co.patitas.tienda.data.Product
import co.patitas.tienda.data.ProductRepository
import co.patitas.tienda.data.ProductStatus
import co.patitas.tienda.data.Reports
import co.patitas.tienda.data.Role
import co.patitas.tienda.data.Session
import co.patitas.tienda.data.User
import co.patitas.tienda.data.UserRepository
import co.patitas.tienda.data.UserStatus
import co.patitas.tienda.databinding.FragmentAdminDashboardBinding
import co.patitas.tienda.databinding.LayoutKpiBinding
import co.patitas.tienda.databinding.ViewSettingRowBinding
import co.patitas.tienda.databinding.ViewStockRowBinding
import co.patitas.tienda.ui.common.RoleHostActivity
import co.patitas.tienda.util.Format

/**
 * Dashboard principal del Administrador (A-01). Los indicadores, las tareas pendientes y la
 * actividad reciente se calculan con los usuarios, productos y pedidos de Firestore en tiempo real.
 */
class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!
    private var users: List<User> = emptyList()
    private var products: List<Product> = emptyList()
    private var orders: List<Order> = emptyList()

    /** Infla el layout del fragmento con ViewBinding. */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** Configura la vista: adaptadores, eventos y observadores de Firestore. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        UserRepository.all().observe(viewLifecycleOwner) { users = it; bind() }
        ProductRepository.all().observe(viewLifecycleOwner) { products = it; bind() }
        OrderRepository.all().observe(viewLifecycleOwner) { orders = it; bind() }
        bind()
    }

    /** Pinta los indicadores y listas con los datos más recientes de Firestore. */
    private fun bind() {
        val host = requireActivity() as RoleHostActivity
        binding.textGreeting.text = getString(R.string.home_greeting, Session.user?.name?.substringBefore(" ").orEmpty())

        val activeUsers = users.count { it.status == UserStatus.ACTIVE }
        val inReview = products.count { it.status == ProductStatus.IN_REVIEW }
        val pendingSellers = users.count { it.role == Role.SELLER && it.status == UserStatus.PENDING }

        kpi(binding.kpiUsers, R.drawable.ic_group, Format.number(activeUsers), R.string.kpi_active_users)
        kpi(binding.kpiSales, R.drawable.ic_trending_up, Format.moneyShort(Reports.salesThisMonth(orders)), R.string.kpi_sales_month)
        kpi(binding.kpiReview, R.drawable.ic_inventory, inReview.toString(), R.string.kpi_in_review)
        kpi(binding.kpiSellers, R.drawable.ic_store, users.count { it.role == Role.SELLER }.toString(), R.string.kpi_sellers)

        row(binding.rowVerifySellers, R.drawable.ic_verified,
            resources.getQuantityString(R.plurals.task_verify_sellers, pendingSellers, pendingSellers),
            getString(R.string.task_verify_sellers_detail)) { host.selectTab(R.id.tab_admin_users) }
        row(binding.rowModerate, R.drawable.ic_inventory,
            resources.getQuantityString(R.plurals.task_moderate, inReview, inReview),
            getString(R.string.task_moderate_detail)) { host.selectTab(R.id.tab_admin_products) }
        row(binding.rowSalesReport, R.drawable.ic_report, getString(R.string.task_report),
            getString(R.string.task_report_detail)) { host.selectTab(R.id.tab_admin_reports) }

        // Actividad reciente: últimos pedidos y registros (RNF-SEG-04)
        val events = orders.take(4).map { order ->
            getString(R.string.activity_order, order.code, order.buyerName, order.sellerName, Format.money(order.total)) to order.createdAt
        } + users.take(3).map { user ->
            getString(R.string.activity_user, user.name, user.role.label) to user.createdAt
        }
        binding.containerAudit.removeAllViews()
        if (events.isEmpty()) {
            val line = ViewStockRowBinding.inflate(layoutInflater, binding.containerAudit, true)
            line.imageIcon.setImageResource(R.drawable.ic_schedule)
            line.textName.setText(R.string.activity_empty)
            line.textValue.visibility = View.GONE
        }
        events.sortedByDescending { it.second }.take(6).forEach { (text, at) ->
            val line = ViewStockRowBinding.inflate(layoutInflater, binding.containerAudit, true)
            line.imageIcon.setImageResource(R.drawable.ic_schedule)
            line.imageIcon.imageTintList = binding.root.context.getColorStateList(R.color.ink_400)
            line.textName.text = text
            line.textName.maxLines = 2
            line.textValue.text = co.patitas.tienda.data.formatDate(at)
        }
    }

    /** Configura una tarjeta de indicador (ícono, valor y etiqueta). */
    private fun kpi(kpi: LayoutKpiBinding, @DrawableRes icon: Int, value: String, label: Int) {
        kpi.imageKpiIcon.setImageResource(icon)
        kpi.textKpiValue.text = value
        kpi.textKpiLabel.setText(label)
    }

    /** Configura una fila de opción (ícono, título, detalle y acción). */
    private fun row(row: ViewSettingRowBinding, @DrawableRes icon: Int, title: String, detail: String, onClick: () -> Unit) {
        row.imageRowIcon.setImageResource(icon)
        row.textRowTitle.text = title
        row.textRowDetail.text = detail
        row.root.setOnClickListener { onClick() }
    }

    /** Libera el binding cuando se destruye la vista para evitar fugas de memoria. */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
