package co.patitas.tienda.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import co.patitas.tienda.R
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.data.ProductStatus
import co.patitas.tienda.data.Role
import co.patitas.tienda.data.UserStatus
import co.patitas.tienda.databinding.FragmentAdminDashboardBinding
import co.patitas.tienda.databinding.ViewSettingRowBinding
import co.patitas.tienda.databinding.ViewStockRowBinding
import co.patitas.tienda.ui.common.RoleHostActivity
import co.patitas.tienda.util.Format

/** Dashboard principal del Administrador (A-01). */
class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        bind()
    }

    private fun bind() {
        val repo = PatitasRepository
        val host = requireActivity() as RoleHostActivity
        binding.textGreeting.text = getString(R.string.home_greeting, repo.currentUser?.name?.substringBefore(" ") ?: "")

        val activeUsers = repo.users.count { it.status == UserStatus.ACTIVE }
        val inReview = repo.products.count { it.status == ProductStatus.IN_REVIEW }
        val pendingSellers = repo.users.count { it.role == Role.SELLER && it.status == UserStatus.PENDING }

        kpi(binding.kpiUsers, R.drawable.ic_group, Format.number(activeUsers), R.string.kpi_active_users)
        kpi(binding.kpiSales, R.drawable.ic_trending_up, Format.moneyShort(repo.salesByPeriod(1).last().value), R.string.kpi_sales_month)
        kpi(binding.kpiReview, R.drawable.ic_inventory, inReview.toString(), R.string.kpi_in_review)
        kpi(binding.kpiSellers, R.drawable.ic_store, repo.users.count { it.role == Role.SELLER }.toString(), R.string.kpi_sellers)

        row(binding.rowVerifySellers, R.drawable.ic_verified,
            resources.getQuantityString(R.plurals.task_verify_sellers, pendingSellers, pendingSellers),
            getString(R.string.task_verify_sellers_detail)) { host.selectTab(R.id.tab_admin_users) }
        row(binding.rowModerate, R.drawable.ic_inventory,
            resources.getQuantityString(R.plurals.task_moderate, inReview, inReview),
            getString(R.string.task_moderate_detail)) { host.selectTab(R.id.tab_admin_products) }
        row(binding.rowSalesReport, R.drawable.ic_report, getString(R.string.task_report),
            getString(R.string.task_report_detail)) { host.selectTab(R.id.tab_admin_reports) }

        // Auditoría reciente (RNF-SEG-04)
        binding.containerAudit.removeAllViews()
        resources.getStringArray(R.array.audit_log).forEach { entry ->
            val line = ViewStockRowBinding.inflate(layoutInflater, binding.containerAudit, true)
            line.imageIcon.setImageResource(R.drawable.ic_schedule)
            line.imageIcon.imageTintList = binding.root.context.getColorStateList(R.color.ink_400)
            val parts = entry.split("|")
            line.textName.text = parts.first()
            line.textName.maxLines = 2
            line.textValue.text = parts.getOrElse(1) { "" }
        }
    }

    private fun kpi(kpi: co.patitas.tienda.databinding.LayoutKpiBinding, @DrawableRes icon: Int, value: String, label: Int) {
        kpi.imageKpiIcon.setImageResource(icon)
        kpi.textKpiValue.text = value
        kpi.textKpiLabel.setText(label)
    }

    private fun row(row: ViewSettingRowBinding, @DrawableRes icon: Int, title: String, detail: String, onClick: () -> Unit) {
        row.imageRowIcon.setImageResource(icon)
        row.textRowTitle.text = title
        row.textRowDetail.text = detail
        row.root.setOnClickListener { onClick() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
