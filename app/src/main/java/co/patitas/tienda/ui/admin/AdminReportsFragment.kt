package co.patitas.tienda.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import co.patitas.tienda.R
import co.patitas.tienda.data.Catalog
import co.patitas.tienda.data.Order
import co.patitas.tienda.data.OrderRepository
import co.patitas.tienda.data.Reports
import co.patitas.tienda.data.SalesPoint
import co.patitas.tienda.databinding.FragmentAdminReportsBinding
import co.patitas.tienda.databinding.ItemReportRowBinding
import co.patitas.tienda.databinding.LayoutKpiBinding
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.snack
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Reporte de ventas del Administrador calculado con los pedidos reales de Firestore:
 * indicadores, gráfico por periodo, ventas por categoría y tiendas con más ventas.
 */
class AdminReportsFragment : Fragment() {

    private var _binding: FragmentAdminReportsBinding? = null
    private val binding get() = _binding!!
    private var orders: List<Order> = emptyList()

    /** Infla el layout del fragmento con ViewBinding. */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** Configura la vista: adaptadores, eventos y observadores de Firestore. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.chipGroupPeriod.setOnCheckedStateChangeListener { _, _ -> bindPeriod() }
        binding.buttonExport.setOnClickListener { exportCsv() }
        OrderRepository.all().observe(viewLifecycleOwner) { list ->
            orders = list
            bindPeriod()
            bindRows(binding.containerCategories, Reports.salesByCategory(list))
            bindRows(binding.containerSellers, Reports.topSellers(list))
        }
    }

    /** Calcula los indicadores y el gráfico del periodo elegido (semana, mes o año). */
    private fun bindPeriod() {
        val period = when (binding.chipGroupPeriod.checkedChipId) {
            R.id.chipWeek -> Reports.WEEK
            R.id.chipYear -> Reports.YEAR
            else -> Reports.MONTH
        }
        val series = Reports.salesByPeriod(orders, period)
        val total = series.sumOf { it.value }
        val count = Reports.ordersInPeriod(orders, period).size
        val ticket = total / count.coerceAtLeast(1)
        val commission = (total * Catalog.COMMISSION_RATE).roundToLong()

        kpi(binding.kpiTotal, R.drawable.ic_trending_up, Format.moneyShort(total), getString(R.string.kpi_total_sales))
        kpi(binding.kpiOrders, R.drawable.ic_orders, Format.number(count), getString(R.string.kpi_orders))
        kpi(binding.kpiTicket, R.drawable.ic_cart, Format.moneyShort(ticket), getString(R.string.kpi_ticket))
        kpi(binding.kpiCommission, R.drawable.ic_discount, Format.moneyShort(commission), getString(R.string.kpi_commission))

        binding.textChartTitle.text = resources.getStringArray(R.array.report_chart_titles)[period]
        binding.chart.setData(series)
    }

    /** Exporta los pedidos en formato CSV y los comparte (correo, Drive, WhatsApp...). */
    private fun exportCsv() {
        if (orders.isEmpty()) {
            binding.root.snack(R.string.report_no_data)
            return
        }
        val csv = buildString {
            appendLine("pedido,fecha,comprador,tienda,estado,medio_pago,referencia_epayco,subtotal,envio,descuento,total")
            orders.forEach { o ->
                appendLine(listOf(o.code, o.date, o.buyerName, o.sellerName, o.status.style.label, o.paymentMethod,
                    o.paymentRef, o.subtotal, o.shipping, o.discount, o.total).joinToString(",") { "\"$it\"" })
            }
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.report_export_subject))
            .putExtra(android.content.Intent.EXTRA_TEXT, csv)
        startActivity(android.content.Intent.createChooser(intent, getString(R.string.action_export_csv)))
    }

    /** Configura una tarjeta de indicador (ícono, valor y etiqueta). */
    private fun kpi(kpi: LayoutKpiBinding, @DrawableRes icon: Int, value: String, label: String) {
        kpi.imageKpiIcon.setImageResource(icon)
        kpi.textKpiValue.text = value
        kpi.textKpiLabel.text = label
    }

    /** Filas con barra proporcional al mayor valor de la lista. */
    private fun bindRows(container: LinearLayout, data: List<SalesPoint>) {
        container.removeAllViews()
        if (data.isEmpty()) {
            val row = ItemReportRowBinding.inflate(layoutInflater, container, true)
            row.textLabel.setText(R.string.report_no_data)
            row.textValue.text = ""
            row.progress.progress = 0
            return
        }
        val max = data.maxOf { it.value }.coerceAtLeast(1)
        data.forEach { point ->
            val row = ItemReportRowBinding.inflate(layoutInflater, container, true)
            row.textLabel.text = point.label
            row.textValue.text = Format.moneyShort(point.value)
            row.progress.progress = (point.value * 100.0 / max).roundToInt()
        }
    }

    /** Libera el binding cuando se destruye la vista para evitar fugas de memoria. */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
