package co.patitas.tienda.data

import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/**
 * CRUD del carrito en la subcolección "carts/{uid}/items".
 * Al estar en Firestore, el carrito se conserva al cerrar la app y entre dispositivos.
 */
object CartRepository {

    private var live: QueryLiveData<CartItem>? = null
    private var liveUid: String? = null

    private fun items(uid: String) = Fb.db.collection(Fb.CARTS).document(uid).collection(Fb.CART_ITEMS)

    /** Leer: el carrito del usuario actual en tiempo real (una sola instancia compartida). */
    fun live(): QueryLiveData<CartItem>? {
        val uid = Fb.uid ?: return null
        if (live == null || liveUid != uid) {
            live = QueryLiveData(items(uid), CartItem::from) { list -> list.sortedBy { it.addedAt } }
            liveUid = uid
        }
        return live
    }

    /** Últimos ítems conocidos (los que muestra la pantalla). */
    val current: List<CartItem> get() = live?.current.orEmpty()

    /** Lee el carrito directamente del servidor (se usa justo antes de pagar). */
    suspend fun fetch(): List<CartItem> {
        val uid = Fb.uid ?: return emptyList()
        return items(uid).get(Source.SERVER).await().documents.map(CartItem::from)
    }

    /**
     * Crear: agrega un producto. Si ya estaba con la misma presentación, suma la cantidad
     * sin superar el stock disponible.
     */
    suspend fun add(product: Product, quantity: Int, variant: String) {
        val uid = Fb.uid ?: return
        val itemId = product.id + "_" + variant.replace(Regex("[^A-Za-z0-9]"), "")
        val reference = items(uid).document(itemId)
        val existing = reference.get().await()
        val previous = if (existing.exists()) (existing.getLong("quantity") ?: 0L).toInt() else 0
        val item = CartItem(
            id = itemId, productId = product.id, name = product.name, price = product.price,
            quantity = (previous + quantity).coerceAtMost(product.stock), variant = variant,
            stock = product.stock, sellerId = product.sellerId, sellerName = product.sellerName,
            categoryId = product.categoryId, thumbnail = product.thumbnail,
            addedAt = existing.getLong("addedAt") ?: System.currentTimeMillis()
        )
        reference.set(item.toMap()).await()
    }

    /** Actualizar la cantidad de un ítem. */
    suspend fun setQuantity(item: CartItem, quantity: Int) {
        val uid = Fb.uid ?: return
        items(uid).document(item.id).update("quantity", quantity).await()
    }

    /** Eliminar un ítem. */
    suspend fun remove(item: CartItem) {
        val uid = Fb.uid ?: return
        items(uid).document(item.id).delete().await()
    }

    /** Volver a agregar un ítem eliminado (opción "Deshacer"). */
    suspend fun restore(item: CartItem) {
        val uid = Fb.uid ?: return
        items(uid).document(item.id).set(item.toMap()).await()
    }

    /** Vaciar el carrito. */
    suspend fun clear() {
        val uid = Fb.uid ?: return
        val docs = items(uid).get().await().documents
        if (docs.isEmpty()) return
        val batch = Fb.db.batch()
        docs.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    /** Se llama al cerrar sesión para no mostrar el carrito de otra cuenta. */
    fun reset() {
        live = null
        liveUid = null
    }
}
