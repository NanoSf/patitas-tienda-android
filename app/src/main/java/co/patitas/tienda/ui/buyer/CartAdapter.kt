package co.patitas.tienda.ui.buyer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.patitas.tienda.R
import co.patitas.tienda.data.CartItem
import co.patitas.tienda.databinding.ItemCartBinding
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.snack

/** Adaptador del carrito: actualizar cantidad (U) y eliminar (D) del CRUD de carrito. */
class CartAdapter(
    private val onQuantityChanged: () -> Unit,
    private val onDelete: (CartItem) -> Unit
) : RecyclerView.Adapter<CartAdapter.Holder>() {

    private var items: List<CartItem> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<CartItem>) {
        items = list.toList()
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemCartBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val b = holder.binding
        b.frameThumb.setBackgroundResource(item.product.tile)
        b.imageProduct.setImageResource(item.product.icon)
        b.textStore.text = item.product.sellerName
        b.textName.text = item.product.name
        b.textVariant.text = item.variant
        b.textPrice.text = Format.money(item.product.price * item.quantity)
        b.stepper.textQuantity.text = item.quantity.toString()

        b.stepper.buttonMinus.setOnClickListener {
            if (item.quantity > 1) {
                item.quantity--
                notifyItemChanged(holder.bindingAdapterPosition)
                onQuantityChanged()
            }
        }
        b.stepper.buttonPlus.setOnClickListener {
            if (item.quantity < item.product.stock) {
                item.quantity++
                notifyItemChanged(holder.bindingAdapterPosition)
                onQuantityChanged()
            } else {
                b.root.snack(b.root.context.getString(R.string.cart_max_stock, item.product.stock))
            }
        }
        b.buttonDelete.setOnClickListener { onDelete(item) }
    }
}
