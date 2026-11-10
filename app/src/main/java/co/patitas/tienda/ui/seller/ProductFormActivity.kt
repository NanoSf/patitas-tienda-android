package co.patitas.tienda.ui.seller

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import co.patitas.tienda.R
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.data.Product
import co.patitas.tienda.data.ProductStatus
import co.patitas.tienda.data.Species
import co.patitas.tienda.databinding.ActivityProductFormBinding
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.validate
import co.patitas.tienda.util.value
import co.patitas.tienda.util.visibleIf

/**
 * Crear / editar producto (V-03, RF-PRD-01 a 05). Si recibe EXTRA_PRODUCT_ID es edición.
 * Al publicar, el producto queda "En revisión" hasta que el administrador lo apruebe.
 */
class ProductFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductFormBinding
    private var editing: Product? = null
    private var photos = 0
    private var categoryId = PatitasRepository.categories.first().id

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        editing = PatitasRepository.product(intent.getIntExtra(EXTRA_PRODUCT_ID, -1))
        binding.toolbar.setTitle(if (editing == null) R.string.form_title_new else R.string.form_title_edit)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Menú desplegable de categorías (ExposedDropdownMenu)
        val categories = PatitasRepository.categories
        binding.dropdownCategory.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, categories.map { it.name })
        )
        binding.dropdownCategory.setOnItemClickListener { _, _, position, _ ->
            categoryId = categories[position].id
            onCategoryChanged()
        }

        binding.buttonTakePhoto.setOnClickListener {
            // En la integración se abre CameraX con guía de encuadre (V-04).
            photos = (photos + 1).coerceAtMost(MAX_PHOTOS)
            updatePhotos()
            binding.root.snack(R.string.photo_taken)
        }
        binding.buttonDraft.setOnClickListener { save(ProductStatus.DRAFT) }
        binding.buttonPublish.setOnClickListener { save(ProductStatus.IN_REVIEW) }

        editing?.let { fill(it) } ?: run {
            binding.dropdownCategory.setText(categories.first().name, false)
            onCategoryChanged()
        }
        updatePhotos()
    }

    private fun fill(product: Product) {
        categoryId = product.categoryId
        photos = 3
        binding.inputName.editText?.setText(product.name)
        binding.dropdownCategory.setText(PatitasRepository.category(product.categoryId).name, false)
        binding.toggleSpecies.check(
            when (product.species) {
                Species.DOG -> R.id.buttonDog
                Species.CAT -> R.id.buttonCat
                Species.OTHER -> R.id.buttonOther
            }
        )
        binding.inputBrand.editText?.setText(product.brand)
        binding.inputPresentation.editText?.setText(product.presentation)
        binding.inputPrice.editText?.setText(product.price.toString())
        binding.inputStock.editText?.setText(product.stock.toString())
        binding.inputIca.editText?.setText(product.icaRegister)
        binding.inputDescription.editText?.setText(product.description)
        binding.framePreview.setBackgroundResource(product.tile)
        binding.imagePreview.setImageResource(product.icon)
        onCategoryChanged()
    }

    private fun onCategoryChanged() {
        val category = PatitasRepository.category(categoryId)
        binding.inputIca.visibleIf(category.requiresIca)
        if (editing == null) binding.imagePreview.setImageResource(category.icon)
    }

    private fun updatePhotos() {
        binding.textPhotoCount.text = getString(R.string.form_photo_count, photos, MAX_PHOTOS)
    }

    private fun save(status: ProductStatus) {
        val required = getString(R.string.error_required)
        val price = binding.inputPrice.value.toLongOrNull() ?: 0
        val stock = binding.inputStock.value.toIntOrNull() ?: -1
        val category = PatitasRepository.category(categoryId)

        var valid = binding.inputName.validate(binding.inputName.value.length >= 5, getString(R.string.error_product_name))
        valid = binding.inputBrand.validate(binding.inputBrand.value.isNotEmpty(), required) && valid
        valid = binding.inputPresentation.validate(binding.inputPresentation.value.isNotEmpty(), required) && valid
        valid = binding.inputPrice.validate(price > 0, getString(R.string.error_price)) && valid
        valid = binding.inputStock.validate(stock >= 0, getString(R.string.error_stock)) && valid
        if (category.requiresIca && status == ProductStatus.IN_REVIEW) {
            valid = binding.inputIca.validate(binding.inputIca.value.length >= 4, getString(R.string.error_ica)) && valid
        }
        if (status == ProductStatus.IN_REVIEW && photos == 0) {
            binding.root.snack(R.string.error_photos)
            valid = false
        }
        if (!valid) return

        val seller = PatitasRepository.currentUser
        val species = when (binding.toggleSpecies.checkedButtonId) {
            R.id.buttonCat -> Species.CAT
            R.id.buttonOther -> Species.OTHER
            else -> Species.DOG
        }
        val base = editing
        val product = Product(
            id = base?.id ?: -1,
            name = binding.inputName.value,
            categoryId = categoryId,
            species = species,
            brand = binding.inputBrand.value,
            presentation = binding.inputPresentation.value,
            price = price,
            stock = stock,
            sellerId = base?.sellerId ?: seller?.id ?: -1,
            sellerName = base?.sellerName ?: seller?.let { it.storeName.ifBlank { it.name } } ?: "",
            description = binding.inputDescription.value,
            status = status,
            rating = base?.rating ?: 0.0,
            reviews = base?.reviews ?: 0,
            distanceKm = base?.distanceKm ?: 1.2,
            tile = base?.tile ?: R.drawable.bg_tile_brown,
            icon = base?.icon ?: category.icon,
            icaRegister = binding.inputIca.value
        )
        PatitasRepository.saveProduct(product)
        setResult(RESULT_OK)
        finish()
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "extra_product_id"
        private const val MAX_PHOTOS = 6
    }
}
