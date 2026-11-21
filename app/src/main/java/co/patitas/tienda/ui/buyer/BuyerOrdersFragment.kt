package co.patitas.tienda.ui.buyer

import android.content.Intent
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
import co.patitas.tienda.ui.auth.LoginActivity
import co.patitas.tienda.ui.common.OrderAdapter
import co.patitas.tienda.ui.common.OrderDetails
import co.patitas.tienda.util.errorMessage
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.visibleIf
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Historial de pedidos del Comprador (C-07), leído de Firestore en tiempo real:
 * seguimiento por estado, retracto y cancelación (RF-PED-02 y 05).
 */
class BuyerOrdersFragment : Fragment() {

    private var _binding: FragmentOrdersBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: OrderAdapter
    private var filter = 0
    private var orders: List<Order> = emptyList()

    /** Infla el layout del fragmento con ViewBinding. */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** Configura la vista: adaptadores, eventos y observadores de Firestore. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = OrderAdapter(showBuyer = false) { order -> showTracking(order) }
        binding.recyclerOrders.adapter = adapter

        resources.getStringArray(R.array.buyer_order_filters).forEachIndexed { index, label ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = label
                isCheckable = true
                isChecked = index == 0
                setOnClickListener {
                    filter = index
                    refresh()
                }
            }
            binding.chipGroupStatus.addView(chip)
        }

        val user = Session.user
        if (user == null) {
            binding.empty.imageEmpty.setImageResource(R.drawable.ic_lock)
            binding.empty.textEmptyTitle.setText(R.string.guest_login_title)
            binding.empty.textEmptyMessage.setText(R.string.orders_guest_message)
            binding.empty.buttonEmptyAction.setText(R.string.action_login)
            binding.empty.buttonEmptyAction.visibleIf(true)
            binding.empty.buttonEmptyAction.setOnClickListener {
                startActivity(Intent(requireContext(), LoginActivity::class.java))
            }
            refresh()
        } else {
            binding.empty.imageEmpty.setImageResource(R.drawable.ic_orders)
            binding.empty.textEmptyTitle.setText(R.string.orders_empty_title)
            binding.empty.textEmptyMessage.setText(R.string.orders_empty_message)
            OrderRepository.ofBuyer(user.id).observe(viewLifecycleOwner) { list ->
                orders = list
                refresh()
            }
        }
    }

    /** Aplica el filtro seleccionado a la lista y actualiza el contador y el estado vacío. */
    private fun refresh() {
        val list = when (filter) {
            1 -> orders.filter { it.status in listOf(OrderStatus.NEW, OrderStatus.PREPARING, OrderStatus.SHIPPED) }
            2 -> orders.filter { it.status == OrderStatus.DELIVERED }
            3 -> orders.filter { it.status == OrderStatus.CANCELLED }
            else -> orders
        }
        adapter.submit(list)
        binding.textCount.text = resources.getQuantityString(R.plurals.orders_count, list.size, list.size)
        binding.empty.root.visibleIf(list.isEmpty())
    }

    /** Detalle con la línea de tiempo del historial y las acciones permitidas según el estado. */
    private fun showTracking(order: Order) {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.order_number, order.code))
            .setMessage(OrderDetails.text(requireContext(), order, forSeller = false))
            .setPositiveButton(R.string.action_close, null)
        when (order.status) {
            OrderStatus.DELIVERED -> builder.setNeutralButton(R.string.action_withdrawal) { _, _ ->
                binding.root.snack(R.string.withdrawal_sent)
            }
            OrderStatus.NEW -> builder.setNeutralButton(R.string.action_cancel_order) { _, _ -> cancel(order) }
            else -> Unit
        }
        if (order.hasLocation) {
            builder.setNegativeButton(R.string.action_view_map) { _, _ -> OrderDetails.openMap(requireContext(), order) }
        }
        builder.show()
    }

    /** Cancela el pedido (solo permitido mientras está en estado Nuevo). */
    private fun cancel(order: Order) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                OrderRepository.updateStatus(order, OrderStatus.CANCELLED, Session.user?.name.orEmpty())
                binding.root.snack(R.string.order_cancelled)
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
