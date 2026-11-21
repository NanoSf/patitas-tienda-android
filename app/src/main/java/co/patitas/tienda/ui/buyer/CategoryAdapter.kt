package co.patitas.tienda.ui.buyer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.patitas.tienda.data.Category
import co.patitas.tienda.databinding.ItemCategoryBinding

/** Adaptador de la lista horizontal de categorías del inicio. */
class CategoryAdapter(
    private val items: List<Category>,
    private val onClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.Holder>() {

    class Holder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    /** Crea la vista de una fila a partir de su layout XML. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /** Cantidad de filas que muestra la lista. */
    override fun getItemCount() = items.size

    /** Pinta los datos del elemento en la fila (las filas se reutilizan al desplazarse). */
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val category = items[position]
        holder.binding.imageIcon.setImageResource(category.icon)
        holder.binding.textName.text = category.name
        holder.binding.root.setOnClickListener { onClick(category) }
    }
}
