package co.patitas.tienda.data

import co.patitas.tienda.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Repositorio en memoria con datos de demostración.
 *
 * Esta entrega corresponde al frontend: las pantallas leen y modifican estas listas para que
 * los CRUD funcionen durante la sesión. En la siguiente fase este objeto se reemplaza por
 * repositorios conectados a Firebase (Auth, Firestore, Storage) y a la API de Wompi, sin
 * cambiar las pantallas.
 */
object PatitasRepository {

    /** Contraseña de las cuentas de demostración. */
    const val DEMO_PASSWORD = "Patitas2026*"
    const val COMMISSION_RATE = 0.08

    var currentUser: User? = null
        private set

    var selectedShipping: ShippingType = ShippingType.STANDARD
    var discount: Long = 0
        private set

    private val passwords = mutableMapOf<String, String>()
    private var nextUserId = 100
    private var nextProductId = 100
    private var nextOrderNumber = 1050

    // ------------------------------------------------------------------ Usuarios
    val users = mutableListOf(
        User(1, "Laura Gómez", "comprador@patitas.co", "300 555 0101", Role.BUYER, document = "CC 52.114.908"),
        User(2, "Carlos Ruiz", "vendedor@patitas.co", "310 555 0202", Role.SELLER,
            document = "NIT 901.245.778-1", storeName = "Mundo Animal PetShop"),
        User(3, "Einer Aponte", "admin@patitas.co", "320 555 0303", Role.ADMIN, document = "CC 1.048.851.401"),
        User(4, "Diana Torres", "diana.torres@correo.com", "301 555 0404", Role.BUYER),
        User(5, "Michis & Co.", "ventas@michisyco.co", "315 555 0505", Role.SELLER,
            UserStatus.ACTIVE, "NIT 901.332.410-5", "Michis & Co."),
        User(6, "Ropa Peluda SAS", "hola@ropapeluda.co", "312 555 0606", Role.SELLER,
            UserStatus.PENDING, "NIT 901.554.120-8", "Ropa Peluda"),
        User(7, "Andrés Pérez", "andres.perez@correo.com", "304 555 0707", Role.BUYER, UserStatus.SUSPENDED),
        User(8, "Juguetes Guau", "contacto@juguetesguau.co", "318 555 0808", Role.SELLER,
            UserStatus.ACTIVE, "NIT 901.776.031-2", "Juguetes Guau"),
        User(9, "Valentina Ríos", "vale.rios@correo.com", "305 555 0909", Role.BUYER)
    )

    init {
        users.forEach { passwords[it.email] = DEMO_PASSWORD }
    }

    fun login(email: String, password: String): User? {
        val user = users.firstOrNull { it.email.equals(email.trim(), ignoreCase = true) } ?: return null
        if (passwords[user.email] != password || user.status == UserStatus.SUSPENDED) return null
        currentUser = user
        return user
    }

    fun loginByEmail(email: String): User? {
        val user = users.firstOrNull { it.email.equals(email, ignoreCase = true) && it.status != UserStatus.SUSPENDED }
        currentUser = user
        return user
    }

    fun logout() {
        currentUser = null
        cart.clear()
        discount = 0
    }

    fun emailExists(email: String) = users.any { it.email.equals(email.trim(), ignoreCase = true) }

    fun register(name: String, email: String, phone: String, role: Role, password: String, storeName: String, document: String): User {
        val status = if (role == Role.SELLER) UserStatus.PENDING else UserStatus.ACTIVE
        val user = User(nextUserId++, name.trim(), email.trim(), phone.trim(), role, status, document.trim(), storeName.trim())
        users.add(0, user)
        passwords[user.email] = password
        currentUser = user
        return user
    }

    fun saveUser(user: User) {
        val index = users.indexOfFirst { it.id == user.id }
        if (index >= 0) users[index] = user else {
            users.add(0, user.copy(id = nextUserId++))
            passwords[user.email] = DEMO_PASSWORD
        }
    }

    fun deleteUser(user: User) {
        users.removeAll { it.id == user.id }
    }

    fun newUserId() = nextUserId

    // ---------------------------------------------------------------- Categorías
    val categories = listOf(
        Category(1, "Alimento", R.drawable.ic_food, requiresIca = true),
        Category(2, "Snacks", R.drawable.ic_bone, requiresIca = true),
        Category(3, "Rascadores", R.drawable.ic_scratcher),
        Category(4, "Camas", R.drawable.ic_bed),
        Category(5, "Ropa", R.drawable.ic_hanger),
        Category(6, "Juguetes", R.drawable.ic_ball),
        Category(7, "Higiene", R.drawable.ic_drop)
    )

    fun category(id: Int) = categories.firstOrNull { it.id == id } ?: categories.first()

    // ----------------------------------------------------------------- Productos
    val products = mutableListOf(
        Product(1, "Concentrado NutriCan perro adulto raza mediana", 1, Species.DOG, "NutriCan", "8 kg", 142_900, 18,
            2, "Mundo Animal PetShop", "Pollo y arroz con omega 3 y 6 para piel y pelaje brillante. Sin colorantes artificiales. Ideal para perros de 10 a 25 kg.",
            ProductStatus.PUBLISHED, 4.9, 214, 1.2, R.drawable.bg_tile_brown, R.drawable.ic_food, "ICA 10245-AL"),
        Product(2, "Rascador torre 3 niveles con cueva", 3, Species.CAT, "Michis & Co.", "1,20 m", 189_000, 6,
            5, "Michis & Co.", "Torre de sisal natural con tres plataformas, cueva acolchada y pompón colgante. Base antivuelco.",
            ProductStatus.PUBLISHED, 4.8, 96, 2.4, R.drawable.bg_tile_yellow, R.drawable.ic_scratcher),
        Product(3, "Buzo polar para perro", 5, Species.DOG, "Ropa Peluda", "Talla M", 45_000, 22,
            6, "Ropa Peluda", "Buzo en tela polar antipeeling con capota. Abertura para traílla y costuras reforzadas.",
            ProductStatus.PUBLISHED, 4.7, 58, 3.1, R.drawable.bg_tile_red, R.drawable.ic_hanger),
        Product(4, "Mordedor de caucho resistente", 6, Species.DOG, "Juguetes Guau", "Talla M · rojo", 24_900, 4,
            8, "Juguetes Guau", "Caucho natural no tóxico, apto para rellenar con premios. Para mordedores intensos.",
            ProductStatus.PUBLISHED, 5.0, 131, 1.8, R.drawable.bg_tile_green, R.drawable.ic_bone),
        Product(5, "Arena aglomerante sin olor", 7, Species.CAT, "Michis & Co.", "10 kg", 58_500, 30,
            5, "Michis & Co.", "Arena de bentonita de alta absorción. Forma terrones firmes y controla el olor por 15 días.",
            ProductStatus.PUBLISHED, 4.6, 77, 2.4, R.drawable.bg_tile_blue, R.drawable.ic_drop),
        Product(6, "Cama ortopédica lavable", 4, Species.DOG, "Mundo Animal", "Grande", 159_000, 9,
            2, "Mundo Animal PetShop", "Espuma viscoelástica con funda removible y base antideslizante. Ideal para perros senior.",
            ProductStatus.PUBLISHED, 4.8, 44, 1.2, R.drawable.bg_tile_blue, R.drawable.ic_bed),
        Product(7, "Snacks dentales para perro", 2, Species.DOG, "DentiPet", "Bolsa 300 g", 21_900, 3,
            2, "Mundo Animal PetShop", "Premios en forma de hueso que ayudan a reducir el sarro. Con menta y perejil.",
            ProductStatus.PUBLISHED, 4.5, 63, 1.2, R.drawable.bg_tile_green, R.drawable.ic_bone, "ICA 20871-AL"),
        Product(8, "Alimento húmedo para gato salmón", 1, Species.CAT, "FelinoVet", "Caja x 12", 64_800, 0,
            2, "Mundo Animal PetShop", "Trozos de salmón en salsa, sin granos. Caja de 12 sobres de 85 g.",
            ProductStatus.OUT_OF_STOCK, 4.7, 38, 1.2, R.drawable.bg_tile_yellow, R.drawable.ic_food, "ICA 30112-AL"),
        Product(9, "Pelota interactiva con luz", 6, Species.CAT, "Juguetes Guau", "Unidad", 18_500, 15,
            2, "Mundo Animal PetShop", "Pelota que se enciende y rueda sola al contacto. Recargable por USB.",
            ProductStatus.IN_REVIEW, 0.0, 0, 1.2, R.drawable.bg_tile_red, R.drawable.ic_ball),
        Product(10, "Collar ajustable reflectivo", 5, Species.DOG, "Mundo Animal", "Talla S-M", 29_900, 12,
            2, "Mundo Animal PetShop", "Collar en nailon con banda reflectiva y hebilla de liberación rápida.",
            ProductStatus.PAUSED, 4.4, 21, 1.2, R.drawable.bg_tile_brown, R.drawable.ic_hanger),
        Product(11, "Premios de pollo deshidratado", 2, Species.CAT, "Michis & Co.", "Bolsa 80 g", 16_900, 25,
            5, "Michis & Co.", "Tiras de pechuga de pollo deshidratada, 100 % natural.",
            ProductStatus.IN_REVIEW, 0.0, 0, 2.4, R.drawable.bg_tile_yellow, R.drawable.ic_bone),
        Product(12, "Impermeable con capota", 5, Species.DOG, "Ropa Peluda", "Talla L", 52_000, 8,
            6, "Ropa Peluda", "Impermeable liviano con capota y bandas reflectivas.",
            ProductStatus.IN_REVIEW, 0.0, 0, 3.1, R.drawable.bg_tile_blue, R.drawable.ic_hanger)
    )

    fun product(id: Int) = products.firstOrNull { it.id == id }

    /** Catálogo público: solo productos publicados, ordenados por cercanía. */
    fun catalog(query: String = "", categoryId: Int? = null, species: Species? = null): List<Product> =
        products.filter { it.status == ProductStatus.PUBLISHED }
            .filter { categoryId == null || it.categoryId == categoryId }
            .filter { species == null || it.species == species }
            .filter {
                query.isBlank() || it.name.contains(query, true) || it.brand.contains(query, true) ||
                    category(it.categoryId).name.contains(query, true)
            }
            .sortedBy { it.distanceKm }

    fun productsOfSeller(sellerId: Int, status: ProductStatus? = null) =
        products.filter { it.sellerId == sellerId && (status == null || it.status == status) }

    fun saveProduct(product: Product): Product {
        val index = products.indexOfFirst { it.id == product.id }
        return if (index >= 0) {
            products[index] = product
            product
        } else {
            val created = product.copy(id = nextProductId++)
            products.add(0, created)
            created
        }
    }

    fun duplicateProduct(product: Product): Product =
        saveProduct(product.copy(id = -1, name = product.name + " (copia)", status = ProductStatus.IN_REVIEW))

    fun deleteProduct(product: Product) {
        products.removeAll { it.id == product.id }
        cart.removeAll { it.product.id == product.id }
    }

    // ------------------------------------------------------------------- Carrito
    val cart = mutableListOf<CartItem>()

    fun addToCart(product: Product, quantity: Int, variant: String) {
        val existing = cart.firstOrNull { it.product.id == product.id && it.variant == variant }
        if (existing != null) {
            existing.quantity = (existing.quantity + quantity).coerceAtMost(product.stock)
        } else {
            cart.add(CartItem(product, quantity.coerceAtMost(product.stock), variant))
        }
    }

    fun cartCount() = cart.sumOf { it.quantity }

    fun cartSubtotal() = cart.sumOf { it.product.price * it.quantity }

    fun cartStores() = cart.map { it.product.sellerId }.distinct().size

    fun shippingCost() = if (cart.isEmpty()) 0L else selectedShipping.price * cartStores()

    fun applyCoupon(code: String): Boolean {
        discount = if (code.trim().equals("PELUDO20", ignoreCase = true)) 5_000 else 0
        return discount > 0
    }

    fun cartTotal() = (cartSubtotal() + shippingCost() - discount).coerceAtLeast(0)

    // ------------------------------------------------------------------- Pedidos
    val orders = mutableListOf(
        Order("PT-1049", "Laura G.", 2, "Mundo Animal PetShop", "Concentrado NutriCan 8 kg", 1, 151_900, OrderStatus.NEW, "09 nov 2026", "Nequi"),
        Order("PT-1048", "Diana T.", 2, "Mundo Animal PetShop", "Cama ortopédica + Snacks dentales", 3, 211_700, OrderStatus.NEW, "09 nov 2026", "Tarjeta •••• 4242"),
        Order("PT-1046", "Valentina R.", 2, "Mundo Animal PetShop", "Snacks dentales x2", 2, 52_800, OrderStatus.PREPARING, "08 nov 2026", "PSE"),
        Order("PT-1041", "Laura G.", 5, "Michis & Co.", "Rascador torre 3 niveles", 1, 198_000, OrderStatus.SHIPPED, "06 nov 2026", "Tarjeta •••• 4242"),
        Order("PT-1037", "Laura G.", 8, "Juguetes Guau", "Mordedor de caucho x2", 2, 58_800, OrderStatus.DELIVERED, "02 nov 2026", "Botón Bancolombia"),
        Order("PT-1033", "Diana T.", 2, "Mundo Animal PetShop", "Concentrado NutriCan 8 kg", 1, 151_900, OrderStatus.DELIVERED, "30 oct 2026", "Nequi"),
        Order("PT-1029", "Andrés P.", 2, "Mundo Animal PetShop", "Collar reflectivo", 1, 38_900, OrderStatus.CANCELLED, "27 oct 2026", "PSE")
    )

    fun ordersOfBuyer(name: String) = orders.filter { it.buyerName.startsWith(name.substringBefore(" ")) }

    fun ordersOfSeller(sellerId: Int, status: OrderStatus? = null) =
        orders.filter { it.sellerId == sellerId && (status == null || it.status == status) }

    /**
     * Simula la aprobación del pago: crea un pedido por cada vendedor del carrito
     * (RF-PED-01) y vacía el carrito. Devuelve la referencia de la transacción.
     */
    fun placeOrder(paymentMethod: String): String {
        val buyer = currentUser?.let { it.name.substringBefore(" ") + " " + it.name.substringAfter(" ").take(1) + "." } ?: "Invitado"
        val date = SimpleDateFormat("dd MMM yyyy", Locale("es", "CO")).format(Date())
        cart.groupBy { it.product.sellerId }.forEach { (sellerId, items) ->
            val total = items.sumOf { it.product.price * it.quantity } + selectedShipping.price
            orders.add(0, Order(
                "PT-" + nextOrderNumber++, buyer, sellerId, items.first().product.sellerName,
                items.joinToString(" + ") { it.product.name.take(28) }, items.sumOf { it.quantity },
                total, OrderStatus.NEW, date, paymentMethod
            ))
            items.forEach { item -> item.product.stock = (item.product.stock - item.quantity).coerceAtLeast(0) }
        }
        cart.clear()
        discount = 0
        return "WMP-" + Random.nextInt(100_000, 999_999)
    }

    // ------------------------------------------------------------------ Reportes
    fun salesByPeriod(period: Int): List<SalesPoint> = when (period) {
        0 -> listOf(
            SalesPoint("Lun", 2_340_000), SalesPoint("Mar", 1_980_000), SalesPoint("Mié", 2_710_000),
            SalesPoint("Jue", 2_150_000), SalesPoint("Vie", 3_480_000), SalesPoint("Sáb", 4_120_000), SalesPoint("Dom", 2_890_000)
        )
        2 -> listOf(
            SalesPoint("2021", 98_000_000), SalesPoint("2022", 164_000_000), SalesPoint("2023", 231_000_000),
            SalesPoint("2024", 305_000_000), SalesPoint("2025", 398_000_000), SalesPoint("2026", 452_000_000)
        )
        else -> listOf(
            SalesPoint("Jun", 28_400_000), SalesPoint("Jul", 31_900_000), SalesPoint("Ago", 30_200_000),
            SalesPoint("Sep", 36_700_000), SalesPoint("Oct", 41_300_000), SalesPoint("Nov", 19_670_000)
        )
    }

    fun ordersByPeriod(period: Int) = when (period) {
        0 -> 182
        2 -> 31_420
        else -> 1_284
    }

    fun salesByCategory(): List<SalesPoint> = listOf(
        SalesPoint("Alimento", 18_900_000),
        SalesPoint("Snacks", 6_200_000),
        SalesPoint("Juguetes", 5_100_000),
        SalesPoint("Ropa", 4_700_000),
        SalesPoint("Rascadores", 3_900_000),
        SalesPoint("Camas", 2_500_000)
    )

    fun topSellers(): List<SalesPoint> = listOf(
        SalesPoint("Mundo Animal PetShop", 14_800_000),
        SalesPoint("Michis & Co.", 9_300_000),
        SalesPoint("Juguetes Guau", 6_100_000),
        SalesPoint("Ropa Peluda", 4_200_000)
    )
}
