package co.patitas.tienda.ui.seller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import co.patitas.tienda.R
import co.patitas.tienda.data.Order
import co.patitas.tienda.data.OrderRepository
import co.patitas.tienda.data.OrderStatus
import co.patitas.tienda.data.Session
import co.patitas.tienda.databinding.FragmentOrdersBinding
import co.patitas.tienda.ui.common.OrderAdapter
import co.patitas.tienda.ui.common.OrderDetails
import co.patitas.tienda.util.errorMessage
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.visibleIf
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Historial de pedidos recibidos (V-05 / V-06, RF-PED-03), en tiempo real desde Firestore.
 * Aceptar o rechazar pedidos nuevos y avanzar el estado: Preparando → Enviado → Entregado.
 * Cada cambio queda en el historial del pedido.
 */
class SellerOrdersFragment : Fragment() {

    private var _binding: FragmentOrdersBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: OrderAdapter
    private var filter: OrderStatus? = null
    private var orders: List<Order> = emptyList()

    /** Infla el layout del fragmento con ViewBinding. */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** Configura la vista: adaptadores, eventos y observadores de Firestore. */
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

        OrderRepository.ofSeller(Session.user?.id.orEmpty()).observe(viewLifecycleOwner) { list ->
            orders = list
            refresh()
        }
    }

    /** Aplica el filtro seleccionado a la lista y actualiza el contador y el estado vacío. */
    private fun refresh() {
        val list = orders.filter { filter == null || it.status == filter }
        adapter.submit(list)
        binding.textCount.text = resources.getQuantityString(R.plurals.orders_count, list.size, list.size)
        binding.empty.root.visibleIf(list.isEmpty())
    }

    /** Detalle del pedido con las acciones permitidas según su estado. */
    private fun showOrder(order: Order) {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.order_number, order.code))
            .setMessage(OrderDetails.text(requireContext(), order, forSeller = true))
        when (order.status) {
            OrderStatus.NEW -> builder
                .setPositiveButton(R.string.action_accept) { _, _ -> updateStatus(order, OrderStatus.PREPARING) }
                .setNegativeButton(R.string.action_reject) { _, _ -> updateStatus(order, OrderStatus.CANCELLED) }
            OrderStatus.PREPARING -> builder
                .setPositiveButton(R.string.action_mark_shipped) { _, _ -> updateStatus(order, OrderStatus.SHIPPED) }
            OrderStatus.SHIPPED -> builder
                .setPositiveButton(R.string.action_mark_delivered) { _, _ -> updateStatus(order, OrderStatus.DELIVERED) }
            else -> builder.setPositiveButton(R.string.action_close, null)
        }
        if (order.hasLocation) {
            builder.setNeutralButton(R.string.action_view_map) { _, _ -> OrderDetails.openMap(requireContext(), order) }
        } else if (order.status != OrderStatus.DELIVERED && order.status != OrderStatus.CANCELLED && order.status != OrderStatus.NEW) {
            builder.setNeutralButton(R.string.action_close, null)
        }
        builder.show()
    }

    /** Avanza el estado del pedido y lo registra en el historial. */
    private fun updateStatus(order: Order, status: OrderStatus) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                OrderRepository.updateStatus(order, status, Session.user?.displayName.orEmpty())
                binding.root.snack(getString(R.string.order_status_updated, order.code, status.style.label))
            } catch (e: Exception) {
                binding.root.snack(requireContext().errorMessage(e))
            }
        }
    }

    /** Libera el binding cuando se destruye la vista para evitar fugas de memoria. */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
