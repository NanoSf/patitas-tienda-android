package co.patitas.tienda.ui.seller

import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import co.patitas.tienda.R
import co.patitas.tienda.data.Catalog
import co.patitas.tienda.data.Product
import co.patitas.tienda.data.ProductRepository
import co.patitas.tienda.data.ProductStatus
import co.patitas.tienda.data.Session
import co.patitas.tienda.data.Species
import co.patitas.tienda.databinding.ActivityProductFormBinding
import co.patitas.tienda.util.ImageCodec
import co.patitas.tienda.util.errorMessage
import co.patitas.tienda.util.showProduct
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.validate
import co.patitas.tienda.util.value
import co.patitas.tienda.util.visibleIf
import kotlinx.coroutines.launch
import java.io.File

/**
 * Crear / editar producto (V-03, RF-PRD-01 a 05). Si recibe EXTRA_PRODUCT_ID es edición.
 *
 * La foto se toma con la cámara (ActivityResultContracts.TakePicture + FileProvider) o se
 * elige de la galería (Photo Picker, no requiere permisos). ImageCodec la reduce y comprime y
 * ProductRepository la guarda en Firestore. Al publicar, el producto queda "En revisión"
 * hasta que el administrador lo apruebe.
 */
class ProductFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductFormBinding
    private var editing: Product? = null
    private var categoryId = Catalog.categories.first().id

    /** Foto nueva (Base64) pendiente de guardar; null si no se cambió. */
    private var newImage: ImageCodec.Encoded? = null
    private var cameraUri: Uri? = null

    /** Cámara: guarda la foto en cameraUri y devuelve true si el usuario la confirmó. */
    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        if (saved && uri != null) processImage(uri)
    }

    /** Galería: selector de fotos del sistema (Photo Picker). */
    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) processImage(uri)
    }

    /** Infla el layout, conecta los eventos de la pantalla y carga sus datos. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        // Menú desplegable de categorías (ExposedDropdownMenu)
        val categories = Catalog.categories
        binding.dropdownCategory.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, categories.map { it.name })
        )
        binding.dropdownCategory.setOnItemClickListener { _, _, position, _ ->
            categoryId = categories[position].id
            onCategoryChanged()
        }

        binding.buttonTakePhoto.setOnClickListener { openCamera() }
        binding.buttonGallery.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.buttonDraft.setOnClickListener { save(ProductStatus.DRAFT) }
        binding.buttonPublish.setOnClickListener { save(ProductStatus.IN_REVIEW) }

        val productId = intent.getStringExtra(EXTRA_PRODUCT_ID)
        binding.toolbar.setTitle(if (productId == null) R.string.form_title_new else R.string.form_title_edit)
        if (productId == null) {
            binding.dropdownCategory.setText(categories.first().name, false)
            onCategoryChanged()
        } else {
            setSaving(true)
            lifecycleScope.launch {
                try {
                    ProductRepository.get(productId)?.let { fill(it) } ?: finish()
                } catch (e: Exception) {
                    binding.root.snack(errorMessage(e))
                } finally {
                    setSaving(false)
                }
            }
        }
        updatePhotoBadge()
    }

    /** Crea un archivo temporal y abre la cámara para guardar la foto en él. */
    private fun openCamera() {
        val dir = File(cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "producto_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        cameraUri = uri
        takePicture.launch(uri)
    }

    /** Reduce y comprime la foto en segundo plano y la muestra en la vista previa. */
    private fun processImage(uri: Uri) {
        binding.textPhotoCount.setText(R.string.form_photo_processing)
        lifecycleScope.launch {
            try {
                val encoded = ImageCodec.encode(this@ProductFormActivity, uri)
                newImage = encoded
                binding.imagePreview.showProduct(binding.framePreview, encoded.thumbnail, categoryId)
                binding.root.snack(R.string.photo_taken)
            } catch (e: Exception) {
                binding.root.snack(getString(R.string.photo_error, e.localizedMessage ?: ""))
            }
            updatePhotoBadge()
        }
    }

    /** Miniatura vigente: la foto nueva o la que ya tenía el producto. */
    private fun currentThumbnail(): String = newImage?.thumbnail ?: editing?.thumbnail.orEmpty()

    /** Indica en la vista previa si el producto ya tiene foto. */
    private fun updatePhotoBadge() {
        binding.textPhotoCount.setText(if (currentThumbnail().isEmpty()) R.string.form_photo_none else R.string.form_photo_ready)
    }

    /** Llena el formulario con el producto que se edita. */
    private fun fill(product: Product) {
        editing = product
        categoryId = product.categoryId
        binding.inputName.editText?.setText(product.name)
        binding.dropdownCategory.setText(Catalog.category(product.categoryId).name, false)
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
        onCategoryChanged()
        updatePhotoBadge()
    }

    /** Muestra el registro ICA solo para alimento y snacks y actualiza la vista previa. */
    private fun onCategoryChanged() {
        val category = Catalog.category(categoryId)
        binding.inputIca.visibleIf(category.requiresIca)
        binding.imagePreview.showProduct(binding.framePreview, currentThumbnail(), categoryId)
    }

    /** Bloquea los botones mientras se guarda. */
    private fun setSaving(saving: Boolean) {
        binding.buttonDraft.isEnabled = !saving
        binding.buttonPublish.isEnabled = !saving
    }

    /** Valida el formulario y guarda el producto (y su foto) en Firestore. */
    private fun save(status: ProductStatus) {
        val required = getString(R.string.error_required)
        val price = binding.inputPrice.value.toLongOrNull() ?: 0
        val stock = binding.inputStock.value.toIntOrNull() ?: -1
        val category = Catalog.category(categoryId)

        var valid = binding.inputName.validate(binding.inputName.value.length >= 5, getString(R.string.error_product_name))
        valid = binding.inputBrand.validate(binding.inputBrand.value.isNotEmpty(), required) && valid
        valid = binding.inputPresentation.validate(binding.inputPresentation.value.isNotEmpty(), required) && valid
        valid = binding.inputPrice.validate(price > 0, getString(R.string.error_price)) && valid
        valid = binding.inputStock.validate(stock >= 0, getString(R.string.error_stock)) && valid
        if (category.requiresIca && status == ProductStatus.IN_REVIEW) {
            valid = binding.inputIca.validate(binding.inputIca.value.length >= 4, getString(R.string.error_ica)) && valid
        }
        if (status == ProductStatus.IN_REVIEW && currentThumbnail().isEmpty()) {
            binding.root.snack(R.string.error_photos)
            valid = false
        }
        if (!valid) return

        val seller = Session.user ?: return
        val species = when (binding.toggleSpecies.checkedButtonId) {
            R.id.buttonCat -> Species.CAT
            R.id.buttonOther -> Species.OTHER
            else -> Species.DOG
        }
        val base = editing
        val product = Product(
            id = base?.id.orEmpty(),
            name = binding.inputName.value,
            categoryId = categoryId,
            species = species,
            brand = binding.inputBrand.value,
            presentation = binding.inputPresentation.value,
            price = price,
            stock = stock,
            sellerId = base?.sellerId ?: seller.id,
            sellerName = base?.sellerName ?: seller.displayName,
            description = binding.inputDescription.value,
            status = status,
            rating = base?.rating ?: 0.0,
            reviews = base?.reviews ?: 0,
            icaRegister = binding.inputIca.value,
            express = base?.express ?: true,
            thumbnail = currentThumbnail(),
            createdAt = base?.createdAt ?: System.currentTimeMillis()
        )
        setSaving(true)
        lifecycleScope.launch {
            try {
                ProductRepository.save(product, newImage?.full)
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                setSaving(false)
                binding.root.snack(errorMessage(e))
            }
        }
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "extra_product_id"
    }
}
