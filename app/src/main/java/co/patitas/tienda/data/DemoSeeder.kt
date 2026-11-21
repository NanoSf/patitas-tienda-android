package co.patitas.tienda.data

import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.tasks.await

/**
 * Cuentas de demostración (una por rol) para que el docente pueda probar la app.
 *
 * No hay un servidor que las cree, así que la primera vez que alguien ingresa con una de ellas
 * y la contraseña de demostración, la app crea la cuenta en Firebase Authentication y su
 * perfil en Firestore. A la tienda de demostración se le cargan además productos de ejemplo.
 * Las reglas de seguridad solo permiten el rol Administrador para admin@patitas.co.
 */
object DemoSeeder {

    const val DEMO_PASSWORD = "Patitas2026*"

    private data class Account(
        val name: String, val phone: String, val role: Role, val document: String, val storeName: String = ""
    )

    private val accounts = mapOf(
        "comprador@patitas.co" to Account("Laura Gómez", "3005550101", Role.BUYER, "CC 52.114.908"),
        "vendedor@patitas.co" to Account("Carlos Ruiz", "3105550202", Role.SELLER, "NIT 901.245.778-1", "Mundo Animal PetShop"),
        "admin@patitas.co" to Account("Einer Aponte", "3205550303", Role.ADMIN, "CC 1.048.851.401")
    )

    /** Indica si el correo es de una cuenta de demostración. */
    fun isDemo(email: String) = email.trim().lowercase() in accounts

    /**
     * Crea la cuenta de demostración e inicia sesión con ella.
     * Devuelve false si la cuenta ya existía (entonces la contraseña escrita era incorrecta).
     */
    suspend fun createAccount(email: String): Boolean {
        if (email !in accounts) return false
        val result = try {
            Fb.auth.createUserWithEmailAndPassword(email, DEMO_PASSWORD).await()
        } catch (e: FirebaseAuthUserCollisionException) {
            return false
        }
        val uid = result.user?.uid ?: return false
        createProfile(uid, email)
        return true
    }

    /**
     * Crea el perfil de una cuenta de demostración (y los productos de la tienda demo).
     * También se usa si la cuenta existe en Authentication pero su perfil no se alcanzó a
     * guardar (por ejemplo, porque las reglas de Firestore aún no estaban publicadas).
     */
    suspend fun createProfile(uid: String, email: String): User? {
        val account = accounts[email] ?: return null
        val user = User(
            id = uid, name = account.name, email = email, phone = account.phone,
            role = account.role, status = UserStatus.ACTIVE,
            document = account.document, storeName = account.storeName
        )
        Fb.db.collection(Fb.USERS).document(uid).set(user.toMap()).await()
        if (account.role == Role.SELLER) seedProducts(user)
        return user
    }

    /** Productos de ejemplo de la tienda de demostración (sin foto: usan el ícono de su categoría). */
    private suspend fun seedProducts(seller: User) {
        val batch = Fb.db.batch()
        sampleProducts(seller).forEach { product ->
            batch.set(Fb.db.collection(Fb.PRODUCTS).document(), product.toMap())
        }
        batch.commit().await()
    }

    /** Lista de productos de ejemplo para la tienda de demostración. */
    private fun sampleProducts(seller: User): List<Product> {
        /** Atajo para construir un producto de ejemplo de la tienda demo. */
        fun p(
            name: String, category: Int, species: Species, brand: String, presentation: String,
            price: Long, stock: Int, description: String, status: ProductStatus = ProductStatus.PUBLISHED,
            rating: Double = 0.0, reviews: Int = 0, ica: String = ""
        ) = Product(
            name = name, categoryId = category, species = species, brand = brand, presentation = presentation,
            price = price, stock = stock, sellerId = seller.id, sellerName = seller.storeName,
            description = description, status = status, rating = rating, reviews = reviews, icaRegister = ica
        )
        return listOf(
            p("Concentrado NutriCan perro adulto raza mediana", 1, Species.DOG, "NutriCan", "8 kg", 142_900, 18,
                "Pollo y arroz con omega 3 y 6 para piel y pelaje brillante. Sin colorantes artificiales. Ideal para perros de 10 a 25 kg.",
                rating = 4.9, reviews = 214, ica = "ICA 10245-AL"),
            p("Rascador torre 3 niveles con cueva", 3, Species.CAT, "Michis & Co.", "1,20 m", 189_000, 6,
                "Torre de sisal natural con tres plataformas, cueva acolchada y pompón colgante. Base antivuelco.",
                rating = 4.8, reviews = 96),
            p("Buzo polar para perro", 5, Species.DOG, "Ropa Peluda", "Talla M", 45_000, 22,
                "Buzo en tela polar antipeeling con capota. Abertura para traílla y costuras reforzadas.",
                rating = 4.7, reviews = 58),
            p("Mordedor de caucho resistente", 6, Species.DOG, "Juguetes Guau", "Talla M · rojo", 24_900, 4,
                "Caucho natural no tóxico, apto para rellenar con premios. Para mordedores intensos.",
                rating = 5.0, reviews = 131),
            p("Arena aglomerante sin olor", 7, Species.CAT, "Michis & Co.", "10 kg", 58_500, 30,
                "Arena de bentonita de alta absorción. Forma terrones firmes y controla el olor por 15 días.",
                rating = 4.6, reviews = 77),
            p("Cama ortopédica lavable", 4, Species.DOG, "Mundo Animal", "Grande", 159_000, 9,
                "Espuma viscoelástica con funda removible y base antideslizante. Ideal para perros senior.",
                rating = 4.8, reviews = 44),
            p("Snacks dentales para perro", 2, Species.DOG, "DentiPet", "Bolsa 300 g", 21_900, 3,
                "Premios en forma de hueso que ayudan a reducir el sarro. Con menta y perejil.",
                rating = 4.5, reviews = 63, ica = "ICA 20871-AL"),
            p("Alimento húmedo para gato salmón", 1, Species.CAT, "FelinoVet", "Caja x 12", 64_800, 0,
                "Trozos de salmón en salsa, sin granos. Caja de 12 sobres de 85 g.",
                ProductStatus.OUT_OF_STOCK, 4.7, 38, "ICA 30112-AL"),
            p("Pelota interactiva con luz", 6, Species.CAT, "Juguetes Guau", "Unidad", 18_500, 15,
                "Pelota que se enciende y rueda sola al contacto. Recargable por USB.", ProductStatus.IN_REVIEW),
            p("Collar ajustable reflectivo", 5, Species.DOG, "Mundo Animal", "Talla S-M", 29_900, 12,
                "Collar en nailon con banda reflectiva y hebilla de liberación rápida.", ProductStatus.PAUSED, 4.4, 21)
        )
    }
}
