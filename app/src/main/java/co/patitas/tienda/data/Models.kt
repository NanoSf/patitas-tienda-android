package co.patitas.tienda.data

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import co.patitas.tienda.R

/**
 * Modelos de dominio de Patitas. Reflejan las colecciones del modelo de datos
 * del documento técnico (users, stores, products, categories, carts, orders).
 * Los montos se manejan como enteros en pesos colombianos (Long) para evitar redondeos.
 */

enum class Role(val label: String) {
    BUYER("Comprador"),
    SELLER("Vendedor"),
    ADMIN("Administrador")
}

/** Estado visual: texto, color del texto y color de fondo del badge. */
data class StatusStyle(val label: String, @ColorRes val fg: Int, @ColorRes val bg: Int)

enum class UserStatus(val style: StatusStyle) {
    ACTIVE(StatusStyle("Activo", R.color.state_success, R.color.state_success_bg)),
    PENDING(StatusStyle("Por verificar", R.color.state_warning, R.color.state_warning_bg)),
    SUSPENDED(StatusStyle("Suspendido", R.color.state_error, R.color.state_error_bg))
}

data class User(
    val id: Int,
    var name: String,
    var email: String,
    var phone: String,
    var role: Role,
    var status: UserStatus = UserStatus.ACTIVE,
    var document: String = "",
    var storeName: String = ""
) {
    val initials: String
        get() = name.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }

    /** Correo enmascarado para las vistas del administrador (Ley 1581 de 2012). */
    val maskedEmail: String
        get() {
            val parts = email.split("@")
            if (parts.size != 2 || parts[0].isEmpty()) return email
            return parts[0].first() + "•••@" + parts[1]
        }
}

data class Category(
    val id: Int,
    val name: String,
    @DrawableRes val icon: Int,
    val requiresIca: Boolean = false
)

enum class Species(val label: String) {
    DOG("Perro"),
    CAT("Gato"),
    OTHER("Otros")
}

enum class ProductStatus(val style: StatusStyle) {
    PUBLISHED(StatusStyle("Publicado", R.color.state_success, R.color.state_success_bg)),
    IN_REVIEW(StatusStyle("En revisión", R.color.state_warning, R.color.state_warning_bg)),
    PAUSED(StatusStyle("Pausado", R.color.state_info, R.color.state_info_bg)),
    DRAFT(StatusStyle("Borrador", R.color.ink_600, R.color.border_sand)),
    OUT_OF_STOCK(StatusStyle("Agotado", R.color.ink_600, R.color.surface_soft)),
    REJECTED(StatusStyle("Rechazado", R.color.state_error, R.color.state_error_bg))
}

data class Product(
    val id: Int,
    var name: String,
    var categoryId: Int,
    var species: Species,
    var brand: String,
    var presentation: String,
    var price: Long,
    var stock: Int,
    val sellerId: Int,
    val sellerName: String,
    var description: String,
    var status: ProductStatus,
    val rating: Double,
    val reviews: Int,
    val distanceKm: Double,
    @DrawableRes val tile: Int,
    @DrawableRes val icon: Int,
    var icaRegister: String = "",
    var express: Boolean = true
)

data class CartItem(val product: Product, var quantity: Int, val variant: String)

enum class OrderStatus(val style: StatusStyle) {
    NEW(StatusStyle("Nuevo", R.color.state_info, R.color.state_info_bg)),
    PREPARING(StatusStyle("Preparando", R.color.state_warning, R.color.state_warning_bg)),
    SHIPPED(StatusStyle("Enviado", R.color.seller_primary, R.color.seller_container)),
    DELIVERED(StatusStyle("Entregado", R.color.state_success, R.color.state_success_bg)),
    CANCELLED(StatusStyle("Cancelado", R.color.state_error, R.color.state_error_bg))
}

data class Order(
    val id: String,
    val buyerName: String,
    val sellerId: Int,
    val sellerName: String,
    val summary: String,
    val itemsCount: Int,
    val total: Long,
    var status: OrderStatus,
    val date: String,
    val paymentMethod: String
)

enum class ShippingType(val label: String, val detail: String, val price: Long) {
    STANDARD("Estándar", "24 a 48 horas", 9_000),
    EXPRESS("Express", "En 3 h · tiendas a menos de 5 km", 14_000)
}

/** Punto de una serie del reporte de ventas. */
data class SalesPoint(val label: String, val value: Long)
