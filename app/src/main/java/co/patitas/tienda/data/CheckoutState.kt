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

    fun count(items: List<CartItem>) = items.sumOf { it.quantity }

    fun subtotal(items: List<CartItem>) = items.sumOf { it.lineTotal }

    /** Cantidad de tiendas distintas: el envío se cobra por cada una. */
    fun stores(items: List<CartItem>) = items.map { it.sellerId }.distinct().size

    fun shippingCost(items: List<CartItem>) = if (items.isEmpty()) 0L else shipping.price * stores(items)

    fun total(items: List<CartItem>) = (subtotal(items) + shippingCost(items) - discount).coerceAtLeast(0)

    fun reset() {
        shipping = ShippingType.STANDARD
        discount = 0
        address = null
    }
}
