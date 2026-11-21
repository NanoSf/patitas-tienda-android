package co.patitas.tienda.ui.buyer

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import co.patitas.tienda.R
import co.patitas.tienda.data.Catalog
import co.patitas.tienda.data.Product
import co.patitas.tienda.data.ProductRepository
import co.patitas.tienda.databinding.ActivityProductDetailBinding
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.errorMessage
import co.patitas.tienda.util.showProduct
import co.patitas.tienda.util.visibleIf
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Detalle de producto (C-02): lee el producto y su foto completa de Firestore; presentaciones,
 * cantidad, vendedor y agregar al carrito.
 */
class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private lateinit var product: Product
    private var quantity = 1
    private var favorite = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonAdd.isEnabled = false
        lifecycleScope.launch {
            try {
                val id = intent.getStringExtra(EXTRA_PRODUCT_ID).orEmpty()
                val found = ProductRepository.get(id)
                if (found == null) {
                    finish()
                    return@launch
                }
                product = found
                bind()
                // La foto completa está en otro documento; mientras llega se ve la miniatura.
                ProductRepository.fullImage(id)?.let { full ->
                    binding.imageProduct.showProduct(binding.frameHero, full, found.categoryId)
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, errorMessage(e), Snackbar.LENGTH_LONG).show()
            }
        }
        binding.buttonFavorite.setOnClickListener {
            favorite = !favorite
            val color = if (favorite) R.color.state_error else R.color.ink_400
            binding.buttonFavorite.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, color))
        }
        binding.stepper.buttonMinus.setOnClickListener { changeQuantity(-1) }
        binding.stepper.buttonPlus.setOnClickListener { changeQuantity(+1) }
        binding.buttonAdd.setOnClickListener {
            CartActions.add(this, binding.root, product, quantity, selectedVariant())
        }
    }

    private fun bind() {
        binding.imageProduct.showProduct(binding.frameHero, product.thumbnail, product.categoryId)
        binding.textName.text = product.name
        binding.textSellerLocation.text = getString(
            R.string.detail_seller_location, product.sellerName, Catalog.category(product.categoryId).name
        )
        binding.textRating.text = if (product.reviews > 0) {
            getString(R.string.detail_rating, Format.rating(product.rating), product.reviews)
        } else {
            getString(R.string.detail_no_reviews)
        }
        binding.textPrice.text = Format.money(product.price)
        binding.textDescription.text = product.description
        binding.textSellerName.text = product.sellerName
        binding.textSellerInitials.text = product.sellerName.split(" ").take(2).joinToString("") { it.take(1) }

        // Distintivos con colores propios (no los del tema por defecto)
        styleBadge(binding.textBadgeExpress, R.color.buyer_primary, R.color.buyer_container)
        binding.textBadgeExpress.visibleIf(product.express)
        val category = Catalog.category(product.categoryId)
        if (category.requiresIca && product.icaRegister.isNotBlank()) {
            binding.textBadgeIca.text = getString(R.string.detail_ica, product.icaRegister)
            styleBadge(binding.textBadgeIca, R.color.state_info, R.color.state_info_bg)
        } else {
            binding.textBadgeIca.text = category.name
            styleBadge(binding.textBadgeIca, R.color.seller_primary, R.color.seller_container)
        }

        variantsFor(product).forEach { label ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = label
                isCheckable = true
                isChecked = label == product.presentation
            }
            binding.chipGroupVariants.addView(chip)
        }
        updateQuantity()
    }

    private fun styleBadge(view: TextView, fg: Int, bg: Int) {
        view.setTextColor(ContextCompat.getColor(this, fg))
        view.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, bg))
    }

    private fun variantsFor(product: Product): List<String> = when (product.categoryId) {
        1 -> if (product.presentation.contains("kg")) listOf("3 kg", "8 kg", "15 kg") else listOf(product.presentation)
        5 -> listOf("Talla S", "Talla M", "Talla L")
        else -> listOf(product.presentation)
    }.let { if (product.presentation in it) it else listOf(product.presentation) + it }.distinct()

    private fun selectedVariant(): String {
        val chip = binding.chipGroupVariants.findViewById<Chip>(binding.chipGroupVariants.checkedChipId)
        return chip?.text?.toString() ?: product.presentation
    }

    private fun changeQuantity(delta: Int) {
        if (!::product.isInitialized) return
        quantity = (quantity + delta).coerceIn(1, product.stock.coerceAtLeast(1))
        updateQuantity()
    }

    private fun updateQuantity() {
        binding.stepper.textQuantity.text = quantity.toString()
        binding.textStock.text = if (product.stock > 0) {
            resources.getQuantityString(R.plurals.detail_stock, product.stock, product.stock)
        } else {
            getString(R.string.product_out_of_stock)
        }
        binding.buttonAdd.isEnabled = product.stock > 0
        binding.buttonAdd.text = getString(R.string.detail_add_button, Format.money(product.price * quantity))
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "extra_product_id"
    }
}
