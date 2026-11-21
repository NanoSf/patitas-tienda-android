package co.patitas.tienda.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import kotlinx.coroutines.tasks.await

/**
 * Autenticación con Firebase Authentication (correo y contraseña) y lectura del perfil
 * con el rol desde la colección "users" de Firestore.
 *
 * Authentication solo sabe quién es el usuario; el rol (Comprador, Vendedor o Administrador)
 * y el estado de la cuenta viven en Firestore, y las reglas de seguridad los consultan.
 */
object AuthRepository {

    /** Error de negocio con un mensaje listo para mostrar en pantalla. */
    class AuthError(message: String) : Exception(message)

    /**
     * Inicia sesión y devuelve el perfil. Si la cuenta es una de las de demostración y todavía
     * no existe en este proyecto de Firebase, la crea en el primer ingreso (ver DemoSeeder).
     */
    suspend fun login(email: String, password: String): User {
        val normalized = email.trim().lowercase()
        try {
            Fb.auth.signInWithEmailAndPassword(normalized, password).await()
        } catch (e: Exception) {
            val notFound = e is FirebaseAuthInvalidCredentialsException || e is FirebaseAuthInvalidUserException
            if (notFound && DemoSeeder.isDemo(normalized) && password == DemoSeeder.DEMO_PASSWORD) {
                if (!DemoSeeder.createAccount(normalized)) throw e
            } else {
                throw e
            }
        }
        return loadProfile()
    }

    /**
     * Lee el perfil del usuario autenticado. Si fue eliminado o suspendido por un administrador
     * se cierra la sesión: la cuenta de Authentication existe, pero ya no puede usar la app.
     */
    suspend fun loadProfile(): User {
        val uid = Fb.uid ?: throw AuthError("No hay una sesión activa.")
        val doc = Fb.db.collection(Fb.USERS).document(uid).get().await()
        if (!doc.exists()) {
            Fb.auth.signOut()
            throw AuthError("Esta cuenta fue eliminada. Comunícate con soporte si crees que es un error.")
        }
        val user = User.from(doc)
        if (user.status == UserStatus.SUSPENDED) {
            Fb.auth.signOut()
            throw AuthError("Tu cuenta está suspendida. Escríbenos a soporte@patitas.co.")
        }
        Session.start(user)
        return user
    }

    /** Recupera la sesión guardada por Firebase al abrir la app (usado por el Splash). */
    suspend fun restore(): User? {
        if (Fb.auth.currentUser == null) return null
        return try {
            loadProfile()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Registro de Comprador o Vendedor. Crea la cuenta en Authentication y luego el perfil en
     * Firestore; si el perfil no se puede guardar, se borra la cuenta para no dejarla a medias.
     * Los vendedores quedan "Por verificar" hasta que un administrador los apruebe.
     */
    suspend fun register(
        name: String, email: String, phone: String, role: Role,
        password: String, storeName: String, document: String
    ): User {
        val result = Fb.auth.createUserWithEmailAndPassword(email.trim().lowercase(), password).await()
        val firebaseUser = result.user ?: throw AuthError("No se pudo crear la cuenta.")
        val user = User(
            id = firebaseUser.uid,
            name = name.trim(),
            email = firebaseUser.email.orEmpty(),
            phone = phone.trim(),
            role = role,
            status = if (role == Role.SELLER) UserStatus.PENDING else UserStatus.ACTIVE,
            document = document.trim(),
            storeName = storeName.trim()
        )
        try {
            Fb.db.collection(Fb.USERS).document(user.id).set(user.toMap()).await()
        } catch (e: Exception) {
            firebaseUser.delete()
            throw e
        }
        Session.start(user)
        return user
    }

    /**
     * Envía el correo de recuperación de contraseña de Firebase (en español).
     * Firebase responde igual exista o no el correo, para no revelar qué cuentas existen.
     */
    suspend fun sendPasswordReset(email: String) {
        Fb.auth.setLanguageCode("es")
        Fb.auth.sendPasswordResetEmail(email.trim().lowercase()).await()
    }

    /** Confirma la contraseña del usuario actual (se pide antes de activar la huella). */
    suspend fun verifyPassword(password: String) {
        val user = Fb.auth.currentUser ?: throw AuthError("No hay una sesión activa.")
        val credential = EmailAuthProvider.getCredential(user.email.orEmpty(), password)
        user.reauthenticate(credential).await()
    }

    /** Cierre de sesión: Firebase olvida el token y se limpia el estado en memoria. */
    fun logout() {
        Fb.auth.signOut()
        Session.clear()
    }

    /** Actualiza nombre y teléfono del propio perfil (las reglas no permiten cambiar el rol). */
    suspend fun updateOwnProfile(name: String, phone: String): User {
        val current = Session.user ?: throw AuthError("No hay una sesión activa.")
        Fb.db.collection(Fb.USERS).document(current.id)
            .update(mapOf("name" to name, "phone" to phone, "updatedAt" to System.currentTimeMillis()))
            .await()
        val updated = current.copy(name = name, phone = phone)
        Session.update(updated)
        return updated
    }

    /**
     * Elimina la propia cuenta: primero el perfil y el carrito en Firestore y luego la cuenta
     * de Authentication. Si Firebase exige un inicio de sesión reciente, basta con cerrar sesión:
     * sin perfil, la cuenta ya no puede ingresar.
     */
    suspend fun deleteOwnAccount() {
        val firebaseUser = Fb.auth.currentUser ?: return
        CartRepository.clear()
        Fb.db.collection(Fb.USERS).document(firebaseUser.uid).delete().await()
        try {
            firebaseUser.delete().await()
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            // El perfil ya no existe; el inicio de sesión lo rechazará.
        }
        logout()
    }

    /**
     * Creación de usuarios por el Administrador.
     *
     * createUserWithEmailAndPassword inicia sesión con la cuenta nueva, lo que cerraría la
     * sesión del administrador. Para evitarlo se usa una segunda instancia de FirebaseApp
     * ("patitas-admin") solo para crear la cuenta; el perfil se guarda con la sesión del
     * administrador, que es quien tiene permiso para asignar cualquier rol.
     */
    suspend fun createUserAsAdmin(context: Context, user: User, password: String): User {
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == HELPER_APP }
            ?: FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, HELPER_APP)
        val helperAuth = FirebaseAuth.getInstance(app)
        val result = helperAuth.createUserWithEmailAndPassword(user.email.trim().lowercase(), password).await()
        val uid = result.user?.uid ?: throw AuthError("No se pudo crear la cuenta.")
        helperAuth.signOut()
        val created = user.copy(id = uid, email = user.email.trim().lowercase(), createdAt = System.currentTimeMillis())
        Fb.db.collection(Fb.USERS).document(uid).set(created.toMap()).await()
        return created
    }

    private const val HELPER_APP = "patitas-admin"
}
