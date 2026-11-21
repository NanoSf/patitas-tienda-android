package co.patitas.tienda.ui.buyer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.patitas.tienda.R
import co.patitas.tienda.data.CartItem
import co.patitas.tienda.databinding.ItemCartBinding
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.showProduct
import co.patitas.tienda.util.snack

/** Adaptador del carrito: actualizar cantidad (U) y eliminar (D) del CRUD de carrito. */
class CartAdapter(
    private val onQuantityChanged: (CartItem, Int) -> Unit,
    private val onDelete: (CartItem) -> Unit
) : RecyclerView.Adapter<CartAdapter.Holder>() {

    private var items: List<CartItem> = emptyList()

    /** Reemplaza los elementos de la lista y la vuelve a pintar. */
    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<CartItem>) {
        items = list.toList()
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemCartBinding) : RecyclerView.ViewHolder(binding.root)

    /** Crea la vista de una fila a partir de su layout XML. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /** Cantidad de filas que muestra la lista. */
    override fun getItemCount() = items.size

    /** Pinta los datos del elemento en la fila (las filas se reutilizan al desplazarse). */
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val b = holder.binding
        b.imageProduct.showProduct(b.frameThumb, item.thumbnail, item.categoryId)
        b.textStore.text = item.sellerName
        b.textName.text = item.name
        b.textVariant.text = item.variant
        b.textPrice.text = Format.money(item.lineTotal)
        b.stepper.textQuantity.text = item.quantity.toString()

        // El cambio se guarda en Firestore y el listener del carrito repinta la lista.
        b.stepper.buttonMinus.setOnClickListener {
            if (item.quantity > 1) onQuantityChanged(item, item.quantity - 1)
        }
        b.stepper.buttonPlus.setOnClickListener {
            if (item.quantity < item.stock) {
                onQuantityChanged(item, item.quantity + 1)
            } else {
                b.root.snack(b.root.context.getString(R.string.cart_max_stock, item.stock))
            }
        }
        b.buttonDelete.setOnClickListener { onDelete(item) }
    }
}
