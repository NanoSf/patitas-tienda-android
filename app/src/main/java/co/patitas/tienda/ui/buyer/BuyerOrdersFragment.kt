package co.patitas.tienda.ui.buyer

import android.content.Intent
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
import co.patitas.tienda.ui.auth.LoginActivity
import co.patitas.tienda.ui.common.OrderAdapter
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.visibleIf
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Pedidos del Comprador (C-07): seguimiento por estado, retracto y cancelación (RF-PED-02 y 05). */
class BuyerOrdersFragment : Fragment() {

    private var _binding: FragmentOrdersBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: OrderAdapter
    private var filter = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

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

        if (PatitasRepository.currentUser == null) {
            binding.empty.imageEmpty.setImageResource(R.drawable.ic_lock)
            binding.empty.textEmptyTitle.setText(R.string.guest_login_title)
            binding.empty.textEmptyMessage.setText(R.string.orders_guest_message)
            binding.empty.buttonEmptyAction.setText(R.string.action_login)
            binding.empty.buttonEmptyAction.visibleIf(true)
            binding.empty.buttonEmptyAction.setOnClickListener {
                startActivity(Intent(requireContext(), LoginActivity::class.java))
            }
        } else {
            binding.empty.imageEmpty.setImageResource(R.drawable.ic_orders)
            binding.empty.textEmptyTitle.setText(R.string.orders_empty_title)
            binding.empty.textEmptyMessage.setText(R.string.orders_empty_message)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val user = PatitasRepository.currentUser
        val all = if (user == null) emptyList() else PatitasRepository.ordersOfBuyer(user.name)
        val list = when (filter) {
            1 -> all.filter { it.status in listOf(OrderStatus.NEW, OrderStatus.PREPARING, OrderStatus.SHIPPED) }
            2 -> all.filter { it.status == OrderStatus.DELIVERED }
            3 -> all.filter { it.status == OrderStatus.CANCELLED }
            else -> all
        }
        adapter.submit(list)
        binding.textCount.text = resources.getQuantityString(R.plurals.orders_count, list.size, list.size)
        binding.empty.root.visibleIf(list.isEmpty())
    }

    /** Línea de tiempo del pedido con las acciones permitidas según su estado. */
    private fun showTracking(order: Order) {
        val steps = listOf(OrderStatus.NEW, OrderStatus.PREPARING, OrderStatus.SHIPPED, OrderStatus.DELIVERED)
        val current = steps.indexOf(order.status)
        val timeline = if (order.status == OrderStatus.CANCELLED) {
            getString(R.string.tracking_cancelled)
        } else {
            steps.mapIndexed { index, step ->
                (if (index <= current) "● " else "○ ") + step.style.label
            }.joinToString("\n")
        }
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.order_number, order.id))
            .setMessage(getString(R.string.tracking_message, order.sellerName, timeline))
            .setPositiveButton(R.string.action_close, null)
        when (order.status) {
            OrderStatus.DELIVERED -> builder.setNeutralButton(R.string.action_withdrawal) { _, _ ->
                binding.root.snack(R.string.withdrawal_sent)
            }
            OrderStatus.NEW -> builder.setNeutralButton(R.string.action_cancel_order) { _, _ ->
                order.status = OrderStatus.CANCELLED
                refresh()
                binding.root.snack(R.string.order_cancelled)
            }
            else -> Unit
        }
        builder.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
