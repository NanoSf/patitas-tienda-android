package co.patitas.tienda.data

import kotlinx.coroutines.tasks.await

/**
 * CRUD de usuarios del rol Administrador sobre la colección "users".
 * Crear está en AuthRepository.createUserAsAdmin porque también crea la cuenta de Authentication.
 */
object UserRepository {

    private val collection get() = Fb.db.collection(Fb.USERS)

    /** Leer: todos los usuarios en tiempo real, del más reciente al más antiguo. */
    fun all() = QueryLiveData(collection, User::from) { list -> list.sortedByDescending { it.createdAt } }

    /** Perfil de un usuario en tiempo real (la app lo usa para detectar suspensiones). */
    fun watch(uid: String) = DocumentLiveData(collection.document(uid), User::from)

    /** Leer un usuario por su UID (null si no existe). */
    suspend fun get(uid: String): User? =
        collection.document(uid).get().await().takeIf { it.exists() }?.let(User::from)

    /** Actualizar: datos, rol y estado. El correo no se cambia porque es la credencial de acceso. */
    suspend fun update(user: User) {
        collection.document(user.id).update(
            mapOf(
                "name" to user.name,
                "phone" to user.phone,
                "document" to user.document,
                "storeName" to user.storeName,
                "role" to user.role.name,
                "status" to user.status.name,
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    /** Suspender, reactivar o verificar. El motivo queda guardado en el perfil. */
    suspend fun setStatus(user: User, status: UserStatus, reason: String = "") {
        collection.document(user.id).update(
            mapOf("status" to status.name, "statusReason" to reason, "updatedAt" to System.currentTimeMillis())
        ).await()
    }

    /**
     * Eliminar: borra el perfil de Firestore. La cuenta de Authentication solo se puede borrar
     * con el Admin SDK (servidor); sin perfil, el inicio de sesión la rechaza.
     */
    suspend fun delete(user: User) {
        collection.document(user.id).delete().await()
    }
}
