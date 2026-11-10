package co.patitas.tienda.ui.seller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import co.patitas.tienda.R
import co.patitas.tienda.data.Order
import co.patitas.tienda.data.OrderStatus
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.databinding.FragmentOrdersBinding
import co.patitas.tienda.ui.common.OrderAdapter
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.visibleIf
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.roundToLong

/**
 * Historial de pedidos recibidos (V-05 / V-06, RF-PED-03). Aceptar o rechazar pedidos nuevos
 * y avanzar el estado: Preparando → Enviado → Entregado.
 */
class SellerOrdersFragment : Fragment() {

    private var _binding: FragmentOrdersBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: OrderAdapter
    private var filter: OrderStatus? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = OrderAdapter(showBuyer = true) { order -> showOrder(order) }
        binding.recyclerOrders.adapter = adapter

        listOf(null, OrderStatus.NEW, OrderStatus.PREPARING, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.CANCELLED)
            .forEach { status ->
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
        binding.empty.imageEmpty.setImageResource(R.drawable.ic_orders)
        binding.empty.textEmptyTitle.setText(R.string.orders_empty_title)
        binding.empty.textEmptyMessage.setText(R.string.seller_orders_empty_message)
        refresh()
    }

    private fun refresh() {
        val sellerId = PatitasRepository.currentUser?.id ?: -1
        val list = PatitasRepository.ordersOfSeller(sellerId, filter)
        adapter.submit(list)
        binding.textCount.text = resources.getQuantityString(R.plurals.orders_count, list.size, list.size)
        binding.empty.root.visibleIf(list.isEmpty())
    }

    private fun showOrder(order: Order) {
        val commission = (order.total * PatitasRepository.COMMISSION_RATE).roundToLong()
        val detail = getString(
            R.string.seller_order_detail, order.buyerName, order.summary, order.paymentMethod,
            Format.money(order.total), Format.money(commission), Format.money(order.total - commission)
        )
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.order_number, order.id))
            .setMessage(detail)
        when (order.status) {
            OrderStatus.NEW -> builder
                .setPositiveButton(R.string.action_accept) { _, _ -> updateStatus(order, OrderStatus.PREPARING) }
                .setNegativeButton(R.string.action_reject) { _, _ -> updateStatus(order, OrderStatus.CANCELLED) }
                .setNeutralButton(R.string.action_close, null)
            OrderStatus.PREPARING -> builder
                .setPositiveButton(R.string.action_mark_shipped) { _, _ -> updateStatus(order, OrderStatus.SHIPPED) }
                .setNegativeButton(R.string.action_close, null)
            OrderStatus.SHIPPED -> builder
                .setPositiveButton(R.string.action_mark_delivered) { _, _ -> updateStatus(order, OrderStatus.DELIVERED) }
                .setNegativeButton(R.string.action_close, null)
            else -> builder.setPositiveButton(R.string.action_close, null)
        }
        builder.show()
    }

    private fun updateStatus(order: Order, status: OrderStatus) {
        order.status = status
        refresh()
        binding.root.snack(getString(R.string.order_status_updated, order.id, status.style.label))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
