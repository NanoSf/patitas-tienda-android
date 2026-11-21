package co.patitas.tienda.data

import android.util.Log
import androidx.lifecycle.LiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/**
 * Puntos de acceso a Firebase y nombres de las colecciones de Cloud Firestore.
 *
 * Estructura de la base de datos:
 *  users/{uid}                 Perfil y rol de cada cuenta (el id es el UID de Authentication)
 *  products/{id}               Productos con su miniatura en Base64
 *  productImages/{id}          Foto completa del producto (un documento por producto)
 *  carts/{uid}/items/{itemId}  Carrito de cada comprador
 *  orders/{id}                 Pedidos con su historial de estados y la referencia de ePayco
 */
object Fb {
    const val USERS = "users"
    const val PRODUCTS = "products"
    const val PRODUCT_IMAGES = "productImages"
    const val CARTS = "carts"
    const val CART_ITEMS = "items"
    const val ORDERS = "orders"

    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /** UID del usuario autenticado o null si es invitado. */
    val uid: String? get() = auth.currentUser?.uid
}

/**
 * LiveData que escucha una consulta de Firestore en tiempo real.
 *
 * El listener (addSnapshotListener) se registra solo mientras haya una pantalla visible
 * observando (onActive) y se elimina cuando ya no la hay (onInactive). Así cada cambio en la
 * base de datos (otro usuario crea un producto, el vendedor cambia el estado de un pedido)
 * se refleja de inmediato en la interfaz sin recargar, y no se consumen lecturas en segundo plano.
 *
 * @param mapper convierte cada documento en un objeto del modelo.
 * @param sorter orden local de la lista (evita crear índices compuestos en Firestore).
 */
class QueryLiveData<T>(
    private val query: Query,
    private val mapper: (DocumentSnapshot) -> T,
    private val sorter: (List<T>) -> List<T> = { it }
) : LiveData<List<T>>() {

    private var registration: ListenerRegistration? = null

    override fun onActive() {
        registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Normalmente PERMISSION_DENIED: las reglas de seguridad no permiten la lectura.
                Log.w(TAG, "Error escuchando la consulta", error)
                if (value == null) value = emptyList()
                return@addSnapshotListener
            }
            value = sorter(snapshot?.documents.orEmpty().map(mapper))
        }
    }

    override fun onInactive() {
        registration?.remove()
        registration = null
    }

    /** Valor actual o lista vacía si todavía no llegan datos. */
    val current: List<T> get() = value.orEmpty()

    private companion object {
        const val TAG = "QueryLiveData"
    }
}

/** LiveData que escucha un único documento (por ejemplo, el perfil del usuario actual). */
class DocumentLiveData<T>(
    private val reference: DocumentReference,
    private val mapper: (DocumentSnapshot) -> T?
) : LiveData<T?>() {

    private var registration: ListenerRegistration? = null

    override fun onActive() {
        registration = reference.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("DocumentLiveData", "Error escuchando el documento", error)
                return@addSnapshotListener
            }
            value = snapshot?.takeIf { it.exists() }?.let(mapper)
        }
    }

    override fun onInactive() {
        registration?.remove()
        registration = null
    }
}
