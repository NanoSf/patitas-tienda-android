package co.patitas.tienda.ui.common

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.patitas.tienda.R
import co.patitas.tienda.data.Order
import co.patitas.tienda.databinding.ItemOrderBinding
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.applyStatus

/**
 * Adaptador de pedidos. Con showBuyer = true (vista del vendedor) muestra el comprador
 * con el nombre abreviado; con false (vista del comprador) muestra la tienda.
 */
class OrderAdapter(
    private val showBuyer: Boolean,
    private val onClick: (Order) -> Unit
) : RecyclerView.Adapter<OrderAdapter.Holder>() {

    private var items: List<Order> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Order>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemOrderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val order = items[position]
        val b = holder.binding
        val context = b.root.context
        b.textOrderId.text = context.getString(R.string.order_number, order.id)
        b.textStatus.applyStatus(order.status.style)
        b.textCounterpart.text = if (showBuyer) {
            context.getString(R.string.order_buyer, order.buyerName)
        } else {
            order.sellerName
        }
        b.textSummary.text = context.resources.getQuantityString(
            R.plurals.order_summary, order.itemsCount, order.itemsCount, order.summary
        )
        b.textDate.text = context.getString(R.string.order_date_method, order.date, order.paymentMethod)
        b.textTotal.text = Format.money(order.total)
        b.card.setOnClickListener { onClick(order) }
    }
}
