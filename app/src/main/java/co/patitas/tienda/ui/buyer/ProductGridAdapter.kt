package co.patitas.tienda.ui.buyer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.patitas.tienda.R
import co.patitas.tienda.data.Product
import co.patitas.tienda.databinding.ItemProductGridBinding
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.visibleIf

/** Adaptador de la grilla de productos del catálogo y del inicio. */
class ProductGridAdapter(
    private val onClick: (Product) -> Unit,
    private val onAdd: (Product) -> Unit
) : RecyclerView.Adapter<ProductGridAdapter.Holder>() {

    private var items: List<Product> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Product>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemProductGridBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemProductGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val product = items[position]
        val b = holder.binding
        b.frameTile.setBackgroundResource(product.tile)
        b.imageProduct.setImageResource(product.icon)
        b.textName.text = product.name
        b.textMeta.text = b.root.context.getString(
            R.string.product_meta, product.sellerName, Format.rating(product.rating)
        )
        b.textPrice.text = Format.money(product.price)
        b.textExpress.visibleIf(product.express && product.distanceKm <= 5)
        b.card.setOnClickListener { onClick(product) }
        b.buttonAdd.setOnClickListener { onAdd(product) }
    }
}
