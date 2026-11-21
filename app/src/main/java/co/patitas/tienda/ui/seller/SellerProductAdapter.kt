package co.patitas.tienda.ui.seller

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.patitas.tienda.R
import co.patitas.tienda.data.Catalog
import co.patitas.tienda.data.Product
import co.patitas.tienda.databinding.ItemSellerProductBinding
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.applyStatus
import co.patitas.tienda.util.showProduct

/** Adaptador del CRUD de productos del Vendedor. El botón de menú abre las acciones. */
class SellerProductAdapter(
    private val onClick: (Product) -> Unit,
    private val onMore: (Product, View) -> Unit
) : RecyclerView.Adapter<SellerProductAdapter.Holder>() {

    private var items: List<Product> = emptyList()

    /** Reemplaza los elementos de la lista y la vuelve a pintar. */
    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Product>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemSellerProductBinding) : RecyclerView.ViewHolder(binding.root)

    /** Crea la vista de una fila a partir de su layout XML. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemSellerProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /** Cantidad de filas que muestra la lista. */
    override fun getItemCount() = items.size

    /** Pinta los datos del elemento en la fila (las filas se reutilizan al desplazarse). */
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val product = items[position]
        val b = holder.binding
        val context = b.root.context
        b.imageProduct.showProduct(b.frameThumb, product.thumbnail, product.categoryId)
        b.textName.text = product.name
        b.textMeta.text = context.getString(
            R.string.seller_product_meta,
            Catalog.category(product.categoryId).name, product.species.label, product.presentation
        )
        b.textPrice.text = Format.money(product.price)
        b.textStock.text = context.resources.getQuantityString(R.plurals.stock_units, product.stock, product.stock)
        b.textStatus.applyStatus(product.status.style)
        b.card.setOnClickListener { onClick(product) }
        b.buttonMore.setOnClickListener { onMore(product, it) }
    }
}
