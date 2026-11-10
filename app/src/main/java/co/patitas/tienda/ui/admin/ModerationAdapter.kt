package co.patitas.tienda.ui.admin

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.patitas.tienda.R
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.data.Product
import co.patitas.tienda.data.ProductStatus
import co.patitas.tienda.databinding.ItemModerationBinding
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.applyStatus
import co.patitas.tienda.util.visibleIf

/**
 * Adaptador de moderación de productos (RF-ADP-01 y 02). Muestra las validaciones
 * automáticas como apoyo; la decisión final siempre es del administrador.
 */
class ModerationAdapter(
    private val onApprove: (Product) -> Unit,
    private val onReject: (Product) -> Unit
) : RecyclerView.Adapter<ModerationAdapter.Holder>() {

    private var items: List<Product> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Product>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemModerationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemModerationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val product = items[position]
        val b = holder.binding
        val context = b.root.context
        val category = PatitasRepository.category(product.categoryId)
        b.frameThumb.setBackgroundResource(product.tile)
        b.imageProduct.setImageResource(product.icon)
        b.textName.text = product.name
        b.textMeta.text = context.getString(R.string.moderation_meta, product.sellerName, category.name)
        b.textPrice.text = Format.money(product.price)
        b.textStatus.applyStatus(product.status.style)
        b.textChecks.text = checks(product, category.requiresIca, context)

        val inReview = product.status == ProductStatus.IN_REVIEW
        val published = product.status == ProductStatus.PUBLISHED
        b.groupActions.visibleIf(inReview || published || product.status == ProductStatus.REJECTED)
        b.buttonApprove.visibleIf(!published)
        b.buttonReject.visibleIf(product.status != ProductStatus.REJECTED)
        b.buttonApprove.setText(if (product.status == ProductStatus.REJECTED) R.string.action_republish else R.string.action_approve)
        b.buttonReject.setText(if (published) R.string.action_unpublish else R.string.action_reject)
        b.buttonApprove.setOnClickListener { onApprove(product) }
        b.buttonReject.setOnClickListener { onReject(product) }
    }

    /** Validaciones automáticas: fotos, precio de referencia, registro ICA y palabras prohibidas. */
    private fun checks(product: Product, requiresIca: Boolean, context: android.content.Context): String {
        val lines = mutableListOf<String>()
        lines += context.getString(R.string.check_photos_ok)
        lines += if (product.price in 5_000..2_000_000) context.getString(R.string.check_price_ok)
        else context.getString(R.string.check_price_warning)
        if (requiresIca) {
            lines += if (product.icaRegister.isNotBlank()) context.getString(R.string.check_ica_ok, product.icaRegister)
            else context.getString(R.string.check_ica_missing)
        }
        val forbidden = listOf("cachorro", "gatito en venta", "camada", "adopción con costo")
        lines += if (forbidden.any { product.name.contains(it, true) || product.description.contains(it, true) }) {
            context.getString(R.string.check_live_animals)
        } else {
            context.getString(R.string.check_words_ok)
        }
        return lines.joinToString("\n")
    }
}
