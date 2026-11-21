package co.patitas.tienda.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/**
 * CRUD de pedidos en la colección "orders" de Firestore.
 * Cada cambio de estado se agrega al arreglo "history" (quién y cuándo), que es el historial
 * del pedido que ven el comprador, el vendedor y el administrador.
 */
object OrderRepository {

    private val orders get() = Fb.db.collection(Fb.ORDERS)
    private val byNewest: (List<Order>) -> List<Order> = { list -> list.sortedByDescending { it.createdAt } }

    fun ofBuyer(uid: String) = QueryLiveData(orders.whereEqualTo("buyerId", uid), Order::from, byNewest)

    fun ofSeller(uid: String) = QueryLiveData(orders.whereEqualTo("sellerId", uid), Order::from, byNewest)

    fun all() = QueryLiveData(orders as Query, Order::from, byNewest)

    /** Resultado de la pasarela que se guarda en el pedido. */
    data class Payment(val reference: String, val method: String, val status: String)

    /**
     * Crear: registra la compra después de que ePayco aprueba el pago.
     *
     * En un solo lote (batch) atómico:
     *  1. Crea un pedido por cada tienda del carrito, con el envío de esa tienda.
     *  2. Descuenta el stock de cada producto.
     *  3. Vacía el carrito.
     * El cupón se descuenta del primer pedido.
     */
    suspend fun placeOrders(
        buyer: User, cart: List<CartItem>, address: DeliveryAddress, payment: Payment
    ): List<Order> {
        val now = System.currentTimeMillis()
        val batch = Fb.db.batch()
        var discountLeft = CheckoutState.discount
        val created = mutableListOf<Order>()

        cart.groupBy { it.sellerId }.values.forEachIndexed { index, items ->
            val subtotal = items.sumOf { it.lineTotal }
            val shipping = CheckoutState.shipping.price
            val discount = discountLeft.coerceAtMost(subtotal)
            discountLeft -= discount
            val reference = orders.document()
            val order = Order(
                id = reference.id,
                code = "PT-" + (now % 1_000_000).toString().padStart(6, '0') + (index + 1),
                buyerId = buyer.id,
                buyerName = buyer.name,
                sellerId = items.first().sellerId,
                sellerName = items.first().sellerName,
                items = items.map { OrderItem(it.productId, it.name, it.variant, it.quantity, it.price, it.categoryId) },
                subtotal = subtotal,
                shipping = shipping,
                discount = discount,
                total = subtotal + shipping - discount,
                status = OrderStatus.NEW,
                history = listOf(OrderEvent(OrderStatus.NEW, now, buyer.name)),
                shippingType = CheckoutState.shipping.label,
                address = address.address,
                city = address.city,
                neighborhood = address.neighborhood,
                notes = address.notes,
                latitude = address.latitude,
                longitude = address.longitude,
                paymentMethod = payment.method,
                paymentRef = payment.reference,
                paymentStatus = payment.status,
                createdAt = now
            )
            batch.set(reference, order.toMap())
            created += order
        }

        // Descuento de inventario: una sola actualización por producto.
        cart.groupBy { it.productId }.forEach { (productId, items) ->
            batch.update(
                Fb.db.collection(Fb.PRODUCTS).document(productId),
                "stock", FieldValue.increment(-items.sumOf { it.quantity }.toLong())
            )
        }

        // Vaciar el carrito
        cart.forEach { item ->
            batch.delete(Fb.db.collection(Fb.CARTS).document(buyer.id).collection(Fb.CART_ITEMS).document(item.id))
        }

        batch.commit().await()
        CheckoutState.reset()
        return created
    }

    /** Actualizar: cambia el estado del pedido y lo agrega al historial. */
    suspend fun updateStatus(order: Order, status: OrderStatus, by: String) {
        val now = System.currentTimeMillis()
        orders.document(order.id).update(
            mapOf(
                "status" to status.name,
                "updatedAt" to now,
                "history" to FieldValue.arrayUnion(OrderEvent(status, now, by).toMap())
            )
        ).await()
    }

    /** Eliminar (solo administrador, por ejemplo pedidos de prueba). */
    suspend fun delete(order: Order) {
        orders.document(order.id).delete().await()
    }

    /** Referencia única de la factura enviada a ePayco. */
    fun newInvoice(): String = "PAT-" + System.currentTimeMillis() + "-" + Random.nextInt(100, 999)
}
