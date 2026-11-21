package co.patitas.tienda.data

/**
 * Sesión en memoria: el perfil (documento de "users") del usuario autenticado.
 * Firebase Authentication conserva la sesión entre aperturas de la app; este objeto guarda
 * el perfil ya leído de Firestore para no consultarlo en cada pantalla.
 */
object Session {

    /** Usuario autenticado o null si navega como invitado. */
    var user: User? = null
        private set

    val isGuest: Boolean get() = user == null

    /** Guarda el perfil del usuario que acaba de iniciar sesión. */
    fun start(user: User) {
        this.user = user
    }

    /** Actualiza el perfil en memoria cuando cambia en Firestore (por ejemplo, al editar el perfil). */
    fun update(user: User) {
        if (user.id == this.user?.id) this.user = user
    }

    /** Cierra la sesión en memoria y limpia el carrito y la compra en curso. */
    fun clear() {
        user = null
        CartRepository.reset()
        CheckoutState.reset()
    }
}
