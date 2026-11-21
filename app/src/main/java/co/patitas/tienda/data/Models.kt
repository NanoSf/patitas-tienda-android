package co.patitas.tienda.data

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import co.patitas.tienda.R
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modelos de dominio de Patitas y su conversión hacia y desde Cloud Firestore.
 *
 * Cada modelo tiene:
 *  - toMap(): el mapa de campos que se guarda en el documento.
 *  - from(doc): construye el objeto a partir de un DocumentSnapshot.
 * La conversión es manual (y no con toObject()) para guardar los enums como texto y
 * tolerar documentos con campos faltantes sin que la app se cierre.
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

/** Lee un enum guardado como texto; si el valor no existe devuelve el valor por defecto. */
inline fun <reified T : Enum<T>> enumOf(value: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default

/** Fecha corta en español de Colombia: "21 nov 2026". */
fun formatDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale("es", "CO")).format(Date(millis))

/** Documento de la colección "users". El id es el UID de Firebase Authentication. */
data class User(
    val id: String = "",
    var name: String = "",
    var email: String = "",
    var phone: String = "",
    var role: Role = Role.BUYER,
    var status: UserStatus = UserStatus.ACTIVE,
    var document: String = "",
    var storeName: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val initials: String
        get() = name.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

    /** Nombre que se muestra: la tienda para vendedores y el nombre para los demás. */
    val displayName: String
        get() = if (role == Role.SELLER && storeName.isNotBlank()) storeName else name

    /** Correo enmascarado para las vistas del administrador (Ley 1581 de 2012). */
    val maskedEmail: String
        get() {
            val parts = email.split("@")
            if (parts.size != 2 || parts[0].isEmpty()) return email
            return parts[0].first() + "•••@" + parts[1]
        }

    /** Convierte el objeto en el mapa de campos que se guarda en Firestore. */
    fun toMap(): Map<String, Any> = mapOf(
        "name" to name,
        "email" to email,
        "phone" to phone,
        "role" to role.name,
        "status" to status.name,
        "document" to document,
        "storeName" to storeName,
        "createdAt" to createdAt
    )

    companion object {
        /** Construye el objeto a partir del documento (o mapa) leído de Firestore. */
        fun from(doc: DocumentSnapshot) = User(
            id = doc.id,
            name = doc.getString("name").orEmpty(),
            email = doc.getString("email").orEmpty(),
            phone = doc.getString("phone").orEmpty(),
            role = enumOf(doc.getString("role"), Role.BUYER),
            status = enumOf(doc.getString("status"), UserStatus.ACTIVE),
            document = doc.getString("document").orEmpty(),
            storeName = doc.getString("storeName").orEmpty(),
            createdAt = doc.getLong("createdAt") ?: 0L
        )
    }
}

/** Categorías fijas del catálogo (no cambian, por eso viven en el código y no en Firestore). */
data class Category(
    val id: Int,
    val name: String,
    @DrawableRes val icon: Int,
    @DrawableRes val tile: Int,
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

/**
 * Documento de la colección "products".
 * La miniatura (thumbnail) se guarda en el mismo documento como JPEG en Base64 para mostrar
 * las listas rápido; la foto completa vive en "productImages/{id}" y solo se carga en el detalle.
 */
data class Product(
    val id: String = "",
    var name: String = "",
    var categoryId: Int = 1,
    var species: Species = Species.DOG,
    var brand: String = "",
    var presentation: String = "",
    var price: Long = 0,
    var stock: Int = 0,
    val sellerId: String = "",
    val sellerName: String = "",
    var description: String = "",
    var status: ProductStatus = ProductStatus.DRAFT,
    val rating: Double = 0.0,
    val reviews: Int = 0,
    var icaRegister: String = "",
    var express: Boolean = true,
    var thumbnail: String = "",
    var rejectionReason: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    val hasPhoto: Boolean get() = thumbnail.isNotEmpty()

    /** Convierte el objeto en el mapa de campos que se guarda en Firestore. */
    fun toMap(): Map<String, Any> = mapOf(
        "name" to name,
        "categoryId" to categoryId,
        "species" to species.name,
        "brand" to brand,
        "presentation" to presentation,
        "price" to price,
        "stock" to stock,
        "sellerId" to sellerId,
        "sellerName" to sellerName,
        "description" to description,
        "status" to status.name,
        "rating" to rating,
        "reviews" to reviews,
        "icaRegister" to icaRegister,
        "express" to express,
        "thumbnail" to thumbnail,
        "rejectionReason" to rejectionReason,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        /** Construye el objeto a partir del documento (o mapa) leído de Firestore. */
        fun from(doc: DocumentSnapshot) = Product(
            id = doc.id,
            name = doc.getString("name").orEmpty(),
            categoryId = (doc.getLong("categoryId") ?: 1L).toInt(),
            species = enumOf(doc.getString("species"), Species.DOG),
            brand = doc.getString("brand").orEmpty(),
            presentation = doc.getString("presentation").orEmpty(),
            price = doc.getLong("price") ?: 0L,
            stock = (doc.getLong("stock") ?: 0L).toInt(),
            sellerId = doc.getString("sellerId").orEmpty(),
            sellerName = doc.getString("sellerName").orEmpty(),
            description = doc.getString("description").orEmpty(),
            status = enumOf(doc.getString("status"), ProductStatus.DRAFT),
            rating = doc.getDouble("rating") ?: 0.0,
            reviews = (doc.getLong("reviews") ?: 0L).toInt(),
            icaRegister = doc.getString("icaRegister").orEmpty(),
            express = doc.getBoolean("express") ?: true,
            thumbnail = doc.getString("thumbnail").orEmpty(),
            rejectionReason = doc.getString("rejectionReason").orEmpty(),
            createdAt = doc.getLong("createdAt") ?: 0L,
            updatedAt = doc.getLong("updatedAt") ?: 0L
        )
    }
}

/**
 * Documento de "carts/{uid}/items". Guarda una copia del nombre, precio y miniatura del
 * producto para pintar el carrito sin consultar cada producto.
 */
data class CartItem(
    val id: String = "",
    val productId: String = "",
    val name: String = "",
    val price: Long = 0,
    var quantity: Int = 1,
    val variant: String = "",
    val stock: Int = 0,
    val sellerId: String = "",
    val sellerName: String = "",
    val categoryId: Int = 1,
    val thumbnail: String = "",
    val addedAt: Long = System.currentTimeMillis()
) {
    val lineTotal: Long get() = price * quantity

    /** Convierte el objeto en el mapa de campos que se guarda en Firestore. */
    fun toMap(): Map<String, Any> = mapOf(
        "productId" to productId,
        "name" to name,
        "price" to price,
        "quantity" to quantity,
        "variant" to variant,
        "stock" to stock,
        "sellerId" to sellerId,
        "sellerName" to sellerName,
        "categoryId" to categoryId,
        "thumbnail" to thumbnail,
        "addedAt" to addedAt
    )

    companion object {
        /** Construye el objeto a partir del documento (o mapa) leído de Firestore. */
        fun from(doc: DocumentSnapshot) = CartItem(
            id = doc.id,
            productId = doc.getString("productId").orEmpty(),
            name = doc.getString("name").orEmpty(),
            price = doc.getLong("price") ?: 0L,
            quantity = (doc.getLong("quantity") ?: 1L).toInt(),
            variant = doc.getString("variant").orEmpty(),
            stock = (doc.getLong("stock") ?: 0L).toInt(),
            sellerId = doc.getString("sellerId").orEmpty(),
            sellerName = doc.getString("sellerName").orEmpty(),
            categoryId = (doc.getLong("categoryId") ?: 1L).toInt(),
            thumbnail = doc.getString("thumbnail").orEmpty(),
            addedAt = doc.getLong("addedAt") ?: 0L
        )
    }
}

enum class OrderStatus(val style: StatusStyle) {
    NEW(StatusStyle("Nuevo", R.color.state_info, R.color.state_info_bg)),
    PREPARING(StatusStyle("Preparando", R.color.state_warning, R.color.state_warning_bg)),
    SHIPPED(StatusStyle("Enviado", R.color.seller_primary, R.color.seller_container)),
    DELIVERED(StatusStyle("Entregado", R.color.state_success, R.color.state_success_bg)),
    CANCELLED(StatusStyle("Cancelado", R.color.state_error, R.color.state_error_bg))
}

/** Línea de un pedido (se guarda como lista de mapas dentro del documento del pedido). */
data class OrderItem(
    val productId: String,
    val name: String,
    val variant: String,
    val quantity: Int,
    val price: Long,
    val categoryId: Int
) {
    /** Convierte el objeto en el mapa de campos que se guarda en Firestore. */
    fun toMap(): Map<String, Any> = mapOf(
        "productId" to productId, "name" to name, "variant" to variant,
        "quantity" to quantity, "price" to price, "categoryId" to categoryId
    )

    companion object {
        /** Construye el objeto a partir del documento (o mapa) leído de Firestore. */
        fun from(map: Map<*, *>) = OrderItem(
            productId = map["productId"] as? String ?: "",
            name = map["name"] as? String ?: "",
            variant = map["variant"] as? String ?: "",
            quantity = (map["quantity"] as? Number)?.toInt() ?: 0,
            price = (map["price"] as? Number)?.toLong() ?: 0L,
            categoryId = (map["categoryId"] as? Number)?.toInt() ?: 1
        )
    }
}

/** Entrada del historial de estados de un pedido (quién lo cambió y cuándo). */
data class OrderEvent(val status: OrderStatus, val at: Long, val by: String) {
    /** Convierte el objeto en el mapa de campos que se guarda en Firestore. */
    fun toMap(): Map<String, Any> = mapOf("status" to status.name, "at" to at, "by" to by)

    companion object {
        /** Construye el objeto a partir del documento (o mapa) leído de Firestore. */
        fun from(map: Map<*, *>) = OrderEvent(
            status = enumOf(map["status"] as? String, OrderStatus.NEW),
            at = (map["at"] as? Number)?.toLong() ?: 0L,
            by = map["by"] as? String ?: ""
        )
    }
}

/**
 * Documento de la colección "orders". Se crea un pedido por cada tienda del carrito,
 * todos con la misma referencia de pago de ePayco.
 */
data class Order(
    val id: String = "",
    val code: String = "",
    val buyerId: String = "",
    val buyerName: String = "",
    val sellerId: String = "",
    val sellerName: String = "",
    val items: List<OrderItem> = emptyList(),
    val subtotal: Long = 0,
    val shipping: Long = 0,
    val discount: Long = 0,
    val total: Long = 0,
    var status: OrderStatus = OrderStatus.NEW,
    val history: List<OrderEvent> = emptyList(),
    val shippingType: String = "",
    val address: String = "",
    val city: String = "",
    val neighborhood: String = "",
    val notes: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val paymentMethod: String = "",
    val paymentRef: String = "",
    val paymentStatus: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val itemsCount: Int get() = items.sumOf { it.quantity }
    val summary: String get() = items.joinToString(" + ") { it.name.take(28) }
    val date: String get() = formatDate(createdAt)
    val hasLocation: Boolean get() = latitude != null && longitude != null

    /** Convierte el objeto en el mapa de campos que se guarda en Firestore. */
    fun toMap(): Map<String, Any?> = mapOf(
        "code" to code,
        "buyerId" to buyerId,
        "buyerName" to buyerName,
        "sellerId" to sellerId,
        "sellerName" to sellerName,
        "items" to items.map { it.toMap() },
        "subtotal" to subtotal,
        "shipping" to shipping,
        "discount" to discount,
        "total" to total,
        "status" to status.name,
        "history" to history.map { it.toMap() },
        "shippingType" to shippingType,
        "address" to address,
        "city" to city,
        "neighborhood" to neighborhood,
        "notes" to notes,
        "latitude" to latitude,
        "longitude" to longitude,
        "paymentMethod" to paymentMethod,
        "paymentRef" to paymentRef,
        "paymentStatus" to paymentStatus,
        "createdAt" to createdAt,
        "updatedAt" to createdAt
    )

    companion object {
        /** Construye el objeto a partir del documento (o mapa) leído de Firestore. */
        fun from(doc: DocumentSnapshot) = Order(
            id = doc.id,
            code = doc.getString("code").orEmpty(),
            buyerId = doc.getString("buyerId").orEmpty(),
            buyerName = doc.getString("buyerName").orEmpty(),
            sellerId = doc.getString("sellerId").orEmpty(),
            sellerName = doc.getString("sellerName").orEmpty(),
            items = (doc.get("items") as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.let(OrderItem::from) },
            subtotal = doc.getLong("subtotal") ?: 0L,
            shipping = doc.getLong("shipping") ?: 0L,
            discount = doc.getLong("discount") ?: 0L,
            total = doc.getLong("total") ?: 0L,
            status = enumOf(doc.getString("status"), OrderStatus.NEW),
            history = (doc.get("history") as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.let(OrderEvent::from) },
            shippingType = doc.getString("shippingType").orEmpty(),
            address = doc.getString("address").orEmpty(),
            city = doc.getString("city").orEmpty(),
            neighborhood = doc.getString("neighborhood").orEmpty(),
            notes = doc.getString("notes").orEmpty(),
            latitude = doc.getDouble("latitude"),
            longitude = doc.getDouble("longitude"),
            paymentMethod = doc.getString("paymentMethod").orEmpty(),
            paymentRef = doc.getString("paymentRef").orEmpty(),
            paymentStatus = doc.getString("paymentStatus").orEmpty(),
            createdAt = doc.getLong("createdAt") ?: 0L
        )
    }
}

enum class ShippingType(val label: String, val detail: String, val price: Long) {
    STANDARD("Estándar", "24 a 48 horas", 9_000),
    EXPRESS("Express", "En 3 h · tiendas a menos de 5 km", 14_000)
}

/** Dirección de entrega capturada en el checkout (con coordenadas si se usó el GPS). */
data class DeliveryAddress(
    val address: String,
    val city: String,
    val neighborhood: String,
    val notes: String,
    val latitude: Double?,
    val longitude: Double?
)

/** Punto de una serie del reporte de ventas. */
data class SalesPoint(val label: String, val value: Long)
