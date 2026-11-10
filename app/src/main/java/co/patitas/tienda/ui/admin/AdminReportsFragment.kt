package co.patitas.tienda.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import co.patitas.tienda.R
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.data.SalesPoint
import co.patitas.tienda.databinding.FragmentAdminReportsBinding
import co.patitas.tienda.databinding.ItemReportRowBinding
import co.patitas.tienda.databinding.LayoutKpiBinding
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.snack
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Reporte de ventas del Administrador: indicadores, gráfico por periodo, categorías y vendedores. */
class AdminReportsFragment : Fragment() {

    private var _binding: FragmentAdminReportsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.chipGroupPeriod.setOnCheckedStateChangeListener { _, _ -> bindPeriod() }
        binding.buttonExport.setOnClickListener { binding.root.snack(R.string.export_done) }
        bindPeriod()
        bindRows(binding.containerCategories, PatitasRepository.salesByCategory())
        bindRows(binding.containerSellers, PatitasRepository.topSellers())
    }

    private fun bindPeriod() {
        val period = when (binding.chipGroupPeriod.checkedChipId) {
            R.id.chipWeek -> 0
            R.id.chipYear -> 2
            else -> 1
        }
        val series = PatitasRepository.salesByPeriod(period)
        val total = series.sumOf { it.value }
        val orders = PatitasRepository.ordersByPeriod(period)
        val ticket = total / orders.coerceAtLeast(1)
        val commission = (total * PatitasRepository.COMMISSION_RATE).roundToLong()

        kpi(binding.kpiTotal, R.drawable.ic_trending_up, Format.moneyShort(total), getString(R.string.kpi_total_sales))
        kpi(binding.kpiOrders, R.drawable.ic_orders, Format.number(orders), getString(R.string.kpi_orders))
        kpi(binding.kpiTicket, R.drawable.ic_cart, Format.moneyShort(ticket), getString(R.string.kpi_ticket))
        kpi(binding.kpiCommission, R.drawable.ic_discount, Format.moneyShort(commission), getString(R.string.kpi_commission))

        binding.textChartTitle.text = resources.getStringArray(R.array.report_chart_titles)[period]
        binding.chart.setData(series)
    }

    private fun kpi(kpi: LayoutKpiBinding, @DrawableRes icon: Int, value: String, label: String) {
        kpi.imageKpiIcon.setImageResource(icon)
        kpi.textKpiValue.text = value
        kpi.textKpiLabel.text = label
    }

    /** Filas con barra proporcional al mayor valor de la lista. */
    private fun bindRows(container: LinearLayout, data: List<SalesPoint>) {
        container.removeAllViews()
        val max = data.maxOfOrNull { it.value } ?: 1
        data.forEach { point ->
            val row = ItemReportRowBinding.inflate(layoutInflater, container, true)
            row.textLabel.text = point.label
            row.textValue.text = Format.moneyShort(point.value)
            row.progress.progress = (point.value * 100.0 / max).roundToInt()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
