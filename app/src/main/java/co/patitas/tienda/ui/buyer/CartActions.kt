package co.patitas.tienda.ui.buyer

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import co.patitas.tienda.R
import co.patitas.tienda.data.CartRepository
import co.patitas.tienda.data.Product
import co.patitas.tienda.data.Session
import co.patitas.tienda.ui.auth.LoginActivity
import co.patitas.tienda.util.errorMessage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Acción común "Agregar al carrito" (RF-CAR-01): guarda el ítem en Firestore.
 * Si el usuario es invitado se le pide iniciar sesión, tal como define la lógica de navegación
 * del documento técnico.
 */
object CartActions {

    /** Agrega el producto al carrito en Firestore y ofrece ir al carrito. */
    fun add(activity: Activity, anchor: View, product: Product, quantity: Int = 1, variant: String = product.presentation) {
        if (Session.isGuest) {
            askLogin(activity)
            return
        }
        if (product.stock <= 0) {
            Snackbar.make(anchor, R.string.product_out_of_stock, Snackbar.LENGTH_SHORT).show()
            return
        }
        val scope = (activity as AppCompatActivity).lifecycleScope
        scope.launch {
            try {
                CartRepository.add(product, quantity, variant)
                Snackbar.make(anchor, activity.getString(R.string.cart_added, product.name), Snackbar.LENGTH_SHORT)
                    .setAction(R.string.action_view_cart) {
                        if (activity is BuyerHomeActivity) {
                            activity.selectTab(R.id.tab_cart)
                        } else {
                            activity.startActivity(BuyerHomeActivity.intent(activity, R.id.tab_cart))
                        }
                    }
                    .show()
            } catch (e: Exception) {
                Snackbar.make(anchor, activity.errorMessage(e), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    /** Pide al invitado iniciar sesión para continuar. */
    fun askLogin(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setIcon(R.drawable.ic_lock)
            .setTitle(R.string.guest_login_title)
            .setMessage(R.string.guest_login_message)
            .setPositiveButton(R.string.action_login) { _, _ ->
                activity.startActivity(Intent(activity, LoginActivity::class.java))
            }
            .setNegativeButton(R.string.action_not_now, null)
            .show()
    }
}
