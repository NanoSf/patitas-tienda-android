package co.patitas.tienda.data

/**
 * Datos temporales de la compra en curso: tipo de envío, cupón y dirección de entrega.
 * Los totales se calculan siempre a partir del carrito guardado en Firestore.
 */
object CheckoutState {

    const val COUPON_CODE = "PELUDO20"
    private const val COUPON_VALUE = 5_000L

    var shipping: ShippingType = ShippingType.STANDARD
    var discount: Long = 0
        private set
    var address: DeliveryAddress? = null

    /** Aplica el cupón si es válido y devuelve si quedó aplicado. */
    fun applyCoupon(code: String): Boolean {
        discount = if (code.trim().equals(COUPON_CODE, ignoreCase = true)) COUPON_VALUE else 0
        return discount > 0
    }

    /** Cantidad total de unidades en el carrito. */
    fun count(items: List<CartItem>) = items.sumOf { it.quantity }

    /** Suma de precio por cantidad de todos los ítems. */
    fun subtotal(items: List<CartItem>) = items.sumOf { it.lineTotal }

    /** Cantidad de tiendas distintas: el envío se cobra por cada una. */
    fun stores(items: List<CartItem>) = items.map { it.sellerId }.distinct().size

    /** Costo de envío: el tipo elegido multiplicado por el número de tiendas. */
    fun shippingCost(items: List<CartItem>) = if (items.isEmpty()) 0L else shipping.price * stores(items)

    /** Total a pagar: subtotal + envío - descuento (nunca negativo). */
    fun total(items: List<CartItem>) = (subtotal(items) + shippingCost(items) - discount).coerceAtLeast(0)

    /** Limpia la compra en curso (al pagar o al cerrar sesión). */
    fun reset() {
        shipping = ShippingType.STANDARD
        discount = 0
        address = null
    }
}
