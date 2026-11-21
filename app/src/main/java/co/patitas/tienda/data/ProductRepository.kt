package co.patitas.tienda.data

import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * CRUD de productos sobre las colecciones "products" y "productImages" de Firestore.
 *
 * Las fotos se guardan como JPEG comprimido en Base64 dentro de Firestore (plan Spark de
 * Firebase, que no incluye Cloud Storage): una miniatura en el documento del producto para las
 * listas, y la foto completa en un documento aparte que solo se lee en el detalle.
 */
object ProductRepository {

    private val products get() = Fb.db.collection(Fb.PRODUCTS)
    private val images get() = Fb.db.collection(Fb.PRODUCT_IMAGES)

    private val byNewest: (List<Product>) -> List<Product> = { list -> list.sortedByDescending { it.createdAt } }

    /** Catálogo público: solo productos publicados (lo pueden leer incluso los invitados). */
    fun catalog() = QueryLiveData(products.whereEqualTo("status", ProductStatus.PUBLISHED.name), Product::from, byNewest)

    /** Productos de un vendedor, en cualquier estado. */
    fun ofSeller(sellerId: String) = QueryLiveData(products.whereEqualTo("sellerId", sellerId), Product::from, byNewest)

    /** Todos los productos (moderación del administrador). */
    fun all() = QueryLiveData(products as Query, Product::from, byNewest)

    /** Filtro del catálogo por texto, categoría y especie. */
    fun filter(list: List<Product>, query: String, categoryId: Int?, species: Species?): List<Product> =
        list.filter { categoryId == null || it.categoryId == categoryId }
            .filter { species == null || it.species == species }
            .filter {
                query.isBlank() || it.name.contains(query, true) || it.brand.contains(query, true) ||
                    Catalog.category(it.categoryId).name.contains(query, true)
            }

    suspend fun get(id: String): Product? =
        products.document(id).get().await().takeIf { it.exists() }?.let(Product::from)

    /** Foto completa del producto en Base64, o null si no tiene. */
    suspend fun fullImage(id: String): String? =
        images.document(id).get().await().getString("data")?.takeIf { it.isNotEmpty() }

    /**
     * Crear o actualizar. Si el producto no tiene id se crea un documento nuevo.
     * Producto e imagen se escriben en un mismo lote (batch): o se guardan ambos o ninguno.
     * @param fullImage foto nueva en Base64; null si no cambió.
     */
    suspend fun save(product: Product, fullImage: String?): String {
        val reference = if (product.id.isEmpty()) products.document() else products.document(product.id)
        product.updatedAt = System.currentTimeMillis()
        val batch = Fb.db.batch()
        batch.set(reference, product.toMap())
        if (fullImage != null) {
            batch.set(images.document(reference.id), mapOf("data" to fullImage, "sellerId" to product.sellerId))
        }
        batch.commit().await()
        return reference.id
    }

    /** Cambiar el estado (pausar, publicar, aprobar, rechazar con motivo). */
    suspend fun setStatus(product: Product, status: ProductStatus, reason: String = "") {
        products.document(product.id).update(
            mapOf("status" to status.name, "rejectionReason" to reason, "updatedAt" to System.currentTimeMillis())
        ).await()
    }

    /** Duplicar: copia el producto (y su foto) como uno nuevo en revisión. */
    suspend fun duplicate(product: Product): String {
        val copy = product.copy(
            id = "", name = product.name + " (copia)", status = ProductStatus.IN_REVIEW,
            createdAt = System.currentTimeMillis()
        )
        return save(copy, fullImage(product.id))
    }

    /** Eliminar el producto y su foto. */
    suspend fun delete(product: Product) {
        val batch = Fb.db.batch()
        batch.delete(products.document(product.id))
        if (product.hasPhoto) batch.delete(images.document(product.id))
        batch.commit().await()
    }

    /** Volver a crear un producto eliminado (opción "Deshacer"). */
    suspend fun restore(product: Product, fullImage: String?) {
        val batch = Fb.db.batch()
        batch.set(products.document(product.id), product.toMap())
        if (fullImage != null) {
            batch.set(images.document(product.id), mapOf("data" to fullImage, "sellerId" to product.sellerId))
        }
        batch.commit().await()
    }
}
